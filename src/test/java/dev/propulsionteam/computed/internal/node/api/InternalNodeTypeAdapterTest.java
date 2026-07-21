package dev.propulsionteam.computed.internal.node.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import dev.propulsionteam.computed.api.node.DiagnosticSink;
import dev.propulsionteam.computed.api.node.ExecutionPolicy;
import dev.propulsionteam.computed.api.node.NodeExecutionContext;
import dev.propulsionteam.computed.api.node.NodePropertyBag;
import dev.propulsionteam.computed.api.node.NodeType;
import dev.propulsionteam.computed.api.node.PortKey;
import dev.propulsionteam.computed.api.node.PortType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class InternalNodeTypeAdapterTest {
    private static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("computed_test", "category");

    @Test
    void descriptorExecutesTypedInternalNodeWithoutMutatingPriorState() throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("computed_test", "sum");
        NodeRegistry.NodeFactory factory = (x, y) -> {
            WNode node = new WNode(id, "Sum", x, y);
            node.addInput("lhs", "Left renamed", WPin.DataType.NUMBER, 0xFFFFFFFF);
            node.addInput("rhs", "Right", WPin.DataType.NUMBER, 0xFFFFFFFF);
            node.addOutput("sum", "Sum", WPin.DataType.NUMBER, 0xFFFFFFFF);
            node.setEvaluator(n -> n.getOutputs().getFirst().setValue(
                    n.getInputs().get(0).getValue() + n.getInputs().get(1).getValue()));
            return node;
        };
        NodeType<CompoundTag> type = InternalNodeTypeAdapter.describe(factory, factory.create(0, 0), CATEGORY);
        FakeContext context = new FakeContext();
        context.input(PortKey.of("lhs", PortType.NUMBER), 2.25);
        context.input(PortKey.of("rhs", PortType.NUMBER), 3.75);
        CompoundTag prior = type.defaultState();
        CompoundTag priorSnapshot = prior.copy();

        CompoundTag next = type.evaluator().execute(prior, context);

        assertEquals(6.0, context.output(PortKey.of("sum", PortType.NUMBER)));
        assertEquals(priorSnapshot, prior, "the adapter must treat prior state as immutable");
        assertNotSame(prior, next);
        assertEquals(ExecutionPolicy.INPUT_DRIVEN, type.executionPolicy());
        assertEquals("Left renamed", type.schema(type.defaultProperties()).requirePort("lhs").label().getString());
    }

    @Test
    void fullNbtStateFlowsFromOnePublicExecutionToTheNext() throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("computed_test", "accumulator");
        NodeRegistry.NodeFactory factory = (x, y) -> new AccumulatorNode(id, x, y);
        NodeType<CompoundTag> type = InternalNodeTypeAdapter.describe(factory, factory.create(0, 0), CATEGORY);
        PortKey<Double> add = PortKey.of("add", PortType.NUMBER);
        PortKey<Double> total = PortKey.of("total", PortType.NUMBER);
        FakeContext first = new FakeContext();
        first.input(add, 4.0);

        CompoundTag state1 = type.evaluator().execute(type.defaultState(), first);
        assertEquals(4.0, first.output(total));

        FakeContext second = new FakeContext();
        second.input(add, 1.5);
        CompoundTag state2 = type.evaluator().execute(state1, second);

        assertEquals(5.5, second.output(total));
        assertEquals(5.5, state2.getDouble("Total"));
        assertEquals(ExecutionPolicy.EVERY_GRAPH_STEP, type.executionPolicy());
        assertEquals(true, type.stateBoundary());
    }

    @Test
    void tickDescriptorUsesGameTicksAndPersistsItsAccumulator() throws Exception {
        NodeRegistry.NodeFactory factory = (x, y) -> {
            WNode node = new WNode(WGraph.TICK_NODE_TYPE, "Tick", x, y);
            node.addOutput("Tick", WPin.DataType.NUMBER, 0xFFFFFFFF);
            node.addOutput("Delta time", WPin.DataType.NUMBER, 0xFFFFFFFF);
            return node;
        };
        NodeType<CompoundTag> type = InternalNodeTypeAdapter.describe(factory, factory.create(0, 0), CATEGORY);
        FakeContext first = new FakeContext();
        first.gameTick = 100;

        CompoundTag state1 = type.evaluator().execute(type.defaultState(), first);

        assertEquals(1.0, first.output(PortKey.of("output.tick", PortType.NUMBER)));
        assertEquals(0.05, first.output(PortKey.of("output.delta_time", PortType.NUMBER)), 1.0e-9);
        assertEquals(ExecutionPolicy.EVERY_GAME_TICK, type.executionPolicy());

        FakeContext second = new FakeContext();
        second.gameTick = 101;
        type.evaluator().execute(state1, second);
        assertEquals(1.0, second.output(PortKey.of("output.tick", PortType.NUMBER)));
        assertEquals(0.05, second.output(PortKey.of("output.delta_time", PortType.NUMBER)), 1.0e-9);
    }

    private static final class AccumulatorNode extends WNode {
        private double total;

        private AccumulatorNode(ResourceLocation id, int x, int y) {
            super(id, "Accumulator", x, y);
            addInput("add", "Add", WPin.DataType.NUMBER, 0xFFFFFFFF);
            addOutput("total", "Total", WPin.DataType.NUMBER, 0xFFFFFFFF);
            setEvaluator(node -> {
                total += node.getInputs().getFirst().getValue();
                node.getOutputs().getFirst().setValue(total);
            });
        }

        @Override
        public boolean isStateBoundary() {
            return true;
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = super.save();
            tag.putDouble("Total", total);
            return tag;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            total = tag.getDouble("Total");
            getOutputs().getFirst().setValue(total);
        }
    }

    private static final class FakeContext implements NodeExecutionContext {
        private final Map<PortKey<?>, Object> inputs = new HashMap<>();
        private final Map<PortKey<?>, Object> outputs = new HashMap<>();
        private long gameTick;

        private <T> void input(PortKey<T> key, T value) {
            inputs.put(key, value);
        }

        private <T> T output(PortKey<T> key) {
            return key.type().castOrDefault(outputs.get(key));
        }

        @Override
        public NodePropertyBag properties() {
            return NodePropertyBag.empty();
        }

        @Override
        public <T> T input(PortKey<T> key) {
            return key.type().castOrDefault(inputs.get(key));
        }

        @Override
        public <T> void output(PortKey<T> key, T value) {
            outputs.put(key, value);
        }

        @Override
        public boolean isInputConnected(PortKey<?> key) {
            return inputs.containsKey(key);
        }

        @Override
        public long gameTick() {
            return gameTick;
        }

        @Override
        public long graphStep() {
            return gameTick;
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
            return Optional.empty();
        }

        @Override
        public boolean sideEffectsAllowed() {
            return false;
        }

        @Override
        public DiagnosticSink diagnostics() {
            return ignored -> {};
        }
    }
}
