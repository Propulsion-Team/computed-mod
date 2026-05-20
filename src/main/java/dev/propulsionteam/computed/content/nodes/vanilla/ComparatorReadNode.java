package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WDropdown;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the analog comparator output (0-15) of the block at a chosen relative face. Falls back to the weak
 * redstone signal if the target block does not provide a comparator-style analog output.
 */
public final class ComparatorReadNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "comparator_read");

    private RelativeFace readFace = RelativeFace.FRONT;
    private final WDropdown<RelativeFace> faceDropdown;

    public ComparatorReadNode(int x, int y) {
        super(TYPE_ID, "Comparator Read", x, y);
        addOutput("Signal", 0xFFFFBB00);
        faceDropdown = new WDropdown<>(
                88,
                List.of(RelativeFace.values()),
                f -> "Face: " + f.displayName(),
                readFace,
                f -> readFace = f);
        addElement(faceDropdown);
        addElement(new WLabel("Analog 0-15 (comparator)"));
        setEvaluator(n -> n.getOutputs().get(0).setValue(readAnalog()));
    }

    private int readAnalog() {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return 0;
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) return 0;
        BlockState selfState = host.getBlockState();
        if (!selfState.hasProperty(ComputerBlock.FACING)) return 0;
        Direction worldFace = readFace.toWorld(selfState.getValue(ComputerBlock.FACING));
        BlockPos neighbor = host.getBlockPos().relative(worldFace);
        BlockState target = lvl.getBlockState(neighbor);
        if (target.hasAnalogOutputSignal()) {
            return target.getAnalogOutputSignal(lvl, neighbor);
        }
        return lvl.getSignal(neighbor, worldFace);
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("computedReadFace", readFace.name());
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("computedReadFace")) {
            String raw = tag.getString("computedReadFace");
            RelativeFace parsed = null;
            try {
                parsed = RelativeFace.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                Direction legacy = Direction.byName(raw);
                if (legacy != null) {
                    parsed = RelativeFace.fromLegacyDirection(legacy);
                }
            }
            if (parsed != null) {
                readFace = parsed;
                faceDropdown.setSelected(parsed);
            }
        }
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.VANILLA;
    public static final Component LABEL = Component.literal("Comparator Read");

    public static void register() {
        NodeRegistry.register(TYPE_ID, ComparatorReadNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
