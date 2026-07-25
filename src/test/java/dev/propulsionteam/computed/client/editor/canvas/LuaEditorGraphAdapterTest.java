package dev.propulsionteam.computed.client.editor.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LuaEditorGraphAdapterTest {
    @Test
    void preservesStablePortsPositionsAndWireWaypointsAcrossEditorRoundTrip() {
        var definitions = BundledLuaLibrary.load();
        UUID sourceId = uuid(1);
        UUID targetId = uuid(2);
        UUID connectionId = uuid(3);
        GraphNode source = new GraphNode(
                sourceId,
                "computed:add",
                definitions.get("computed:add").hash(),
                10,
                20,
                List.of(
                        port("a", PortDirection.INPUT),
                        port("b", PortDirection.INPUT),
                        port("result", PortDirection.OUTPUT)),
                Map.of());
        GraphNode target = new GraphNode(
                targetId,
                "computed:counter",
                definitions.get("computed:counter").hash(),
                100,
                20,
                List.of(
                        port("increment", PortDirection.INPUT),
                        port("count", PortDirection.OUTPUT)),
                Map.of());
        GraphConnection connection = new GraphConnection(
                connectionId,
                sourceId,
                "result",
                targetId,
                "increment",
                List.of(new GraphPoint(40, 12), new GraphPoint(70, 25)));
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(10), List.of(source, target), List.of(connection)),
                Map.of(),
                Map.of(),
                null);

        var editorGraph = LuaEditorGraphAdapter.toEditorGraph(program);
        editorGraph.getNode(sourceId).setPos(15, 25);
        ComputedProgramV3 restored = LuaEditorGraphAdapter.fromEditorGraph(editorGraph, program, 1);

        assertEquals(15, restored.rootGraph().node(sourceId).orElseThrow().x());
        assertEquals(connectionId, restored.rootGraph().connections().getFirst().id());
        assertEquals("result", restored.rootGraph().connections().getFirst().sourcePort());
        assertEquals("increment", restored.rootGraph().connections().getFirst().targetPort());
        assertEquals(connection.waypoints(), restored.rootGraph().connections().getFirst().waypoints());
    }

    private static PortSnapshot port(String id, PortDirection direction) {
        return new PortSnapshot(id, direction, ConnectionType.NUMBER, id);
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
