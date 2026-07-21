package dev.propulsionteam.computed.node.program;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Named reusable function graph. */
public record FunctionModel(
        UUID id,
        String name,
        GraphModel graph,
        CompoundTag metadata,
        CompoundTag rawTag) {

    public FunctionModel {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
        Objects.requireNonNull(graph, "graph");
        metadata = metadata == null ? new CompoundTag() : metadata.copy();
        rawTag = rawTag == null ? new CompoundTag() : rawTag.copy();
    }

    public FunctionModel(UUID id, String name, GraphModel graph) {
        this(id, name, graph, new CompoundTag(), new CompoundTag());
    }

    @Override
    public CompoundTag metadata() {
        return metadata.copy();
    }

    @Override
    public CompoundTag rawTag() {
        return rawTag.copy();
    }
}
