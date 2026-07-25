package dev.propulsionteam.computed.network;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComputerEditPolicyTest {
    @Test
    void enforcesDistanceBuildPermissionAndInteractionPermission() {
        assertNull(ComputerEditPolicy.access(ComputerEditPolicy.MAX_DISTANCE_SQ, true, true));
        assertTrue(ComputerEditPolicy.access(ComputerEditPolicy.MAX_DISTANCE_SQ + 1, true, true)
                .contains("far"));
        assertTrue(ComputerEditPolicy.access(1, false, true).contains("permission"));
        assertTrue(ComputerEditPolicy.access(1, true, false).contains("permission"));
        assertTrue(ComputerEditPolicy.access(Double.NaN, true, true).contains("far"));
    }

    @Test
    void rejectsStaleRevisionsAndOversizedPayloads() {
        assertNull(ComputerEditPolicy.revision(7, 7));
        assertTrue(ComputerEditPolicy.revision(7, 6).contains("stale"));
        assertNull(ComputerEditPolicy.encodedSize(ComputerEditPolicy.MAX_PROGRAM_BYTES));
        assertTrue(ComputerEditPolicy.encodedSize(ComputerEditPolicy.MAX_PROGRAM_BYTES + 1)
                .contains("size limit"));
        assertTrue(ComputerEditPolicy.encodedSize(-1).contains("measured"));
    }

    @Test
    void rejectsProgramsPastTheAuthoritativeNodeLimit() {
        List<GraphNode> nodes = new ArrayList<>();
        for (int index = 0; index <= ComputerEditPolicy.MAX_NODES; index++) {
            nodes.add(new GraphNode(
                    new UUID(0, index + 1L),
                    "missing:test",
                    "",
                    0,
                    0,
                    List.of(),
                    Map.of()));
        }
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(UUID.randomUUID(), nodes, List.of()),
                Map.of(),
                Map.of(),
                null);

        assertTrue(ComputerEditPolicy.programShape(program).contains("node limit"));
    }
}
