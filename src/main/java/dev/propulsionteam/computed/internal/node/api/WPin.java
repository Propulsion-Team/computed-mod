package dev.propulsionteam.computed.internal.node.api;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

public final class WPin {
    public enum Type {
        INPUT,
        OUTPUT
    }

    public enum DataType {
        NUMBER,
        STRING,
        WIDGET
    }

    public static final int COLOR_NUMBER_DEFAULT = 0xFFFFFFFF;
    public static final int COLOR_STRING_DEFAULT = 0xFFFFC830;
    public static final int COLOR_WIDGET_DEFAULT = 0xFF40D0FF;

    private String name;
    private String stableKey;
    private final Type type;
    private final DataType dataType;
    private final int color;
    private boolean connected;

    public WPin(String name, Type type, int color) {
        this(null, name, type, DataType.NUMBER, color);
    }

    public WPin(String name, Type type, DataType dataType, int color) {
        this(null, name, type, dataType, color);
    }

    public WPin(String stableKey, String name, Type type, DataType dataType, int color) {
        this.stableKey = normalize(stableKey);
        this.name = name == null ? "" : name;
        this.type = type;
        this.dataType = dataType;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getStableKey() {
        return stableKey;
    }

    public void setStableKey(String stableKey) {
        this.stableKey = normalize(stableKey);
    }

    public Type getType() {
        return type;
    }

    public DataType getDataType() {
        return dataType;
    }

    public int getColor() {
        return color;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (stableKey != null) {
            tag.putString("portKey", stableKey);
        }
        tag.putString("name", name);
        tag.putString("dataType", dataType.name().toLowerCase(Locale.ROOT));
        tag.putInt("color", color);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("portKey")) {
            setStableKey(tag.getString("portKey"));
        }
        if (tag.contains("name")) {
            setName(tag.getString("name"));
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
