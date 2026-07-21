package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WDropdown;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Peripheral node: when Tick input &gt; 0.5, emits weak redstone toward the chosen face of the computer block.
 * The face is block-relative (Front/Back/Left/Right/Top/Bottom) and resolved against block facing at emit time.
 */
public final class RedstonePortNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "redstone_emitter");

    private RelativeFace emitFace = RelativeFace.FRONT;
    private final WDropdown<RelativeFace> faceDropdown;

    public RedstonePortNode(int x, int y) {
        super(TYPE_ID, "Redstone Output", x, y);
        addInput("Tick", 0xFF00FF88);
        addInput("Level", 0xFFFF6655);
        faceDropdown = new WDropdown<>(
                88,
                List.of(RelativeFace.values()),
                f -> "Face: " + f.displayName(),
                emitFace,
                f -> emitFace = f);
        addElement(faceDropdown);
        addElement(new WLabel("Weak power 0-15 to neighbor", 0xFF888888));
        setEvaluator(n -> {});
    }

    public RelativeFace getEmitFace() {
        return emitFace;
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("computedEmitFace", emitFace.name());
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("computedEmitFace")) {
            String raw = tag.getString("computedEmitFace");
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
                emitFace = parsed;
                faceDropdown.setSelected(parsed);
            }
        }
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.VANILLA;
    public static final Component LABEL = Component.literal("Redstone Output");

    public static void register() {
        NodeRegistry.register(TYPE_ID, RedstonePortNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
