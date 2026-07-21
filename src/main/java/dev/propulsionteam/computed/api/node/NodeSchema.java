package dev.propulsionteam.computed.api.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Immutable, ordered set of the ports exposed by a node instance. */
public final class NodeSchema {
    private static final NodeSchema EMPTY = new NodeSchema(List.of());

    private final List<PortDefinition<?>> ports;
    private final List<PortDefinition<?>> inputs;
    private final List<PortDefinition<?>> outputs;
    private final Map<String, PortDefinition<?>> byId;

    private NodeSchema(List<PortDefinition<?>> definitions) {
        LinkedHashMap<String, PortDefinition<?>> indexed = new LinkedHashMap<>();
        ArrayList<PortDefinition<?>> inputList = new ArrayList<>();
        ArrayList<PortDefinition<?>> outputList = new ArrayList<>();
        for (PortDefinition<?> definition : definitions) {
            Objects.requireNonNull(definition, "port definition");
            PortDefinition<?> previous = indexed.putIfAbsent(definition.key().id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate port key '" + definition.key().id() + "'");
            }
            (definition.direction() == PortDirection.INPUT ? inputList : outputList).add(definition);
        }
        ports = List.copyOf(indexed.values());
        inputs = List.copyOf(inputList);
        outputs = List.copyOf(outputList);
        byId = Collections.unmodifiableMap(indexed);
    }

    public static NodeSchema empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<PortDefinition<?>> ports() {
        return ports;
    }

    public List<PortDefinition<?>> inputs() {
        return inputs;
    }

    public List<PortDefinition<?>> outputs() {
        return outputs;
    }

    public Optional<PortDefinition<?>> port(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public <T> Optional<PortDefinition<T>> port(PortKey<T> key) {
        PortDefinition<?> definition = byId.get(Objects.requireNonNull(key, "key").id());
        if (definition == null || !definition.key().equals(key)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        PortDefinition<T> typed = (PortDefinition<T>) definition;
        return Optional.of(typed);
    }

    public PortDefinition<?> requirePort(String id) {
        PortDefinition<?> definition = byId.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown port key '" + id + "'");
        }
        return definition;
    }

    public static final class Builder {
        private final List<PortDefinition<?>> definitions = new ArrayList<>();

        private Builder() {}

        public Builder port(PortDefinition<?> definition) {
            definitions.add(Objects.requireNonNull(definition, "definition"));
            return this;
        }

        public <T> Builder input(PortKey<T> key, Component label) {
            return port(PortDefinition.input(key, label));
        }

        public <T> Builder output(PortKey<T> key, Component label) {
            return port(PortDefinition.output(key, label));
        }

        public NodeSchema build() {
            return definitions.isEmpty() ? EMPTY : new NodeSchema(definitions);
        }
    }
}
