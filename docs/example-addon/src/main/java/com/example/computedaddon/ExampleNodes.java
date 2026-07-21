package com.example.computedaddon;

import com.mojang.serialization.Codec;
import dev.propulsionteam.computed.api.node.ComputedNodeApi;
import dev.propulsionteam.computed.api.node.ExecutionPolicy;
import dev.propulsionteam.computed.api.node.NodeProperty;
import dev.propulsionteam.computed.api.node.NodeSchema;
import dev.propulsionteam.computed.api.node.NodeType;
import dev.propulsionteam.computed.api.node.PortKey;
import dev.propulsionteam.computed.api.node.PortType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Common-side registration example. */
public final class ExampleNodes {
    public static final ResourceLocation CATEGORY = id("examples");
    public static final PortKey<Double> AMOUNT = PortKey.of("amount", PortType.NUMBER);
    public static final PortKey<Double> RESET = PortKey.of("reset", PortType.NUMBER);
    public static final PortKey<Double> TOTAL = PortKey.of("total", PortType.NUMBER);
    public static final NodeProperty<Double> SCALE = NodeProperty.builder(
                    "scale", Component.literal("Scale"), Double.class, Codec.DOUBLE)
            .defaultValue(1.0D)
            .validator(value -> Double.isFinite(value) && value >= 0.0D, "must be finite and non-negative")
            .build();

    private static final Codec<AccumulatorState> STATE_CODEC =
            Codec.DOUBLE.xmap(AccumulatorState::new, AccumulatorState::total);

    public static final NodeType<AccumulatorState> ACCUMULATOR = NodeType.<AccumulatorState>builder(id("accumulator"))
            .title(Component.literal("Accumulator"))
            .category(CATEGORY)
            .property(SCALE)
            .schema(NodeSchema.builder()
                    .input(AMOUNT, Component.literal("Amount"))
                    .input(RESET, Component.literal("Reset"))
                    .output(TOTAL, Component.literal("Total"))
                    .build())
            .stateCodec(STATE_CODEC)
            .defaultState(new AccumulatorState(0.0D))
            .stateBoundary(true)
            .executionPolicy(ExecutionPolicy.EVERY_GRAPH_STEP)
            .evaluator((priorState, context) -> {
                double nextTotal = context.input(RESET) > 0.0D
                        ? 0.0D
                        : priorState.total() + context.input(AMOUNT) * context.properties().get(SCALE);
                context.output(TOTAL, nextTotal);
                return new AccumulatorState(nextTotal);
            })
            .build();

    private ExampleNodes() {}

    public static void register() {
        ComputedNodeApi.registerCategory(CATEGORY, Component.literal("Example addon"), ComputedNodeApi.ROOT_CATEGORY);
        ComputedNodeApi.register(ACCUMULATOR);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("computed_example", path);
    }

    /** State values are replaced after each graph step rather than mutated in place. */
    public record AccumulatorState(double total) {}
}
