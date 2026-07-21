package dev.propulsionteam.computed.api.node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable values for a declared set of typed node properties. */
public final class NodePropertyBag {
    private static final NodePropertyBag EMPTY =
            new NodePropertyBag(new LinkedHashMap<>(), new LinkedHashMap<>());

    private final Map<String, NodeProperty<?>> definitions;
    private final Map<String, Object> values;

    private NodePropertyBag(
            LinkedHashMap<String, NodeProperty<?>> definitions, LinkedHashMap<String, Object> values) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static NodePropertyBag empty() {
        return EMPTY;
    }

    public static Builder builder(Collection<? extends NodeProperty<?>> definitions) {
        return new Builder(definitions);
    }

    public static NodePropertyBag defaults(Collection<? extends NodeProperty<?>> definitions) {
        return builder(definitions).build();
    }

    public List<NodeProperty<?>> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<NodeProperty<?>> definition(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public <T> T get(NodeProperty<T> property) {
        requireDefinition(property);
        return property.castAndValidate(values.get(property.key()));
    }

    public <T> NodePropertyBag with(NodeProperty<T> property, T value) {
        requireDefinition(property);
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(property.key(), property.castAndValidate(value));
        return new NodePropertyBag(new LinkedHashMap<>(definitions), copy);
    }

    /** A read-only persistence-oriented view keyed by stable property keys. */
    public Map<String, Object> values() {
        return values;
    }

    private void requireDefinition(NodeProperty<?> property) {
        Objects.requireNonNull(property, "property");
        NodeProperty<?> declared = definitions.get(property.key());
        if (declared != property) {
            throw new IllegalArgumentException("Property '" + property.key() + "' is not declared by this bag");
        }
    }

    public static final class Builder {
        private final LinkedHashMap<String, NodeProperty<?>> definitions = new LinkedHashMap<>();
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private Builder(Collection<? extends NodeProperty<?>> suppliedDefinitions) {
            Objects.requireNonNull(suppliedDefinitions, "definitions");
            for (NodeProperty<?> property : suppliedDefinitions) {
                Objects.requireNonNull(property, "property");
                if (definitions.putIfAbsent(property.key(), property) != null) {
                    throw new IllegalArgumentException("Duplicate property key '" + property.key() + "'");
                }
                values.put(property.key(), property.defaultValue());
            }
        }

        public <T> Builder set(NodeProperty<T> property, T value) {
            Objects.requireNonNull(property, "property");
            if (definitions.get(property.key()) != property) {
                throw new IllegalArgumentException("Property '" + property.key() + "' is not declared by this bag");
            }
            values.put(property.key(), property.castAndValidate(value));
            return this;
        }

        public NodePropertyBag build() {
            return definitions.isEmpty()
                    ? EMPTY
                    : new NodePropertyBag(definitions, values);
        }
    }
}
