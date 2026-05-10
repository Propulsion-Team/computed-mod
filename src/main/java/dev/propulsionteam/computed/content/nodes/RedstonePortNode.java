package dev.propulsionteam.computed.content.nodes;

import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WButton;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Peripheral node: when Tick input &gt; 0.5, emits weak redstone toward the chosen face of the computer block.
 */
public final class RedstonePortNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "redstone_emitter");

    private Direction emitFace = Direction.NORTH;
    private final WLabel faceLabel;

    public RedstonePortNode(int x, int y) {
        super(TYPE_ID, "Redstone", x, y);
        addInput("Tick", 0xFF00FF88);
        addInput("Level", 0xFFFF6655);
        faceLabel = new WLabel("", 0xFFCCCCCC);
        addElement(faceLabel);
        addElement(
                new WButton("Cycle face", 88, () -> {
                    Direction[] v = Direction.values();
                    emitFace = v[(emitFace.ordinal() + 1) % v.length];
                    refreshFaceLabel();
                }));
        addElement(new WLabel("Weak power 0-15 to neighbor", 0xFF888888));
        setEvaluator(n -> {});
        refreshFaceLabel();
    }

    public Direction getEmitDirection() {
        return emitFace;
    }

    private void refreshFaceLabel() {
        faceLabel.setText("Face: " + emitFace.name().toLowerCase());
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("computedEmitFace", emitFace.getName());
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("computedEmitFace")) {
            Direction parsed = Direction.byName(tag.getString("computedEmitFace"));
            if (parsed != null) {
                emitFace = parsed;
            }
        }
        refreshFaceLabel();
    }
}
