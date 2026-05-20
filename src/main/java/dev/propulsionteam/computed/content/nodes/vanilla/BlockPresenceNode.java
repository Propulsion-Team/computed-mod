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
 * Emits 1.0 when the block at the chosen relative face is non-air, else 0.0.
 */
public final class BlockPresenceNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "block_presence");

    private RelativeFace readFace = RelativeFace.FRONT;
    private final WDropdown<RelativeFace> faceDropdown;

    public BlockPresenceNode(int x, int y) {
        super(TYPE_ID, "Block Presence", x, y);
        addOutput("Present", 0xFFFF5555);
        faceDropdown = new WDropdown<>(
                88,
                List.of(RelativeFace.values()),
                f -> "Face: " + f.displayName(),
                readFace,
                f -> readFace = f);
        addElement(faceDropdown);
        addElement(new WLabel("1.0 if non-air at face"));
        setEvaluator(n -> n.getOutputs().get(0).setValue(present() ? 1.0 : 0.0));
    }

    private boolean present() {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return false;
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) return false;
        BlockState selfState = host.getBlockState();
        if (!selfState.hasProperty(ComputerBlock.FACING)) return false;
        Direction worldFace = readFace.toWorld(selfState.getValue(ComputerBlock.FACING));
        BlockPos neighbor = host.getBlockPos().relative(worldFace);
        return !lvl.getBlockState(neighbor).isAir();
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
    public static final Component LABEL = Component.literal("Block Presence");

    public static void register() {
        NodeRegistry.register(TYPE_ID, BlockPresenceNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
