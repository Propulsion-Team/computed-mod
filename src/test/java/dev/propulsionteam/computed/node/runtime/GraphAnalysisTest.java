package dev.propulsionteam.computed.node.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.node.program.ConnectionModel;
import dev.propulsionteam.computed.node.program.GraphModel;
import dev.propulsionteam.computed.node.program.NodeModel;
import dev.propulsionteam.computed.node.program.NodeModel.PlaceholderStatus;
import dev.propulsionteam.computed.node.program.PortId;
import dev.propulsionteam.computed.node.program.PortModel;
import dev.propulsionteam.computed.node.program.PortModel.Direction;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class GraphAnalysisTest {
    private static final PortId INPUT = new PortId("input");
    private static final PortId OUTPUT = new PortId("output");

    @Test
    void topologicalOrderIsStableAcrossInsertionOrderAndUsesUuidAsTieBreaker() {
        NodeModel first = node(1, "computed:pure");
        NodeModel second = node(2, "computed:pure");
        NodeModel join = node(3, "computed:pure");
        ConnectionModel firstToJoin = connection(11, first, join);
        ConnectionModel secondToJoin = connection(12, second, join);

        GraphModel scrambled = graph(
                List.of(join, second, first),
                List.of(secondToJoin, firstToJoin));
        GraphModel reversed = graph(
                List.of(first, join, second),
                List.of(firstToJoin, secondToJoin));

        List<UUID> expected = List.of(first.id(), second.id(), join.id());
        assertEquals(expected, GraphAnalysis.analyze(scrambled, ignored -> false).topologicalOrder());
        assertEquals(expected, GraphAnalysis.analyze(reversed, ignored -> false).topologicalOrder());
    }

    @Test
    void combinationalCycleDisablesOnlyItsStronglyConnectedComponent() {
        NodeModel first = node(1, "computed:pure");
        NodeModel second = node(2, "computed:pure");
        NodeModel independent = node(3, "computed:pure");
        ConnectionModel forward = connection(11, first, second);
        ConnectionModel back = connection(12, second, first);

        GraphAnalysis.AnalysisResult result = GraphAnalysis.analyze(
                graph(List.of(independent, second, first), List.of(back, forward)),
                ignored -> false);

        assertEquals(List.of(independent.id()), result.topologicalOrder());
        assertEquals(List.of(List.of(first.id(), second.id())), result.combinationalCycles());
        assertEquals(java.util.Set.of(first.id(), second.id()), result.disabledNodes());
        assertEquals(java.util.Set.of(forward.id(), back.id()), result.invalidConnections());
        assertFalse(result.executable(first.id()));
        assertTrue(result.executable(independent.id()));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("combinational_cycle")));
    }

    @Test
    void stateBoundaryBreaksFeedbackWhileRemainingScheduledAfterItsCurrentInput() {
        NodeModel pure = node(1, "computed:pure");
        NodeModel memory = node(2, "computed:memory");

        GraphAnalysis.AnalysisResult result = GraphAnalysis.analyze(
                graph(
                        List.of(memory, pure),
                        List.of(connection(11, pure, memory), connection(12, memory, pure))),
                type -> type.equals("computed:memory"));

        assertTrue(result.combinationalCycles().isEmpty());
        assertTrue(result.disabledNodes().isEmpty());
        assertTrue(result.invalidConnections().isEmpty());
        assertEquals(List.of(pure.id(), memory.id()), result.topologicalOrder());
    }

    @Test
    void placeholderAndInvalidStablePortDoNotDisableIndependentValidNodes() {
        NodeModel placeholder = new NodeModel(
                uuid(1),
                "missing:addon_node",
                "missing:addon_node",
                "Unavailable",
                0,
                0,
                new CompoundTag(),
                new CompoundTag(),
                ports(),
                PlaceholderStatus.MISSING_TYPE,
                new CompoundTag());
        NodeModel valid = node(2, "computed:pure");
        ConnectionModel invalidPort = new ConnectionModel(
                uuid(11),
                valid.id(),
                new PortId("renamed-output"),
                placeholder.id(),
                INPUT,
                List.of(),
                new CompoundTag());

        GraphAnalysis.AnalysisResult result =
                GraphAnalysis.analyze(graph(List.of(valid, placeholder), List.of(invalidPort)), ignored -> false);

        assertEquals(java.util.Set.of(placeholder.id()), result.disabledNodes());
        assertEquals(java.util.Set.of(invalidPort.id()), result.invalidConnections());
        assertEquals(List.of(valid.id()), result.topologicalOrder());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("placeholder_node_disabled")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("invalid_connection_port")));
    }

    @Test
    void numberToStringConnectionMatchesRuntimeConversionPolicy() {
        NodeModel numberSource = nodeWithTypes(1, "number", "number");
        NodeModel stringTarget = nodeWithTypes(2, "string", "string");

        GraphAnalysis.AnalysisResult result = GraphAnalysis.analyze(
                graph(List.of(stringTarget, numberSource), List.of(connection(11, numberSource, stringTarget))),
                ignored -> false);

        assertTrue(result.invalidConnections().isEmpty());
        assertEquals(List.of(numberSource.id(), stringTarget.id()), result.topologicalOrder());
        assertTrue(GraphAnalysis.compatibleValueTypes("number", "string"));
        assertFalse(GraphAnalysis.compatibleValueTypes("string", "number"));
    }

    private static GraphModel graph(List<NodeModel> nodes, List<ConnectionModel> connections) {
        return new GraphModel(uuid(100), nodes, connections, List.of());
    }

    private static NodeModel node(long id, String type) {
        return nodeWithTypes(id, "number", "number", type);
    }

    private static NodeModel nodeWithTypes(long id, String inputType, String outputType) {
        return nodeWithTypes(id, inputType, outputType, "computed:pure");
    }

    private static NodeModel nodeWithTypes(long id, String inputType, String outputType, String type) {
        return new NodeModel(
                uuid(id),
                type,
                type,
                type,
                0,
                0,
                new CompoundTag(),
                new CompoundTag(),
                List.of(
                        new PortModel(INPUT, Direction.INPUT, inputType, "Input", new CompoundTag()),
                        new PortModel(OUTPUT, Direction.OUTPUT, outputType, "Output", new CompoundTag())),
                PlaceholderStatus.RESOLVED,
                new CompoundTag());
    }

    private static List<PortModel> ports() {
        return List.of(
                new PortModel(INPUT, Direction.INPUT, "number", "Input", new CompoundTag()),
                new PortModel(OUTPUT, Direction.OUTPUT, "number", "Output", new CompoundTag()));
    }

    private static ConnectionModel connection(long id, NodeModel source, NodeModel target) {
        return new ConnectionModel(
                uuid(id), source.id(), OUTPUT, target.id(), INPUT, List.of(), new CompoundTag());
    }

    private static UUID uuid(long suffix) {
        return new UUID(0L, suffix);
    }
}
