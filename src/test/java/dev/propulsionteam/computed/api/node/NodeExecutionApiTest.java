package dev.propulsionteam.computed.api.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class NodeExecutionApiTest {
    private static final PortKey<Double> DELTA = PortKey.of("delta", PortType.NUMBER);
    private static final PortKey<Double> TOTAL = PortKey.of("total", PortType.NUMBER);
    private static final NodeProperty<Double> SCALE = NodeProperty.number("scale", Component.literal("Scale"), 2.0D);

    @Test
    void evaluatorReadsTypedInputsAndPriorStateThenReturnsNextState() throws Exception {
        Codec<AccumulatorState> stateCodec = Codec.DOUBLE.xmap(AccumulatorState::new, AccumulatorState::total);
        NodeType<AccumulatorState> type = NodeType.<AccumulatorState>builder(
                        ResourceLocation.fromNamespaceAndPath("computed_api_test", "accumulator"))
                .title(Component.literal("Accumulator"))
                .schema(NodeSchema.builder()
                        .input(DELTA, Component.literal("Delta"))
                        .output(TOTAL, Component.literal("Total"))
                        .build())
                .property(SCALE)
                .stateCodec(stateCodec)
                .defaultState(new AccumulatorState(0.0D))
                .stateBoundary(true)
                .executionPolicy(ExecutionPolicy.EVERY_GRAPH_STEP)
                .evaluator((priorState, context) -> {
                    double nextTotal = priorState.total()
                            + context.input(DELTA) * context.properties().get(SCALE);
                    context.output(TOTAL, nextTotal);
                    context.report(NodeDiagnostic.info("test.evaluated", Component.literal("Evaluated")));
                    return new AccumulatorState(nextTotal);
                })
                .build();
        FakeContext context = new FakeContext(type.defaultProperties());
        context.inputs.put(DELTA, 3.0D);
        AccumulatorState prior = new AccumulatorState(4.0D);

        AccumulatorState next = type.evaluator().execute(prior, context);

        assertEquals(4.0D, prior.total(), "the immutable prior state remains unchanged");
        assertEquals(10.0D, next.total());
        assertEquals(10.0D, context.outputs.get(TOTAL));
        assertEquals(ExecutionPolicy.EVERY_GRAPH_STEP, type.executionPolicy());
        assertEquals(true, type.stateBoundary());
        assertEquals(0.0D, type.defaultState().total());
        assertSame(stateCodec, type.stateCodec());
        assertEquals("test.evaluated", context.reported.getFirst().code());
        assertFalse(context.runSideEffect(level -> {
            throw new AssertionError("preview side effect must not run");
        }));
    }

    private record AccumulatorState(double total) {}

    private static final class FakeContext implements NodeExecutionContext {
        private final NodePropertyBag properties;
        private final Map<PortKey<?>, Object> inputs = new HashMap<>();
        private final Map<PortKey<?>, Object> outputs = new HashMap<>();
        private final List<NodeDiagnostic> reported = new ArrayList<>();

        private FakeContext(NodePropertyBag properties) {
            this.properties = properties;
        }

        @Override
        public NodePropertyBag properties() {
            return properties;
        }

        @Override
        public <T> T input(PortKey<T> key) {
            return key.type().castOrDefault(inputs.get(key));
        }

        @Override
        public <T> void output(PortKey<T> key, T value) {
            if (!key.type().accepts(value)) {
                throw new IllegalArgumentException("Wrong output type for " + key);
            }
            outputs.put(key, value);
        }

        @Override
        public boolean isInputConnected(PortKey<?> key) {
            return inputs.containsKey(key);
        }

        @Override
        public long gameTick() {
            return 42L;
        }

        @Override
        public long graphStep() {
            return 7L;
        }

        @Override
        public boolean isPreview() {
            return true;
        }

        @Override
        public Optional<ServerLevel> level() {
            return Optional.empty();
        }

        @Override
        public Optional<BlockPos> origin() {
            return Optional.of(BlockPos.ZERO);
        }

        @Override
        public boolean sideEffectsAllowed() {
            return false;
        }

        @Override
        public DiagnosticSink diagnostics() {
            return reported::add;
        }
    }
}
