package net.minecraft.nbt;

/** Minimal structural NBT signature for plain unit tests outside the game runtime. */
public interface Tag {
    int TAG_END = 0;
    int TAG_BYTE = 1;
    int TAG_INT = 3;
    int TAG_LONG = 4;
    int TAG_DOUBLE = 6;
    int TAG_STRING = 8;
    int TAG_LIST = 9;
    int TAG_COMPOUND = 10;
    int TAG_INT_ARRAY = 11;

    byte getId();

    Tag copy();
}
