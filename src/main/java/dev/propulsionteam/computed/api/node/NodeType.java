package dev.propulsionteam.computed.api.node;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Immutable public definition of a Computed node type. */
public final class NodeType<S> {
    private final ResourceLocation id;
    private final Component title;
    private final ResourceLocation category;
    private final NodeSchemaFactory schemaFactory;
    private final List<NodeProperty<?>> properties;
    private final NodePropertyBag defaultProperties;
    private final Codec<S> stateCodec;
    private final Supplier<? extends S> defaultStateFactory;
    private final NodeExecutor<S> evaluator;
    private final boolean stateBoundary;
    private final ExecutionPolicy executionPolicy;

    private NodeType(Builder<S> builder) {
        id = builder.id;
        title = Objects.requireNonNull(builder.title, "Node type '" + id + "' has no title");
        category = Objects.requireNonNull(builder.category, "Node type '" + id + "' has no category");
        schemaFactory = Objects.requireNonNull(builder.schemaFactory, "Node type '" + id + "' has no schema");
        properties = List.copyOf(builder.properties);
        defaultProperties = NodePropertyBag.defaults(properties);
        Objects.requireNonNull(schemaFactory.create(defaultProperties), "Schema factory returned null for '" + id + "'");
        stateCodec = Objects.requireNonNull(builder.stateCodec, "Node type '" + id + "' has no state codec");
        defaultStateFactory = Objects.requireNonNull(
                builder.defaultStateFactory, "Node type '" + id + "' has no default state");
        Objects.requireNonNull(defaultState(), "Default state for node type '" + id + "'");
        evaluator = Objects.requireNonNull(builder.evaluator, "Node type '" + id + "' has no evaluator");
        stateBoundary = builder.stateBoundary;
        executionPolicy = builder.executionPolicy;
    }

    public static <S> Builder<S> builder(ResourceLocation id) {
        return new Builder<>(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public Component title() {
        return title;
    }

    public ResourceLocation category() {
        return category;
    }

    public List<NodeProperty<?>> properties() {
        return properties;
    }

    public NodePropertyBag defaultProperties() {
        return defaultProperties;
    }

    public NodeSchema schema(NodePropertyBag properties) {
        Objects.requireNonNull(properties, "properties");
        return Objects.requireNonNull(schemaFactory.create(properties), "Schema factory returned null for '" + id + "'");
    }

    public NodeSchemaFactory schemaFactory() {
        return schemaFactory;
    }

    public Codec<S> stateCodec() {
        return stateCodec;
    }

    public S defaultState() {
        return defaultStateFactory.get();
    }

    public NodeExecutor<S> evaluator() {
        return evaluator;
    }

    public boolean stateBoundary() {
        return stateBoundary;
    }

    public ExecutionPolicy executionPolicy() {
        return executionPolicy;
    }

    public static final class Builder<S> {
        private final ResourceLocation id;
        private Component title;
        private ResourceLocation category = ComputedNodeApi.UNCATEGORIZED_CATEGORY;
        private NodeSchemaFactory schemaFactory;
        private final List<NodeProperty<?>> properties = new ArrayList<>();
        private Codec<S> stateCodec;
        private Supplier<? extends S> defaultStateFactory;
        private NodeExecutor<S> evaluator;
        private boolean stateBoundary;
        private ExecutionPolicy executionPolicy = ExecutionPolicy.INPUT_DRIVEN;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder<S> title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder<S> category(ResourceLocation category) {
            this.category = Objects.requireNonNull(category, "category");
            return this;
        }

        public Builder<S> schema(NodeSchema schema) {
            schemaFactory = NodeSchemaFactory.fixed(schema);
            return this;
        }

        public Builder<S> schema(NodeSchemaFactory schemaFactory) {
            this.schemaFactory = Objects.requireNonNull(schemaFactory, "schemaFactory");
            return this;
        }

        public Builder<S> property(NodeProperty<?> property) {
            Objects.requireNonNull(property, "property");
            if (properties.stream().anyMatch(existing -> existing.key().equals(property.key()))) {
                throw new IllegalArgumentException(
                        "Duplicate property key '" + property.key() + "' on node type '" + id + "'");
            }
            properties.add(property);
            return this;
        }

        public Builder<S> properties(Iterable<? extends NodeProperty<?>> properties) {
            Objects.requireNonNull(properties, "properties").forEach(this::property);
            return this;
        }

        public Builder<S> stateCodec(Codec<S> stateCodec) {
            this.stateCodec = Objects.requireNonNull(stateCodec, "stateCodec");
            return this;
        }

        public Builder<S> defaultState(S state) {
            Objects.requireNonNull(state, "state");
            defaultStateFactory = () -> state;
            return this;
        }

        public Builder<S> defaultState(Supplier<? extends S> stateFactory) {
            defaultStateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
            return this;
        }

        public Builder<S> evaluator(NodeExecutor<S> evaluator) {
            this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
            return this;
        }

        public Builder<S> stateBoundary(boolean stateBoundary) {
            this.stateBoundary = stateBoundary;
            return this;
        }

        public Builder<S> executionPolicy(ExecutionPolicy executionPolicy) {
            this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy");
            return this;
        }

        public NodeType<S> build() {
            return new NodeType<>(this);
        }
    }
}
