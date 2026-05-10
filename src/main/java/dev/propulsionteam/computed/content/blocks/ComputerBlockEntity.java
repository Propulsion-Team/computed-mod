package dev.propulsionteam.computed.content.blocks;

import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.FunctionDefinitionStore;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.nodes.RedstonePortNode;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ComputerBlockEntity extends BaseContainerBlockEntity {
    public static final int CONTAINER_SIZE = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final WGraph graph = new WGraph();
    /** Saved function bodies keyed by id (parallel to graph function cards). */
    private final FunctionDefinitionStore functionDefinitions = new FunctionDefinitionStore();
    /** Weak redstone emitted toward each {@link Direction} (neighbor on that side sees this level). */
    private final int[] redstoneEmitted = new int[6];

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.COMPUTER_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Runs the node graph on the server world thread at 20 TPS. Evaluators are not safe for arbitrary
     * background threads without a numeric snapshot pipeline, so stepping stays synchronous here.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.graph.advanceSimulationInWorld(1.0 / WGraph.MAX_TICK_RATE);
        be.refreshRedstoneFromGraph();
    }

    /** Redstone wire / blocks query this from the neighbor toward the computer. */
    public int getEmittedRedstone(Direction towardNeighbor) {
        return redstoneEmitted[towardNeighbor.ordinal()];
    }

    private void refreshRedstoneFromGraph() {
        Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        int[] next = new int[6];
        List<RedstonePortNode> ports = new ArrayList<>();
        collectRedstonePorts(graph, ports);
        for (RedstonePortNode rp : ports) {
            WNode n = rp;
            if (n.getInputs().size() < 2) {
                continue;
            }
            double tick = n.getInputs().get(0).getValue();
            double lv = n.getInputs().get(1).getValue();
            if (tick > 0.5) {
                int p = net.minecraft.util.Mth.clamp((int) Math.round(lv), 0, 15);
                int o = rp.getEmitDirection().ordinal();
                next[o] = Math.max(next[o], p);
            }
        }
        if (!Arrays.equals(next, redstoneEmitted)) {
            System.arraycopy(next, 0, redstoneEmitted, 0, 6);
            setChanged();
            BlockState st = getBlockState();
            lvl.updateNeighborsAt(worldPosition, st.getBlock());
            for (Direction d : Direction.values()) {
                lvl.neighborChanged(worldPosition.relative(d), st.getBlock(), worldPosition);
            }
        }
    }

    private static void collectRedstonePorts(WGraph g, List<RedstonePortNode> out) {
        for (WNode n : g.getNodes()) {
            if (n instanceof RedstonePortNode rp) {
                out.add(rp);
            } else if (n instanceof FunctionCardNode fc) {
                collectRedstonePorts(fc.getInnerGraph(), out);
            }
        }
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.computed.computer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items.clear();
        for (int i = 0; i < Math.min(newItems.size(), items.size()); i++) {
            items.set(i, newItems.get(i));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ComputerPeripheralMenu(ComputedRegistries.COMPUTER_PERIPHERAL_MENU.get(), containerId, playerInventory, this);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        syncPeripheralNodesWithInventory();
    }

    public WGraph getGraph() {
        return graph;
    }

    public CompoundTag getGraphData() {
        CompoundTag bundle = new CompoundTag();
        bundle.put("ComputerGraph", graph.save());
        bundle.put("ComputerFunctions", functionDefinitions.saveList());
        return bundle;
    }

    public void applyGraphFromNetwork(CompoundTag tag) {
        if (tag.contains("ComputerGraph", Tag.TAG_COMPOUND)) {
            graph.load(tag.getCompound("ComputerGraph"));
            functionDefinitions.clear();
            if (tag.contains("ComputerFunctions")) {
                functionDefinitions.load(tag.getList("ComputerFunctions", Tag.TAG_COMPOUND));
            }
        } else {
            graph.load(tag);
            functionDefinitions.clear();
        }
        FunctionCardNode.applyLibraryToInnerGraphs(graph, functionDefinitions);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void hydrateFunctionCardsFromLibrary() {
        FunctionCardNode.applyLibraryToInnerGraphs(graph, functionDefinitions);
    }

    /**
     * Shift-use: place one peripheral into the first valid empty slot (unique types only).
     */
    public boolean tryInsertPeripheralFromHand(ItemStack stack) {
        if (!Peripherals.isPeripheral(stack)) {
            return false;
        }
        ItemStack one = stack.split(1);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (getItem(i).isEmpty() && Peripherals.mayPlaceInComputer(this, i, one)) {
                setItem(i, one);
                return true;
            }
        }
        stack.grow(1);
        return false;
    }

    private void syncPeripheralNodesWithInventory() {
        Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        Map<ResourceLocation, CompoundTag> savedPeripheralState = new HashMap<>();
        List<WNode> toRemove = new ArrayList<>();
        for (WNode n : graph.getNodes()) {
            if (Peripherals.isPeripheralNodeType(n.getTypeId())) {
                savedPeripheralState.putIfAbsent(n.getTypeId(), n.save().copy());
                toRemove.add(n);
            }
        }
        for (WNode n : toRemove) {
            graph.removeNode(n);
        }
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            ItemStack st = getItem(i);
            if (st.isEmpty()) {
                continue;
            }
            ResourceLocation nodeId = Peripherals.nodeTypeFor(st);
            WNode node = NodeRegistry.createNode(nodeId, 40 + (i % 3) * 88, 48 + (i / 3) * 72);
            if (node != null) {
                CompoundTag prev = savedPeripheralState.get(nodeId);
                if (prev != null) {
                    node.load(prev.copy());
                }
                graph.addNode(node);
            }
        }
        setChanged();
        lvl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.contains("ComputerGraph")) {
            graph.load(tag.getCompound("ComputerGraph"));
        }
        if (tag.contains("ComputerFunctions")) {
            functionDefinitions.load(tag.getList("ComputerFunctions", Tag.TAG_COMPOUND));
        } else {
            functionDefinitions.clear();
        }
        hydrateFunctionCardsFromLibrary();
        if (level != null && !level.isClientSide) {
            syncPeripheralNodesWithInventory();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        tag.put("ComputerGraph", graph.save());
        tag.put("ComputerFunctions", functionDefinitions.saveList());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("ComputerGraph", graph.save());
        tag.put("ComputerFunctions", functionDefinitions.saveList());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.contains("ComputerGraph")) {
            graph.load(tag.getCompound("ComputerGraph"));
        }
        if (tag.contains("ComputerFunctions")) {
            functionDefinitions.load(tag.getList("ComputerFunctions", Tag.TAG_COMPOUND));
        } else {
            functionDefinitions.clear();
        }
        hydrateFunctionCardsFromLibrary();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
