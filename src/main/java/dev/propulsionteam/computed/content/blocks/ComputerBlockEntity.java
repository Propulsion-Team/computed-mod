package dev.propulsionteam.computed.content.blocks;

import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.FunctionDefinitionStore;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.ComputedTags;
import dev.propulsionteam.computed.content.PlacedPeripheralLink;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.nodes.vanilla.RedstonePortNode;
import dev.propulsionteam.computed.integration.CreateRedstoneLinkBridge;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

public class ComputerBlockEntity extends BaseContainerBlockEntity {
    public static final int CONTAINER_SIZE = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final WGraph graph = new WGraph();
    /** Saved function bodies keyed by id (parallel to graph function cards). */
    private final FunctionDefinitionStore functionDefinitions = new FunctionDefinitionStore();
    /** Weak redstone emitted toward each {@link Direction} (neighbor on that side sees this level). */
    private final int[] redstoneEmitted = new int[6];
    private final CreateRedstoneLinkBridge createRedstoneLinks = new CreateRedstoneLinkBridge();
    /** In-world blocks linked to this computer (thrusters, etc.); hardware id is the block registry id. */
    private final List<PlacedPeripheralLink> placedPeripheralLinks = new ArrayList<>();
    private int nextPlacedPeripheralInstanceId = 1;

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
        Level lvl = be.getLevel();
        if (CreateRedstoneLinkBridge.isCreateLoaded() && lvl != null && !lvl.isClientSide) {
            be.createRedstoneLinks.clear(lvl);
            be.createRedstoneLinks.syncFromGraph(lvl, be, be.graph);
        }
        be.prunePlacedPeripherals();
        ComputedGraphExecution.withHost(be, () -> be.graph.advanceSimulationInWorld(1.0 / WGraph.MAX_TICK_RATE));
        if (CreateRedstoneLinkBridge.isCreateLoaded() && lvl != null && !lvl.isClientSide) {
            be.createRedstoneLinks.pushTransmitters(lvl);
        }
        be.mutePeripheralsWithoutHardware(be.graph);
        be.refreshRedstoneFromGraph();
    }

    /** Redstone wire / blocks query this from the neighbor toward the computer. */
    public int getEmittedRedstone(Direction towardNeighbor) {
        return redstoneEmitted[towardNeighbor.ordinal()];
    }

    /** Zeros outputs for peripheral nodes with no matching item in this computer (including nested function graphs). */
    private void mutePeripheralsWithoutHardware(WGraph g) {
        for (WNode n : g.getNodes()) {
            if (n instanceof FunctionCardNode fc) {
                mutePeripheralsWithoutHardware(fc.getInnerGraph());
            }
            if (Peripherals.isPeripheralNodeType(n.getTypeId()) && !hasPeripheralEquipped(n.getTypeId())) {
                for (var out : n.getOutputs()) {
                    out.setValue(0.0);
                }
            }
        }
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
            if (!hasPeripheralEquipped(RedstonePortNode.TYPE_ID)) {
                continue;
            }
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
        CompoundTag copy = tag.copy();
        Peripherals.stripEditorOnlyTags(copy);
        if (copy.contains("ComputerGraph", Tag.TAG_COMPOUND)) {
            graph.load(copy.getCompound("ComputerGraph"));
            functionDefinitions.clear();
            if (copy.contains("ComputerFunctions")) {
                functionDefinitions.load(copy.getList("ComputerFunctions", Tag.TAG_COMPOUND));
            }
        } else {
            graph.load(copy);
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

    /**
     * True if the hardware token required for {@code nodeTypeId} is satisfied by an inventory peripheral item or an
     * in-world linked block (see {@link #addPlacedPeripheralFromWorld}).
     */
    public boolean hasPeripheralEquipped(ResourceLocation nodeTypeId) {
        ResourceLocation requiredItem = Peripherals.peripheralItemRequiredForNodeType(nodeTypeId);
        if (requiredItem == null) {
            return true;
        }
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            ItemStack st = getItem(i);
            if (!st.isEmpty()
                    && Peripherals.isPeripheral(st)
                    && BuiltInRegistries.ITEM.getKey(st.getItem()).equals(requiredItem)) {
                return true;
            }
        }
        Level lvl = this.level;
        return lvl != null && placedHardwareProvides(lvl, requiredItem);
    }

    private boolean placedHardwareProvides(Level level, ResourceLocation requiredToken) {
        for (PlacedPeripheralLink link : placedPeripheralLinks) {
            if (!link.kind().equals(requiredToken)) {
                continue;
            }
            if (isPlacedLinkStillValid(level, link)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlacedLinkStillValid(Level level, PlacedPeripheralLink link) {
        BlockState st = level.getBlockState(link.pos());
        if (st.isAir() || !Peripherals.isPlacedPeripheralLinkTargetState(st)) {
            return false;
        }
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).equals(link.kind());
    }

    private void prunePlacedPeripherals() {
        Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        boolean removed = placedPeripheralLinks.removeIf(link -> !isPlacedLinkStillValid(lvl, link));
        if (removed) {
            setChanged();
        }
    }

    /**
     * Registers an already-placed block as linked hardware. The block must be in {@link ComputedTags.Blocks#PLACED_PERIPHERAL_LINK_TARGETS}
     * and within range of this computer.
     */
    public boolean addPlacedPeripheralFromWorld(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !Peripherals.isPlacedPeripheralLinkTargetState(state)) {
            return false;
        }
        if (worldPosition.distSqr(pos) > 64 * 64) {
            return false;
        }
        ResourceLocation kind = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (int i = 0; i < placedPeripheralLinks.size(); i++) {
            PlacedPeripheralLink existing = placedPeripheralLinks.get(i);
            if (existing.pos().equals(pos)) {
                if (existing.kind().equals(kind)) {
                    return true;
                }
                placedPeripheralLinks.remove(i);
                break;
            }
        }
        int id = nextPlacedPeripheralInstanceId++;
        placedPeripheralLinks.add(new PlacedPeripheralLink(pos.immutable(), kind, id));
        setChanged();
        return true;
    }

    public List<PlacedPeripheralLink> placedPeripheralLinksView() {
        return List.copyOf(placedPeripheralLinks);
    }

    public boolean isPlacedPeripheralLinkActive(PlacedPeripheralLink link) {
        Level lvl = this.level;
        return lvl != null && isPlacedLinkStillValid(lvl, link);
    }

    @Nullable
    public BlockPos findActivePlacedHardware(ResourceLocation blockKind, int instanceId) {
        for (PlacedPeripheralLink link : placedPeripheralLinks) {
            if (link.kind().equals(blockKind)
                    && link.instanceId() == instanceId
                    && isPlacedPeripheralLinkActive(link)) {
                return link.pos();
            }
        }
        return null;
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
        placedPeripheralLinks.clear();
        nextPlacedPeripheralInstanceId = 1;
        if (tag.contains("PlacedPeripheralLinks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PlacedPeripheralLinks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (e.contains("Pos", Tag.TAG_LONG)
                        && e.contains("Kind", Tag.TAG_STRING)
                        && e.contains("Id", Tag.TAG_INT)) {
                    placedPeripheralLinks.add(
                            new PlacedPeripheralLink(
                                    BlockPos.of(e.getLong("Pos")),
                                    ResourceLocation.parse(e.getString("Kind")),
                                    e.getInt("Id")));
                }
            }
        }
        if (tag.contains("PlacedPeripheralNextId", Tag.TAG_INT)) {
            nextPlacedPeripheralInstanceId = Math.max(1, tag.getInt("PlacedPeripheralNextId"));
        }
        int maxSeen = placedPeripheralLinks.stream().mapToInt(PlacedPeripheralLink::instanceId).max().orElse(0);
        nextPlacedPeripheralInstanceId = Math.max(nextPlacedPeripheralInstanceId, maxSeen + 1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        tag.put("ComputerGraph", graph.save());
        tag.put("ComputerFunctions", functionDefinitions.saveList());
        ListTag list = new ListTag();
        for (PlacedPeripheralLink link : placedPeripheralLinks) {
            CompoundTag e = new CompoundTag();
            e.putLong("Pos", link.pos().asLong());
            e.putString("Kind", link.kind().toString());
            e.putInt("Id", link.instanceId());
            list.add(e);
        }
        tag.put("PlacedPeripheralLinks", list);
        tag.putInt("PlacedPeripheralNextId", nextPlacedPeripheralInstanceId);
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
