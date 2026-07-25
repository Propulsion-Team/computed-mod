package dev.propulsionteam.computed.graph;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GraphConnection(
        UUID id,
        UUID sourceNode,
        String sourcePort,
        UUID targetNode,
        String targetPort,
        List<GraphPoint> waypoints) {

    public GraphConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceNode, "sourceNode");
        Objects.requireNonNull(targetNode, "targetNode");
        sourcePort = requirePort(sourcePort);
        targetPort = requirePort(targetPort);
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
    }

    private static String requirePort(String id) {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException("Invalid connection port id: " + id);
        }
        return id;
    }
}
