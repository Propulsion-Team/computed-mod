package dev.propulsionteam.computed.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphAnalyzerTest {
    @Test
    void producesStableTopologicalOrderAndFindsCombinationalCycles() {
        UUID first = uuid(1);
        UUID second = uuid(2);
        UUID third = uuid(3);
        ComputedGraph acyclic = new ComputedGraph(
                uuid(100),
                List.of(node(third), node(first), node(second)),
                List.of(connection(first, second), connection(second, third)));

        GraphAnalysisResult ordered = GraphAnalyzer.analyze(acyclic, ignored -> false);
        GraphAnalysisResult repeated = GraphAnalyzer.analyze(acyclic, ignored -> false);

        assertEquals(List.of(first, second, third), ordered.executionOrder());
        assertEquals(ordered.executionOrder(), repeated.executionOrder());
        assertTrue(ordered.combinationalCycles().isEmpty());

        ComputedGraph cyclic = new ComputedGraph(
                uuid(101),
                List.of(node(first), node(second)),
                List.of(connection(first, second), connection(second, first)));
        GraphAnalysisResult cycle = GraphAnalyzer.analyze(cyclic, ignored -> false);

        assertEquals(List.of(List.of(first, second)), cycle.combinationalCycles());
        assertTrue(cycle.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("combinational_cycle")));
    }

    @Test
    void treatsStateBoundariesAsCycleBreaksAndValidatesPortTypes() {
        UUID first = uuid(1);
        UUID second = uuid(2);
        ComputedGraph statefulCycle = new ComputedGraph(
                uuid(102),
                List.of(node(first), node(second)),
                List.of(connection(first, second), connection(second, first)));

        GraphAnalysisResult result = GraphAnalyzer.analyze(statefulCycle, node -> node.id().equals(second));

        assertTrue(result.combinationalCycles().isEmpty());
        assertEquals(List.of(second, first), result.executionOrder());

        GraphNode booleanTarget = new GraphNode(
                uuid(3),
                "example:boolean",
                "",
                0,
                0,
                List.of(
                        new PortSnapshot("in", PortDirection.INPUT, ConnectionType.BOOLEAN, "In"),
                        new PortSnapshot("out", PortDirection.OUTPUT, ConnectionType.BOOLEAN, "Out")),
                Map.of());
        ComputedGraph invalid = new ComputedGraph(
                uuid(103),
                List.of(node(first), booleanTarget),
                List.of(new GraphConnection(uuid(300), first, "out", booleanTarget.id(), "in", List.of())));

        assertTrue(GraphAnalyzer.analyze(invalid, ignored -> false)
                .diagnostics()
                .stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("incompatible_ports")));
    }

    private static GraphNode node(UUID id) {
        return new GraphNode(
                id,
                "example:node",
                "",
                0,
                0,
                List.of(
                        new PortSnapshot("in", PortDirection.INPUT, ConnectionType.NUMBER, "In"),
                        new PortSnapshot("out", PortDirection.OUTPUT, ConnectionType.NUMBER, "Out")),
                Map.of());
    }

    private static GraphConnection connection(UUID source, UUID target) {
        return new GraphConnection(UUID.randomUUID(), source, "out", target, "in", List.of());
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
