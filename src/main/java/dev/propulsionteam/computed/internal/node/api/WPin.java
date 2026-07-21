package dev.propulsionteam.computed.internal.node.api;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Represents a connection point on a node.
 * Pins can be of type INPUT or OUTPUT and carry a typed value (number, string, or widget).
 */
public class WPin extends WElement {
    /** Direction of data flow. */
    public enum Type { INPUT, OUTPUT }

    /**
     * The kind of value carried over the pin. Connections are only allowed between matching data types.
     */
    public enum DataType { NUMBER, STRING, WIDGET }

    /** Default editor accent for each data type when callers don't specify one. */
    public static final int COLOR_NUMBER_DEFAULT = 0xFFFFFFFF;
    public static final int COLOR_STRING_DEFAULT = 0xFFFFC830;
    public static final int COLOR_WIDGET_DEFAULT = 0xFF40D0FF;

    private String name;
    /** Stable persistence identity. Labels may change without breaking saved connections. */
    private String stableKey;
    private final Type type;
    private final DataType dataType;
    private final int color;
    private boolean connected;
    private double value;
    private String stringValue = "";
    private Object widgetValue;

    /**
     * Creates a NUMBER pin (back-compat constructor used by existing nodes).
     */
    public WPin(String name, Type type, int color) {
        this(name, type, DataType.NUMBER, color);
    }

    public WPin(String name, Type type, DataType dataType, int color) {
        this(null, name, type, dataType, color);
    }

    public WPin(String stableKey, String name, Type type, DataType dataType, int color) {
        this.stableKey = stableKey == null || stableKey.isBlank() ? null : stableKey;
        this.name = name;
        this.type = type;
        this.dataType = dataType;
        this.color = color;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        // Pin rendering is owned by WNode for layout alignment.
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }
    public String getStableKey() { return stableKey; }
    public void setStableKey(String stableKey) {
        this.stableKey = stableKey == null || stableKey.isBlank() ? null : stableKey;
    }
    public Type getType() { return type; }
    public DataType getDataType() { return dataType; }
    public int getColor() { return color; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (stableKey != null) tag.putString("portKey", stableKey);
        tag.putString("name", name);
        tag.putString("dataType", dataType.name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("color", color);
        switch (dataType) {
            case NUMBER -> tag.putDouble("value", value);
            case STRING -> tag.putString("s", stringValue == null ? "" : stringValue);
            case WIDGET -> {
                // Widget values are recomputed every tick from connected widget nodes — nothing to persist.
            }
        }
        return tag;
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("portKey")) {
            setStableKey(tag.getString("portKey"));
        }
        if (tag.contains("name")) {
            setName(tag.getString("name"));
        }
        switch (dataType) {
            case NUMBER -> this.value = tag.getDouble("value");
            case STRING -> this.stringValue = tag.contains("s") ? tag.getString("s") : "";
            case WIDGET -> {
                // see save()
            }
        }
    }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getStringValue() { return stringValue == null ? "" : stringValue; }
    public void setStringValue(String s) { this.stringValue = s == null ? "" : s; }

    public Object getWidgetValue() { return widgetValue; }
    public void setWidgetValue(Object o) { this.widgetValue = o; }
}
