package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.internal.node.api.WConnection;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LuaEditorGraphAdapter {
    private LuaEditorGraphAdapter() {}

    public static WGraph toEditorGraph(ComputedProgramV3 program) {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>(BundledLuaLibrary.load());
        definitions.putAll(program.library());
        Map<UUID, LuaEditorNode> nodes = new LinkedHashMap<>();
        WGraph graph = new WGraph();
        for (GraphNode node : program.rootGraph().nodes()) {
            LuaNodeDefinition definition = definition(definitions.get(node.definitionId()));
            String title = definition == null ? node.definitionId() : definition.title();
            boolean stateBoundary = definition != null && !definition.stateDefaults().isEmpty();
            LuaEditorNode editorNode = new LuaEditorNode(node, title, stateBoundary);
            nodes.put(node.id(), editorNode);
            graph.addNode(editorNode);
        }
        for (GraphConnection connection : program.rootGraph().connections()) {
            LuaEditorNode source = nodes.get(connection.sourceNode());
            LuaEditorNode target = nodes.get(connection.targetNode());
            if (source == null || target == null) {
                continue;
            }
            int sourcePin = stablePin(source, true, connection.sourcePort());
            int targetPin = stablePin(target, false, connection.targetPort());
            if (sourcePin < 0 || targetPin < 0) {
                continue;
            }
            int[] waypointXs = connection.waypoints().stream().mapToInt(point -> (int) Math.round(point.x())).toArray();
            int[] waypointYs = connection.waypoints().stream().mapToInt(point -> (int) Math.round(point.y())).toArray();
            graph.connect(new WConnection(
                    source.getId(),
                    sourcePin,
                    target.getId(),
                    targetPin,
                    waypointXs,
                    waypointYs,
                    connection.sourcePort(),
                    connection.targetPort()));
        }
        return graph;
    }

    public static ComputedProgramV3 fromEditorGraph(
            WGraph editor,
            ComputedProgramV3 base,
            long revision) {
        Map<UUID, GraphNode> baseNodes = new HashMap<>();
        base.rootGraph().nodes().forEach(node -> baseNodes.put(node.id(), node));
        List<GraphNode> nodes = new ArrayList<>();
        for (WNode editorNode : editor.getNodes()) {
            GraphNode original = baseNodes.get(editorNode.getId());
            if (original == null) {
                continue;
            }
            nodes.add(new GraphNode(
                    original.id(),
                    original.definitionId(),
                    original.definitionHash(),
                    editorNode.getX(),
                    editorNode.getY(),
                    original.ports(),
                    original.fields()));
        }
        Map<String, GraphConnection> oldConnections = new HashMap<>();
        base.rootGraph().connections().forEach(connection ->
                oldConnections.put(identity(connection), connection));
        List<GraphConnection> connections = new ArrayList<>();
        for (WConnection connection : editor.getConnections()) {
            String sourcePort = stablePort(editor, connection.sourceNode(), true, connection.sourcePin());
            String targetPort = stablePort(editor, connection.targetNode(), false, connection.targetPin());
            if (sourcePort == null || targetPort == null) {
                continue;
            }
            String identity = identity(
                    connection.sourceNode(),
                    sourcePort,
                    connection.targetNode(),
                    targetPort);
            GraphConnection previous = oldConnections.get(identity);
            List<GraphPoint> waypoints = new ArrayList<>();
            int[] xs = connection.waypointXs();
            int[] ys = connection.waypointYs();
            for (int index = 0; index < Math.min(xs.length, ys.length); index++) {
                waypoints.add(new GraphPoint(xs[index], ys[index]));
            }
            connections.add(new GraphConnection(
                    previous == null ? UUID.randomUUID() : previous.id(),
                    connection.sourceNode(),
                    sourcePort,
                    connection.targetNode(),
                    targetPort,
                    waypoints));
        }
        return new ComputedProgramV3(
                revision,
                new ComputedGraph(base.rootGraph().id(), nodes, connections),
                base.library(),
                base.persistentState(),
                base.metadata());
    }

    private static LuaNodeDefinition definition(LuaDefinitionSource source) {
        if (source == null) {
            return null;
        }
        try {
            var compiled = new LuaSourceCompiler().compile(source.apiVersion(), source.source());
            return new LuaDefinitionLoader().load(compiled, new LuaSandbox());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int stablePin(WNode node, boolean output, String key) {
        var pins = output ? node.getOutputs() : node.getInputs();
        for (int index = 0; index < pins.size(); index++) {
            if (key.equals(pins.get(index).getStableKey())) {
                return index;
            }
        }
        return -1;
    }

    private static String stablePort(WGraph graph, UUID nodeId, boolean output, int index) {
        WNode node = graph.getNode(nodeId);
        if (node == null) {
            return null;
        }
        var pins = output ? node.getOutputs() : node.getInputs();
        if (index < 0 || index >= pins.size()) {
            return null;
        }
        return pins.get(index).getStableKey();
    }

    private static String identity(GraphConnection connection) {
        return identity(
                connection.sourceNode(),
                connection.sourcePort(),
                connection.targetNode(),
                connection.targetPort());
    }

    private static String identity(UUID source, String sourcePort, UUID target, String targetPort) {
        return source + "\u0000" + sourcePort + "\u0000" + target + "\u0000" + targetPort;
    }
}
