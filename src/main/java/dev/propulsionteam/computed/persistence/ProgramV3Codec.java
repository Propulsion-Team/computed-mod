package dev.propulsionteam.computed.persistence;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ProgramV3Codec {
    private static final String FORMAT_VERSION = "formatVersion";

    private ProgramV3Codec() {}

    public static CompoundTag encode(ComputedProgramV3 program) {
        Objects.requireNonNull(program, "program");
        CompoundTag root = new CompoundTag();
        root.putInt(FORMAT_VERSION, ComputedProgramV3.FORMAT_VERSION);
        root.putLong("revision", program.revision());
        root.put("graph", encodeGraph(program.rootGraph()));
        ListTag library = new ListTag();
        program.library().values().stream()
                .sorted(java.util.Comparator.comparing(LuaDefinitionSource::id))
                .map(ProgramV3Codec::encodeDefinition)
                .forEach(library::add);
        root.put("library", library);
        ListTag states = new ListTag();
        program.persistentState().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag state = new CompoundTag();
                    state.putUUID("node", entry.getKey());
                    state.put("value", entry.getValue());
                    states.add(state);
                });
        root.put("states", states);
        root.put("metadata", program.metadata());
        return root;
    }

    public static LoadResult decode(
            CompoundTag source,
            String computerPosition,
            Consumer<String> warningSink) {
        Objects.requireNonNull(source, "source");
        Consumer<String> warnings = warningSink == null ? ignored -> {} : warningSink;
        CompoundTag root = source.contains("ComputedProgram", Tag.TAG_COMPOUND)
                ? source.getCompound("ComputedProgram")
                : source;
        if (!root.contains(FORMAT_VERSION)) {
            return resetLegacy(root, 0, computerPosition, warnings);
        }
        int version = root.getInt(FORMAT_VERSION);
        if (version < ComputedProgramV3.FORMAT_VERSION) {
            return resetLegacy(root, version, computerPosition, warnings);
        }
        if (version > ComputedProgramV3.FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported Computed program format version: " + version);
        }
        return new LoadResult(decodeV3(root), false, version);
    }

    public static ComputedProgramV3 decodeV3(CompoundTag root) {
        ComputedGraph graph = decodeGraph(root.getCompound("graph"));
        Map<String, LuaDefinitionSource> library = new LinkedHashMap<>();
        ListTag definitions = root.getList("library", Tag.TAG_COMPOUND);
        for (int index = 0; index < definitions.size(); index++) {
            LuaDefinitionSource definition = decodeDefinition(definitions.getCompound(index));
            if (library.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate Lua definition id: " + definition.id());
            }
        }
        Map<UUID, CompoundTag> state = new LinkedHashMap<>();
        ListTag states = root.getList("states", Tag.TAG_COMPOUND);
        for (int index = 0; index < states.size(); index++) {
            CompoundTag encoded = states.getCompound(index);
            UUID nodeId = encoded.getUUID("node");
            if (state.putIfAbsent(nodeId, encoded.getCompound("value").copy()) != null) {
                throw new IllegalArgumentException("Duplicate persistent state for node " + nodeId);
            }
        }
        return new ComputedProgramV3(
                root.getLong("revision"),
                graph,
                library,
                state,
                root.getCompound("metadata"));
    }

    private static LoadResult resetLegacy(
            CompoundTag source,
            int version,
            String computerPosition,
            Consumer<String> warnings) {
        String position = computerPosition == null || computerPosition.isBlank()
                ? "unknown position"
                : computerPosition;
        warnings.accept("Discarded legacy Computed program format "
                + version
                + " at "
                + position
                + "; initialized an empty format-3 program");
        UUID graphId = UUID.nameUUIDFromBytes(
                ("computed:empty:" + position).getBytes(StandardCharsets.UTF_8));
        return new LoadResult(ComputedProgramV3.empty(graphId), true, version);
    }

    private static CompoundTag encodeGraph(ComputedGraph graph) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", graph.id());
        ListTag nodes = new ListTag();
        graph.nodes().forEach(node -> nodes.add(encodeNode(node)));
        tag.put("nodes", nodes);
        ListTag connections = new ListTag();
        graph.connections().forEach(connection -> connections.add(encodeConnection(connection)));
        tag.put("connections", connections);
        return tag;
    }

    private static ComputedGraph decodeGraph(CompoundTag tag) {
        UUID graphId = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        List<GraphNode> nodes = new ArrayList<>();
        ListTag encodedNodes = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedNodes.size(); index++) {
            nodes.add(decodeNode(encodedNodes.getCompound(index)));
        }
        List<GraphConnection> connections = new ArrayList<>();
        ListTag encodedConnections = tag.getList("connections", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedConnections.size(); index++) {
            connections.add(decodeConnection(encodedConnections.getCompound(index)));
        }
        return new ComputedGraph(graphId, nodes, connections);
    }

    private static CompoundTag encodeNode(GraphNode node) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", node.id());
        tag.putString("definition", node.definitionId());
        tag.putString("hash", node.definitionHash());
        tag.putInt("x", node.x());
        tag.putInt("y", node.y());
        ListTag ports = new ListTag();
        node.ports().forEach(port -> {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("id", port.id());
            encoded.putString("direction", port.direction().name());
            encoded.putString("type", port.type().name());
            encoded.putString("label", port.label());
            ports.add(encoded);
        });
        tag.put("ports", ports);
        CompoundTag fields = new CompoundTag();
        node.fields().forEach(fields::put);
        tag.put("fields", fields);
        return tag;
    }

    private static GraphNode decodeNode(CompoundTag tag) {
        List<PortSnapshot> ports = new ArrayList<>();
        ListTag encodedPorts = tag.getList("ports", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedPorts.size(); index++) {
            CompoundTag port = encodedPorts.getCompound(index);
            ports.add(new PortSnapshot(
                    port.getString("id"),
                    PortDirection.valueOf(port.getString("direction")),
                    ConnectionType.valueOf(port.getString("type")),
                    port.getString("label")));
        }
        Map<String, CompoundTag> fields = new LinkedHashMap<>();
        CompoundTag encodedFields = tag.getCompound("fields");
        for (String id : encodedFields.getAllKeys()) {
            Tag value = encodedFields.get(id);
            if (value instanceof CompoundTag compound) {
                fields.put(id, compound.copy());
            }
        }
        return new GraphNode(
                tag.getUUID("id"),
                tag.getString("definition"),
                tag.getString("hash"),
                tag.getInt("x"),
                tag.getInt("y"),
                ports,
                fields);
    }

    private static CompoundTag encodeConnection(GraphConnection connection) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", connection.id());
        tag.putUUID("sourceNode", connection.sourceNode());
        tag.putString("sourcePort", connection.sourcePort());
        tag.putUUID("targetNode", connection.targetNode());
        tag.putString("targetPort", connection.targetPort());
        ListTag waypoints = new ListTag();
        connection.waypoints().forEach(point -> {
            CompoundTag encoded = new CompoundTag();
            encoded.putDouble("x", point.x());
            encoded.putDouble("y", point.y());
            waypoints.add(encoded);
        });
        tag.put("waypoints", waypoints);
        return tag;
    }

    private static GraphConnection decodeConnection(CompoundTag tag) {
        List<GraphPoint> waypoints = new ArrayList<>();
        ListTag encodedWaypoints = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedWaypoints.size(); index++) {
            CompoundTag point = encodedWaypoints.getCompound(index);
            waypoints.add(new GraphPoint(point.getDouble("x"), point.getDouble("y")));
        }
        return new GraphConnection(
                tag.getUUID("id"),
                tag.getUUID("sourceNode"),
                tag.getString("sourcePort"),
                tag.getUUID("targetNode"),
                tag.getString("targetPort"),
                waypoints);
    }

    private static CompoundTag encodeDefinition(LuaDefinitionSource definition) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("apiVersion", definition.apiVersion());
        tag.putString("id", definition.id());
        tag.putString("source", definition.source());
        tag.putString("hash", definition.hash());
        tag.putString("origin", definition.origin().name());
        return tag;
    }

    private static LuaDefinitionSource decodeDefinition(CompoundTag tag) {
        return new LuaDefinitionSource(
                tag.getInt("apiVersion"),
                tag.getString("id"),
                tag.getString("source"),
                tag.getString("hash"),
                LuaDefinitionSource.Origin.valueOf(tag.getString("origin")));
    }

    public record LoadResult(ComputedProgramV3 program, boolean discardedLegacy, int sourceVersion) {
        public LoadResult {
            Objects.requireNonNull(program, "program");
        }
    }
}
