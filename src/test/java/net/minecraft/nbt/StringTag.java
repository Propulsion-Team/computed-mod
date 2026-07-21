package net.minecraft.nbt;

import java.util.Objects;

/** Minimal immutable string NBT value used by diagnostics fixtures. */
public final class StringTag implements Tag {
    private final String value;

    private StringTag(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static StringTag valueOf(String value) {
        return new StringTag(value);
    }

    public String getAsString() {
        return value;
    }

    @Override
    public byte getId() {
        return TAG_STRING;
    }

    @Override
    public StringTag copy() {
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StringTag tag && value.equals(tag.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
