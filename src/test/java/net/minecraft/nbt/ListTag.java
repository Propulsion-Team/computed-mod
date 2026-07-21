package net.minecraft.nbt;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Minimal heterogeneous list implementation sufficient for codec unit tests. */
public final class ListTag extends AbstractList<Tag> implements Tag {
    private final List<Tag> values = new ArrayList<>();

    @Override
    public boolean add(Tag value) {
        return values.add(Objects.requireNonNull(value, "value"));
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Tag get(int index) {
        return values.get(index);
    }

    public CompoundTag getCompound(int index) {
        Tag value = values.get(index);
        return value instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    @Override
    public byte getId() {
        return TAG_LIST;
    }

    @Override
    public ListTag copy() {
        ListTag copy = new ListTag();
        for (Tag value : values) {
            copy.add(value.copy());
        }
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ListTag tag && values.equals(tag.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
