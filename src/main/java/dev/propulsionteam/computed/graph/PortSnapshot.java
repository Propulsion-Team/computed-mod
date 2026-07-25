package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.Objects;

public record PortSnapshot(String id, PortDirection direction, ConnectionType type, String label) {
    public PortSnapshot {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException("Invalid port snapshot id: " + id);
        }
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(type, "type");
        label = label == null ? "" : label;
    }
}
