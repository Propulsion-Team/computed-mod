package dev.propulsionteam.computed.graph;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ComputedGraph(UUID id, List<GraphNode> nodes, List<GraphConnection> connections) {
    public ComputedGraph {
        Objects.requireNonNull(id, "id");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        connections = connections == null ? List.of() : List.copyOf(connections);
    }

    public Optional<GraphNode> node(UUID id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }
}
