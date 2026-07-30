package dev.propulsionteam.computed.network;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

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

    @Test
    void rejectsWrongFieldTypesAndUndeclaredFields() {
        var definition = BundledLuaLibrary.load().get("computed:constant");
        GraphNode wrongType = new GraphNode(
                UUID.randomUUID(),
                definition.id(),
                definition.hash(),
                0,
                0,
                List.of(new PortSnapshot(
                        "value",
                        PortDirection.OUTPUT,
                        ConnectionType.NUMBER,
                        "value")),
                Map.of("value", new LuaStateCodec().encode(LuaValue.valueOf("not a number"))));
        ComputedProgramV3 wrongTypeProgram = new ComputedProgramV3(
                0,
                new ComputedGraph(UUID.randomUUID(), List.of(wrongType), List.of()),
                Map.of(),
                Map.of(),
                null);
        GraphNode extraField = new GraphNode(
                wrongType.id(),
                definition.id(),
                definition.hash(),
                0,
                0,
                wrongType.ports(),
                Map.of("unknown", new LuaStateCodec().encode(LuaValue.ZERO)));
        ComputedProgramV3 extraFieldProgram = new ComputedProgramV3(
                0,
                new ComputedGraph(UUID.randomUUID(), List.of(extraField), List.of()),
                Map.of(),
                Map.of(),
                null);

        assertTrue(ComputerEditPolicy.programShape(wrongTypeProgram).contains("invalid"));
        assertTrue(ComputerEditPolicy.programShape(extraFieldProgram).contains("undeclared"));
    }

    @Test
    void restrictsRunCommandToCreativeComputers() {
        var definition = BundledLuaLibrary.load().get("computed:command");
        GraphNode command = new GraphNode(
                UUID.randomUUID(),
                definition.id(),
                definition.hash(),
                0,
                0,
                List.of(),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(UUID.randomUUID(), List.of(command), List.of()),
                Map.of(),
                Map.of(),
                null);

        assertTrue(ComputerEditPolicy.computerType(program, false).contains("Creative Computer"));
        assertNull(ComputerEditPolicy.computerType(program, true));
        assertNull(ComputerEditPolicy.computerType(
                ComputedProgramV3.empty(UUID.randomUUID()), false));
    }
}
