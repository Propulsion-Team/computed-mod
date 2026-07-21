package dev.propulsionteam.computed.api.node;

import java.util.Objects;
import java.util.regex.Pattern;

/** A stable, typed identifier for one port in a node type's schema. */
public final class PortKey<T> {
    private static final Pattern VALID_ID = Pattern.compile("[a-z][a-z0-9_.-]*");

    private final String id;
    private final PortType<T> type;

    private PortKey(String id, PortType<T> type) {
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Port key must match " + VALID_ID.pattern() + ", got: " + id);
        }
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> PortKey<T> of(String id, PortType<T> type) {
        return new PortKey<>(id, type);
    }

    public String id() {
        return id;
    }

    public PortType<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PortKey<?> key && id.equals(key.id) && type == key.type;
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + System.identityHashCode(type);
    }

    @Override
    public String toString() {
        return id + ':' + type.id();
    }
}
