package dev.propulsionteam.computed.internal.node;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Lossless editor placeholder used when the node implementation is supplied by a missing addon. */
public final class MissingNode extends WNode {
    public static final String MISSING_MARKER = "ComputedMissingType";
    private CompoundTag rawTag;

    private MissingNode(ResourceLocation originalType, int x, int y, CompoundTag rawTag) {
        super(originalType, "Missing: " + originalType, x, y);
        this.rawTag = rawTag.copy();
        addPins(rawTag.getList("inputs", Tag.TAG_COMPOUND), true);
        addPins(rawTag.getList("outputs", Tag.TAG_COMPOUND), false);
        addElement(new WLabel("Missing addon node", 0xFFFF5555));
        addElement(new WLabel(originalType.toString(), 0xFFFFAA88));
        setEvaluator(ignored -> resetOutputs());
    }

    public static MissingNode fromLegacyTag(ResourceLocation originalType, CompoundTag rawTag) {
        return new MissingNode(originalType, rawTag.getInt("x"), rawTag.getInt("y"), rawTag);
    }

    private void addPins(ListTag tags, boolean input) {
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag pinTag = tags.getCompound(index);
            WPin.DataType type = parseType(pinTag);
            String label = pinTag.contains("name", Tag.TAG_STRING)
                    ? pinTag.getString("name")
                    : (input ? "Input " : "Output ") + (index + 1);
            if (input) addInput(label, type, 0xFF888888);
            else addOutput(label, type, 0xFF888888);
        }
    }

    private static WPin.DataType parseType(CompoundTag tag) {
        if (tag.contains("dataType", Tag.TAG_STRING)) {
            try {
                return WPin.DataType.valueOf(tag.getString("dataType").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (tag.contains("s", Tag.TAG_STRING)) return WPin.DataType.STRING;
        if (tag.contains("value")) return WPin.DataType.NUMBER;
        return WPin.DataType.WIDGET;
    }

    private void resetOutputs() {
        for (WPin pin : getOutputs()) {
            switch (pin.getDataType()) {
                case NUMBER -> pin.setValue(0.0);
                case STRING -> pin.setStringValue("");
                case WIDGET -> pin.setWidgetValue(null);
            }
        }
    }

    @Override
    public boolean isMissingType() {
        return true;
    }

    @Override
    public CompoundTag save() {
        CompoundTag result = rawTag.copy();
        CompoundTag structural = super.save();
        for (String key : structural.getAllKeys()) {
            Tag value = structural.get(key);
            if (value != null) result.put(key, value.copy());
        }
        result.putBoolean(MISSING_MARKER, true);
        return result;
    }

    @Override
    public void load(CompoundTag tag) {
        rawTag = tag.copy();
        super.load(tag);
    }
}
