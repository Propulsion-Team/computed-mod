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
 * Reads the weak redstone signal arriving at the chosen block-relative face of the computer
 * and exposes it as a 0-15 Level output.
 */
public final class RedstoneInputNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "redstone_input");

    private RelativeFace readFace = RelativeFace.FRONT;
    private final WDropdown<RelativeFace> faceDropdown;

    public RedstoneInputNode(int x, int y) {
        super(TYPE_ID, "Redstone Input", x, y);
        addOutput("Level", 0xFFFF6655);
        faceDropdown = new WDropdown<>(
                88,
                List.of(RelativeFace.values()),
                f -> "Face: " + f.displayName(),
                readFace,
                f -> readFace = f);
        addElement(faceDropdown);
        addElement(new WLabel("Reads 0-15 from neighbor", 0xFF888888));
        setEvaluator(n -> {
            int level = readNeighborSignal();
            if (!n.getOutputs().isEmpty()) {
                n.getOutputs().get(0).setValue(level);
            }
        });
    }

    public RelativeFace getReadFace() {
        return readFace;
    }

    private int readNeighborSignal() {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) {
            return 0;
        }
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) {
            return 0;
        }
        BlockState st = host.getBlockState();
        if (!st.hasProperty(ComputerBlock.FACING)) {
            return 0;
        }
        Direction facing = st.getValue(ComputerBlock.FACING);
        Direction worldFace = readFace.toWorld(facing);
        BlockPos neighbor = host.getBlockPos().relative(worldFace);
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
    public static final Component LABEL = Component.literal("Redstone Input");

    public static void register() {
        NodeRegistry.register(TYPE_ID, RedstoneInputNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
