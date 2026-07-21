package dev.propulsionteam.computed.node.program;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Editor grouping rectangle stored in graph space. */
public record SectionModel(
        UUID id,
        String name,
        int x,
        int y,
        int width,
        int height,
        int bodyColorArgb,
        int layer,
        CompoundTag rawTag) {

    public static final int DEFAULT_BODY_COLOR_ARGB = 0x221F2A40;

    public SectionModel {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
        width = Math.max(1, width);
        height = Math.max(1, height);
        layer = Math.max(0, layer);
        rawTag = rawTag == null ? new CompoundTag() : rawTag.copy();
    }

    @Override
    public CompoundTag rawTag() {
        return rawTag.copy();
    }
}
