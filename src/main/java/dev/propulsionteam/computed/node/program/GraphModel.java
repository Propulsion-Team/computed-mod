package dev.propulsionteam.computed.node.program;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable persistent graph model. Invalid or dangling data is retained for diagnostics and repair. */
public record GraphModel(
        UUID id,
        List<NodeModel> nodes,
        List<ConnectionModel> connections,
        List<SectionModel> sections,
        CompoundTag metadata,
        CompoundTag rawTag) {

    public GraphModel {
        Objects.requireNonNull(id, "id");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        connections = connections == null ? List.of() : List.copyOf(connections);
        sections = sections == null ? List.of() : List.copyOf(sections);
        metadata = metadata == null ? new CompoundTag() : metadata.copy();
        rawTag = rawTag == null ? new CompoundTag() : rawTag.copy();
    }

    public GraphModel(UUID id, List<NodeModel> nodes, List<ConnectionModel> connections, List<SectionModel> sections) {
        this(id, nodes, connections, sections, new CompoundTag(), new CompoundTag());
    }

    @Override
    public CompoundTag metadata() {
        return metadata.copy();
    }

    @Override
    public CompoundTag rawTag() {
        return rawTag.copy();
    }

    public Optional<NodeModel> node(UUID nodeId) {
        return nodes.stream().filter(node -> node.id().equals(nodeId)).findFirst();
    }
}
