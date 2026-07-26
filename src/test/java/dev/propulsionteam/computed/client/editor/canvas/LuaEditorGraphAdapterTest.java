package dev.propulsionteam.computed.client.editor.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
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

    @Test
    void persistsNewlyPlacedLuaNodes() {
        ComputedProgramV3 program = ComputedProgramV3.empty(uuid(20));
        var editorGraph = LuaEditorGraphAdapter.toEditorGraph(program);
        var placed = LuaEditorGraphAdapter.createEditorNode(program, "computed:math_add", 40, 60);

        assertNotNull(placed);
        editorGraph.addNode(placed);
        ComputedProgramV3 restored = LuaEditorGraphAdapter.fromEditorGraph(editorGraph, program, 1);

        assertEquals(1, restored.rootGraph().nodes().size());
        assertEquals("computed:math_add", restored.rootGraph().nodes().getFirst().definitionId());
        assertEquals(40, restored.rootGraph().nodes().getFirst().x());
        assertEquals(60, restored.rootGraph().nodes().getFirst().y());
    }

    @Test
    void replacementRetainsOnlyStableCompatiblePorts() {
        String originalSource = """
                local node = computed.node(1, "example:test", "Test")
                node:input("keep", "number")
                node:input("remove", "number")
                node:output("out", "number")
                node:on_run(function(ctx) ctx:output("out", ctx:input("keep")) end)
                return node
                """;
        var original = dev.propulsionteam.computed.graph.LuaDefinitionSource.embedded(
                1,
                "example:test",
                originalSource);
        GraphNode first = new GraphNode(
                uuid(21),
                original.id(),
                original.hash(),
                0,
                0,
                List.of(
                        port("keep", PortDirection.INPUT),
                        port("remove", PortDirection.INPUT),
                        port("out", PortDirection.OUTPUT)),
                Map.of());
        GraphNode second = new GraphNode(
                uuid(22),
                original.id(),
                original.hash(),
                100,
                0,
                first.ports(),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(
                        uuid(23),
                        List.of(first, second),
                        List.of(
                                new GraphConnection(
                                        uuid(24),
                                        first.id(),
                                        "out",
                                        second.id(),
                                        "keep",
                                        List.of()),
                                new GraphConnection(
                                        uuid(25),
                                        first.id(),
                                        "out",
                                        second.id(),
                                        "remove",
                                        List.of()))),
                Map.of(original.id(), original),
                Map.of(),
                null);
        String replacementSource = """
                local node = computed.node(1, "example:test", "Test")
                node:input("keep", "number")
                node:output("out", "number")
                node:on_run(function(ctx) ctx:output("out", ctx:input("keep")) end)
                return node
                """;
        var replacement = dev.propulsionteam.computed.graph.LuaDefinitionSource.embedded(
                1,
                "example:test",
                replacementSource);

        ComputedProgramV3 updated = LuaEditorGraphAdapter.replaceDefinition(program, replacement);

        assertEquals(1, updated.rootGraph().connections().size());
        assertEquals("keep", updated.rootGraph().connections().getFirst().targetPort());
        assertEquals(replacement.hash(), updated.rootGraph().nodes().getFirst().definitionHash());
    }

    @Test
    void editedFieldValuesRoundTripAndDuplicateIndependently() {
        String source = """
                local node = computed.node(1, "example:controls", "Controls")
                node:field("amount", "number", {
                    default = 2,
                    min = 0,
                    max = 10,
                    control = "slider",
                    step = 0.5
                })
                node:on_run(function(ctx) end)
                return node
                """;
        var definition = dev.propulsionteam.computed.graph.LuaDefinitionSource.embedded(
                1,
                "example:controls",
                source);
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(30), List.of(), List.of()),
                Map.of(definition.id(), definition),
                Map.of(),
                null);
        LuaEditorNode node = LuaEditorGraphAdapter.createEditorNode(
                program,
                definition.id(),
                10,
                20);
        node.setFieldValue("amount", org.luaj.vm2.LuaValue.valueOf(7.4));
        var graph = LuaEditorGraphAdapter.toEditorGraph(program);
        graph.addNode(node);

        ComputedProgramV3 restored = LuaEditorGraphAdapter.fromEditorGraph(graph, program, 1);
        double amount = new LuaStateCodec()
                .decode(restored.rootGraph().nodes().getFirst().fields().get("amount"))
                .todouble();
        LuaEditorNode duplicate = LuaEditorGraphAdapter.duplicateEditorNode(node, 40, 50);
        duplicate.setFieldValue("amount", org.luaj.vm2.LuaValue.valueOf(1));

        assertEquals(7.5, amount);
        assertEquals(7.5, node.fieldValue("amount").todouble());
        assertEquals(1, duplicate.fieldValue("amount").todouble());
    }

    @Test
    void conditionalFieldsResizeWithTheirControllingChoiceAndKeepHiddenValues() {
        String source = """
                local node = computed.node(1, "example:conditional", "Conditional")
                node:field("layout", "choice", {
                    default = "line",
                    choices = { "line", "manual" }
                })
                node:field("line", "number", {
                    default = 1,
                    visible_when = { field = "layout", equals = "line" }
                })
                node:field("x", "number", {
                    default = 0,
                    visible_when = { field = "layout", equals = "manual" }
                })
                node:field("y", "number", {
                    default = 0,
                    visible_when = { field = "layout", equals = "manual" }
                })
                node:on_run(function(ctx) end)
                return node
                """;
        var definition = dev.propulsionteam.computed.graph.LuaDefinitionSource.embedded(
                1,
                "example:conditional",
                source);
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(35), List.of(), List.of()),
                Map.of(definition.id(), definition),
                Map.of(),
                null);
        LuaEditorNode node = LuaEditorGraphAdapter.createEditorNode(program, definition.id(), 0, 0);
        int lineHeight = node.getHeight();
        node.setFieldValue("x", org.luaj.vm2.LuaValue.valueOf(24));

        node.setFieldValue("layout", org.luaj.vm2.LuaValue.valueOf("manual"));

        assertEquals(lineHeight + LuaNodeFieldControl.ROW_HEIGHT, node.getHeight());
        assertEquals(24, node.fieldValue("x").toint());

        node.setFieldValue("layout", org.luaj.vm2.LuaValue.valueOf("line"));

        assertEquals(lineHeight, node.getHeight());
        assertEquals(24, node.fieldValue("x").toint());
    }

    @Test
    void creationAddsTheDefinitionAndFirstInstanceAtomically() {
        String source = """
                local node = computed.node(1, "user:created", "Created")
                node:field("value", "number", { default = 4 })
                node:output("value", "number")
                node:on_run(function(ctx) ctx:output("value", ctx:field("value")) end)
                return node
                """;
        var definition = dev.propulsionteam.computed.graph.LuaDefinitionSource.embedded(
                1,
                "user:created",
                source);
        ComputedProgramV3 empty = ComputedProgramV3.empty(uuid(40));

        ComputedProgramV3 created =
                LuaEditorGraphAdapter.addDefinitionAndNode(empty, definition, 75, 90);

        assertEquals(definition, created.library().get("user:created"));
        assertEquals(1, created.rootGraph().nodes().size());
        assertEquals("user:created", created.rootGraph().nodes().getFirst().definitionId());
        assertEquals(75, created.rootGraph().nodes().getFirst().x());
        assertEquals(90, created.rootGraph().nodes().getFirst().y());
        assertNotNull(created.rootGraph().nodes().getFirst().fields().get("value"));
    }

    private static PortSnapshot port(String id, PortDirection direction) {
        return new PortSnapshot(id, direction, ConnectionType.NUMBER, id);
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
