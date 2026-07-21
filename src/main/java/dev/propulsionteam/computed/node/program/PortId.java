package dev.propulsionteam.computed.node.program;

import java.util.Objects;

/**
 * Stable, schema-owned identifier for a node port. Unlike the legacy format, a port id is not an
 * index into the node's current input or output list.
 */
public record PortId(String value) implements Comparable<PortId> {
    public PortId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Port id must not be blank");
        }
        if (value.length() > 256) {
            throw new IllegalArgumentException("Port id is longer than 256 characters");
        }
    }

    public static PortId legacyInput(int index) {
        return new PortId("legacy.input." + index);
    }

    public static PortId legacyOutput(int index) {
        return new PortId("legacy.output." + index);
    }

    @Override
    public int compareTo(PortId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
