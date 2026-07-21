package net.minecraft.nbt;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Minimal map-backed compound NBT implementation for deterministic codec unit tests. */
public final class CompoundTag implements Tag {
    /** Mirrors the game API used by public node state descriptors. */
    public static final Codec<CompoundTag> CODEC = new Codec<>() {};

    private final Map<String, Tag> values = new LinkedHashMap<>();

    public Tag put(String key, Tag value) {
        return values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    public Tag get(String key) {
        return values.get(key);
    }

    public void remove(String key) {
        values.remove(key);
    }

    public void putString(String key, String value) {
        put(key, StringTag.valueOf(value));
    }

    public String getString(String key) {
        Tag value = values.get(key);
        return value instanceof StringTag string ? string.getAsString() : "";
    }

    public void putInt(String key, int value) {
        put(key, new ScalarTag(TAG_INT, value));
    }

    public int getInt(String key) {
        Number value = number(key);
        return value == null ? 0 : value.intValue();
    }

    public void putLong(String key, long value) {
        put(key, new ScalarTag(TAG_LONG, value));
    }

    public long getLong(String key) {
        Number value = number(key);
        return value == null ? 0L : value.longValue();
    }

    public void putDouble(String key, double value) {
        put(key, new ScalarTag(TAG_DOUBLE, value));
    }

    public double getDouble(String key) {
        Number value = number(key);
        return value == null ? 0.0D : value.doubleValue();
    }

    public void putBoolean(String key, boolean value) {
        put(key, new ScalarTag(TAG_BYTE, value ? (byte) 1 : (byte) 0));
    }

    public boolean getBoolean(String key) {
        Number value = number(key);
        return value != null && value.byteValue() != 0;
    }

    public void putUUID(String key, UUID value) {
        put(key, new UuidTag(value));
    }

    public UUID getUUID(String key) {
        Tag value = values.get(key);
        if (value instanceof UuidTag uuid) return uuid.value;
        throw new IllegalArgumentException("Not a UUID: " + key);
    }

    public boolean hasUUID(String key) {
        return values.get(key) instanceof UuidTag;
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public boolean contains(String key, int type) {
        Tag value = values.get(key);
        return value != null && value.getId() == type;
    }

    public CompoundTag getCompound(String key) {
        Tag value = values.get(key);
        return value instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public ListTag getList(String key, int ignoredElementType) {
        Tag value = values.get(key);
        return value instanceof ListTag list ? list : new ListTag();
    }

    public Set<String> getAllKeys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public byte getId() {
        return TAG_COMPOUND;
    }

    @Override
    public CompoundTag copy() {
        CompoundTag copy = new CompoundTag();
        values.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CompoundTag tag && values.equals(tag.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    private Number number(String key) {
        Tag value = values.get(key);
        return value instanceof ScalarTag scalar && scalar.value instanceof Number number ? number : null;
    }

    private record ScalarTag(int id, Object value) implements Tag {
        private ScalarTag {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public byte getId() {
            return (byte) id;
        }

        @Override
        public ScalarTag copy() {
            return this;
        }
    }

    private static final class UuidTag implements Tag {
        private final UUID value;

        private UuidTag(UUID value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public byte getId() {
            return TAG_INT_ARRAY;
        }

        @Override
        public UuidTag copy() {
            return this;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof UuidTag tag && value.equals(tag.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
