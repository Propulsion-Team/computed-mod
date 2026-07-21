package dev.propulsionteam.computed.node.program;

import dev.propulsionteam.computed.node.program.ConnectionModel.Waypoint;
import dev.propulsionteam.computed.node.program.NodeModel.PlaceholderStatus;
import dev.propulsionteam.computed.node.program.PortModel.Direction;
import dev.propulsionteam.computed.node.program.ProgramDiagnostic.Severity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** NBT codec for {@link ComputedProgram}, including one-way migration from the legacy graph format. */
public final class ProgramCodec {
    private static final String FORMAT_VERSION = "formatVersion";
    private static final Predicate<String> ASSUME_TYPES_AVAILABLE = ignored -> true;

    private ProgramCodec() {}

    /** Describes whether a read was a native v2 decode or a legacy migration. */
    public record DecodeResult(ComputedProgram program, boolean migrated, int sourceVersion) {
        public DecodeResult {
            Objects.requireNonNull(program, "program");
        }
    }

    /** Result of adapting a v2 program to the positional format consumed by the transitional WGraph runtime. */
    public record LegacyBundleResult(CompoundTag bundle, List<ProgramDiagnostic> diagnostics) {
        public LegacyBundleResult {
            bundle = bundle == null ? new CompoundTag() : bundle.copy();
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        @Override
        public CompoundTag bundle() {
            return bundle.copy();
        }
    }

    public static CompoundTag write(ComputedProgram program) {
        return encode(program);
    }

    public static CompoundTag encode(ComputedProgram program) {
        Objects.requireNonNull(program, "program");
        CompoundTag root = new CompoundTag();
        root.putInt(FORMAT_VERSION, ComputedProgram.FORMAT_VERSION);
        root.putLong("revision", program.revision());
        root.put("graph", encodeGraph(program.rootGraph()));

        ListTag functions = new ListTag();
        for (FunctionModel function : program.functions()) {
            functions.add(encodeFunction(function));
        }
        root.put("functions", functions);

        ListTag diagnostics = new ListTag();
        for (ProgramDiagnostic diagnostic : program.diagnostics()) {
            diagnostics.add(encodeDiagnostic(diagnostic));
        }
        root.put("diagnostics", diagnostics);
        root.put("metadata", program.metadata());
        return root;
    }

    /**
     * Reconstructs {@code ComputerGraph}/{@code ComputerFunctions} for the transitional positional
     * runtime. Connections using non-legacy stable port ids are skipped and reported because guessing
     * an integer position could execute the wrong side effect.
     */
    public static LegacyBundleResult toLegacyBundle(ComputedProgram program) {
        Objects.requireNonNull(program, "program");
        List<ProgramDiagnostic> diagnostics = new ArrayList<>();
        CompoundTag bundle = new CompoundTag();
        bundle.put("ComputerGraph", toLegacyGraph(program.rootGraph(), diagnostics));

        ListTag functions = new ListTag();
        for (FunctionModel function : program.functions()) {
            CompoundTag tag = function.rawTag();
            tag.putUUID("Id", function.id());
            tag.putString("Name", function.name());
            tag.put("Body", toLegacyGraph(function.graph(), diagnostics));
            functions.add(tag);
        }
        bundle.put("ComputerFunctions", functions);
        return new LegacyBundleResult(bundle, diagnostics);
    }

    /** Convenience for callers that have already surfaced or intentionally ignore bridge diagnostics. */
    public static CompoundTag toLegacyBundleTag(ComputedProgram program) {
        return toLegacyBundle(program).bundle();
    }

    public static ComputedProgram read(CompoundTag source) {
        return decode(source).program();
    }

    public static ComputedProgram read(CompoundTag source, Predicate<String> knownNodeType) {
        return decode(source, knownNodeType).program();
    }

    public static DecodeResult decode(CompoundTag source) {
        return decode(source, ASSUME_TYPES_AVAILABLE);
    }

    /**
     * Reads a program while using {@code knownNodeType} to identify recoverable missing-addon
     * placeholders. Passing a registry lookup here allows placeholders to become resolved again as
     * soon as their addon returns.
     */
    public static DecodeResult decode(CompoundTag source, Predicate<String> knownNodeType) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(knownNodeType, "knownNodeType");

        CompoundTag root = source;
        if (source.contains("ComputedProgram", Tag.TAG_COMPOUND)) {
            root = source.getCompound("ComputedProgram");
        }

        if (root.contains(FORMAT_VERSION)) {
            int version = root.getInt(FORMAT_VERSION);
            if (version == ComputedProgram.FORMAT_VERSION) {
                return new DecodeResult(decodeV2(root, knownNodeType), false, version);
            }
            if (version > ComputedProgram.FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Computed program format version: " + version);
            }
        }
        return migrateLegacy(root, knownNodeType);
    }

    private static ComputedProgram decodeV2(CompoundTag root, Predicate<String> knownNodeType) {
        List<ProgramDiagnostic> diagnostics = decodeDiagnostics(root.getList("diagnostics", Tag.TAG_COMPOUND));
        GraphModel graph = decodeV2Graph(root.getCompound("graph"), "root", knownNodeType, diagnostics);

        List<FunctionModel> functions = new ArrayList<>();
        ListTag functionTags = root.getList("functions", Tag.TAG_COMPOUND);
        Set<UUID> functionIds = new HashSet<>();
        for (int i = 0; i < functionTags.size(); i++) {
            CompoundTag tag = functionTags.getCompound(i);
            UUID requestedId = readUuid(tag, "id", stableUuid("v2/function/" + i));
            UUID id = uniqueUuid(requestedId, functionIds, "v2/function/" + i);
            if (!id.equals(requestedId)) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_function_id",
                        "Duplicate function id was replaced with a deterministic id",
                        null,
                        null,
                        null));
            }
            GraphModel body = decodeV2Graph(tag.getCompound("graph"), "function/" + id, knownNodeType, diagnostics);
            functions.add(new FunctionModel(
                    id,
                    tag.getString("name"),
                    body,
                    copyCompound(tag, "metadata"),
                    copyCompound(tag, "raw")));
        }

        long revision = root.contains("revision") ? Math.max(0L, root.getLong("revision")) : 0L;
        return new ComputedProgram(revision, graph, functions, diagnostics, copyCompound(root, "metadata"));
    }

    private static GraphModel decodeV2Graph(
            CompoundTag tag,
            String path,
            Predicate<String> knownNodeType,
            List<ProgramDiagnostic> diagnostics) {
        UUID graphId = readUuid(tag, "id", stableUuid("v2/graph/" + path));
        List<NodeModel> nodes = new ArrayList<>();
        ListTag nodeTags = tag.getList("nodes", Tag.TAG_COMPOUND);
        Set<UUID> nodeIds = new HashSet<>();
        for (int i = 0; i < nodeTags.size(); i++) {
            CompoundTag nodeTag = nodeTags.getCompound(i);
            UUID requestedId = readUuid(nodeTag, "id", stableUuid(path + "/node/" + i));
            UUID id = uniqueUuid(requestedId, nodeIds, path + "/node/" + i);
            if (!id.equals(requestedId)) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_node_id",
                        "Duplicate node id was replaced with a deterministic id",
                        graphId,
                        id,
                        null));
            }

            String originalType = nodeTag.contains("originalType", Tag.TAG_STRING)
                    ? nodeTag.getString("originalType")
                    : nodeTag.getString("type");
            String canonicalType = canonicalType(nodeTag.getString("type"));
            PlaceholderStatus status = placeholderStatus(canonicalType, originalType, knownNodeType);
            if (status != PlaceholderStatus.RESOLVED) {
                diagnostics.add(missingTypeDiagnostic(graphId, id, originalType, status));
            }

            List<PortModel> ports = decodeV2Ports(nodeTag.getList("ports", Tag.TAG_COMPOUND), graphId, id, diagnostics);
            nodes.add(new NodeModel(
                    id,
                    canonicalType,
                    originalType,
                    nodeTag.getString("title"),
                    nodeTag.getInt("x"),
                    nodeTag.getInt("y"),
                    copyCompound(nodeTag, "properties"),
                    copyCompound(nodeTag, "state"),
                    ports,
                    status,
                    copyCompound(nodeTag, "raw")));
        }

        List<ConnectionModel> connections = new ArrayList<>();
        ListTag connectionTags = tag.getList("connections", Tag.TAG_COMPOUND);
        Set<UUID> connectionIds = new HashSet<>();
        for (int i = 0; i < connectionTags.size(); i++) {
            CompoundTag connectionTag = connectionTags.getCompound(i);
            UUID requestedId = readUuid(connectionTag, "id", stableUuid(path + "/connection/" + i));
            UUID id = uniqueUuid(requestedId, connectionIds, path + "/connection/" + i);
            UUID source = readUuid(
                    connectionTag, "sourceNode", stableUuid(path + "/connection/" + i + "/missing-source"));
            UUID target = readUuid(
                    connectionTag, "targetNode", stableUuid(path + "/connection/" + i + "/missing-target"));
            PortId sourcePort = readPortId(
                    connectionTag.getString("sourcePort"), PortId.legacyOutput(0), graphId, id, diagnostics);
            PortId targetPort = readPortId(
                    connectionTag.getString("targetPort"), PortId.legacyInput(0), graphId, id, diagnostics);
            connections.add(new ConnectionModel(
                    id,
                    source,
                    sourcePort,
                    target,
                    targetPort,
                    decodeWaypoints(connectionTag.getList("waypoints", Tag.TAG_COMPOUND)),
                    copyCompound(connectionTag, "raw")));
        }

        List<SectionModel> sections = new ArrayList<>();
        ListTag sectionTags = tag.getList("sections", Tag.TAG_COMPOUND);
        Set<UUID> sectionIds = new HashSet<>();
        for (int i = 0; i < sectionTags.size(); i++) {
            CompoundTag sectionTag = sectionTags.getCompound(i);
            UUID id = uniqueUuid(
                    readUuid(sectionTag, "id", stableUuid(path + "/section/" + i)),
                    sectionIds,
                    path + "/section/" + i);
            sections.add(decodeSection(sectionTag, id, "raw"));
        }
        return new GraphModel(
                graphId,
                nodes,
                connections,
                sections,
                copyCompound(tag, "metadata"),
                copyCompound(tag, "raw"));
    }

    private static List<PortModel> decodeV2Ports(
            ListTag portTags,
            UUID graphId,
            UUID nodeId,
            List<ProgramDiagnostic> diagnostics) {
        List<PortModel> ports = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (int i = 0; i < portTags.size(); i++) {
            CompoundTag portTag = portTags.getCompound(i);
            Direction direction = parseEnum(Direction.class, portTag.getString("direction"), Direction.INPUT);
            PortId fallback = direction == Direction.INPUT ? PortId.legacyInput(i) : PortId.legacyOutput(i);
            PortId id = readPortId(portTag.getString("id"), fallback, graphId, null, diagnostics);
            String identity = direction + "\u0000" + id.value();
            if (!identities.add(identity)) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_port_id",
                        "Node contains duplicate " + direction.name().toLowerCase() + " port id " + id,
                        graphId,
                        nodeId,
                        null));
            }
            ports.add(new PortModel(
                    id,
                    direction,
                    portTag.getString("valueType"),
                    portTag.getString("label"),
                    copyCompound(portTag, "data")));
        }
        return ports;
    }

    private static DecodeResult migrateLegacy(CompoundTag root, Predicate<String> knownNodeType) {
        List<ProgramDiagnostic> diagnostics = new ArrayList<>();
        CompoundTag graphTag;
        ListTag functions;
        Set<String> consumedRootKeys = new HashSet<>();

        if (root.contains("ComputerGraph", Tag.TAG_COMPOUND)) {
            graphTag = root.getCompound("ComputerGraph");
            functions = root.getList("ComputerFunctions", Tag.TAG_COMPOUND);
            consumedRootKeys.add("ComputerGraph");
            consumedRootKeys.add("ComputerFunctions");
        } else if (root.contains("graph", Tag.TAG_COMPOUND)) {
            graphTag = root.getCompound("graph");
            functions = root.getList("functions", Tag.TAG_COMPOUND);
            consumedRootKeys.add("graph");
            consumedRootKeys.add("functions");
            consumedRootKeys.add("embeddedCustomNodes");
        } else if (root.contains("nodes", Tag.TAG_LIST)) {
            graphTag = root;
            functions = new ListTag();
            consumedRootKeys.addAll(root.getAllKeys());
        } else {
            throw new IllegalArgumentException("NBT does not contain a Computed program or legacy graph");
        }
        consumedRootKeys.add(FORMAT_VERSION);
        consumedRootKeys.add("revision");

        GraphModel graph = migrateLegacyGraph(graphTag, "root", knownNodeType, diagnostics);
        List<FunctionModel> migratedFunctions = migrateLegacyFunctions(functions, knownNodeType, diagnostics);

        CompoundTag metadata = new CompoundTag();
        CompoundTag extras = copyExcept(root, consumedRootKeys);
        if (!extras.isEmpty()) {
            metadata.put("legacyRootExtras", extras);
        }
        metadata.putBoolean("migratedFromLegacy", true);
        diagnostics.add(new ProgramDiagnostic(
                Severity.INFO,
                "legacy_program_migrated",
                "Legacy graph was migrated to Computed program format 2",
                graph.id(),
                null,
                null));

        int sourceVersion = root.contains(FORMAT_VERSION) ? root.getInt(FORMAT_VERSION) : 0;
        long revision = root.contains("revision") ? Math.max(0L, root.getLong("revision")) : 0L;
        return new DecodeResult(
                new ComputedProgram(revision, graph, migratedFunctions, diagnostics, metadata),
                true,
                sourceVersion);
    }

    private static GraphModel migrateLegacyGraph(
            CompoundTag legacy,
            String path,
            Predicate<String> knownNodeType,
            List<ProgramDiagnostic> diagnostics) {
        UUID graphId = readUuid(legacy, "id", stableUuid("legacy/graph/" + path));
        List<NodeModel> nodes = new ArrayList<>();
        Set<UUID> usedNodeIds = new HashSet<>();
        Map<String, UUID> nodeAliases = new HashMap<>();
        ListTag nodeTags = legacy.getList("nodes", Tag.TAG_COMPOUND);

        for (int i = 0; i < nodeTags.size(); i++) {
            CompoundTag raw = nodeTags.getCompound(i).copy();
            String rawId = rawUuidText(raw, "id");
            UUID requestedId = parseUuid(rawId, stableUuid(path + "/legacy-node/" + i));
            UUID id = uniqueUuid(requestedId, usedNodeIds, path + "/legacy-node/" + i);
            if (!id.equals(requestedId)) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_node_id",
                        "Legacy duplicate node id was replaced with a deterministic id",
                        graphId,
                        id,
                        null));
            }
            if (!rawId.isBlank()) {
                nodeAliases.putIfAbsent(rawId, id);
            }
            nodeAliases.putIfAbsent(id.toString(), id);

            String originalType = firstString(raw, "typeId", "type");
            String canonicalType = canonicalType(originalType);
            PlaceholderStatus status = placeholderStatus(canonicalType, originalType, knownNodeType);
            if (status != PlaceholderStatus.RESOLVED) {
                diagnostics.add(missingTypeDiagnostic(graphId, id, originalType, status));
            } else if (!canonicalType.equals(originalType)) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.INFO,
                        "legacy_type_renamed",
                        "Migrated node type " + originalType + " to " + canonicalType,
                        graphId,
                        id,
                        null));
            }

            CompoundTag properties = extractLegacyProperties(raw);
            CompoundTag state = extractLegacyState(raw);
            if (raw.contains("inner", Tag.TAG_COMPOUND)) {
                GraphModel inner = migrateLegacyGraph(
                        raw.getCompound("inner"), path + "/node/" + id + "/inner", knownNodeType, diagnostics);
                state.put("innerGraph", encodeGraph(inner));
            }

            List<PortModel> ports = new ArrayList<>();
            migrateLegacyPorts(raw.getList("inputs", Tag.TAG_COMPOUND), Direction.INPUT, ports);
            migrateLegacyPorts(raw.getList("outputs", Tag.TAG_COMPOUND), Direction.OUTPUT, ports);
            nodes.add(new NodeModel(
                    id,
                    canonicalType,
                    originalType,
                    raw.getString("title"),
                    raw.getInt("x"),
                    raw.getInt("y"),
                    properties,
                    state,
                    ports,
                    status,
                    raw));
        }

        List<ConnectionModel> connections = new ArrayList<>();
        ListTag connectionTags = legacy.contains("conns", Tag.TAG_LIST)
                ? legacy.getList("conns", Tag.TAG_COMPOUND)
                : legacy.getList("connections", Tag.TAG_COMPOUND);
        Set<UUID> usedConnectionIds = new HashSet<>();
        for (int i = 0; i < connectionTags.size(); i++) {
            CompoundTag raw = connectionTags.getCompound(i).copy();
            UUID id = uniqueUuid(
                    readUuid(raw, "id", stableUuid(path + "/legacy-connection/" + i)),
                    usedConnectionIds,
                    path + "/legacy-connection/" + i);
            String rawSource = firstUuidText(raw, "src", "sourceNode");
            String rawTarget = firstUuidText(raw, "tgt", "targetNode");
            UUID source = resolveLegacyEndpoint(rawSource, nodeAliases, path + "/connection/" + i + "/source");
            UUID target = resolveLegacyEndpoint(rawTarget, nodeAliases, path + "/connection/" + i + "/target");
            if (!containsNode(nodes, source) || !containsNode(nodes, target)) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "dangling_connection",
                        "Legacy connection references a missing node; it was retained for repair",
                        graphId,
                        null,
                        id));
            }

            NodeModel sourceNode = findNode(nodes, source);
            NodeModel targetNode = findNode(nodes, target);
            PortId sourcePort = legacyPortId(raw, true, sourceNode);
            PortId targetPort = legacyPortId(raw, false, targetNode);
            ListTag waypointTags = raw.contains("wps", Tag.TAG_LIST)
                    ? raw.getList("wps", Tag.TAG_COMPOUND)
                    : raw.getList("waypoints", Tag.TAG_COMPOUND);
            connections.add(new ConnectionModel(
                    id,
                    source,
                    sourcePort,
                    target,
                    targetPort,
                    decodeWaypoints(waypointTags),
                    raw));
        }

        List<SectionModel> sections = new ArrayList<>();
        ListTag sectionTags = legacy.getList("sections", Tag.TAG_COMPOUND);
        Set<UUID> usedSectionIds = new HashSet<>();
        for (int i = 0; i < sectionTags.size(); i++) {
            CompoundTag raw = sectionTags.getCompound(i).copy();
            UUID id = uniqueUuid(
                    readUuid(raw, "id", stableUuid(path + "/legacy-section/" + i)),
                    usedSectionIds,
                    path + "/legacy-section/" + i);
            sections.add(decodeLegacySection(raw, id));
        }

        CompoundTag metadata = copyExcept(legacy, Set.of("id", "nodes", "conns", "connections", "sections"));
        return new GraphModel(graphId, nodes, connections, sections, metadata, legacy.copy());
    }

    private static List<FunctionModel> migrateLegacyFunctions(
            ListTag functionTags,
            Predicate<String> knownNodeType,
            List<ProgramDiagnostic> diagnostics) {
        List<FunctionModel> functions = new ArrayList<>();
        Set<UUID> usedIds = new HashSet<>();
        for (int i = 0; i < functionTags.size(); i++) {
            CompoundTag raw = functionTags.getCompound(i).copy();
            String rawId = firstUuidText(raw, "Id", "id");
            UUID requestedId = parseUuid(rawId, stableUuid("legacy/function/" + i));
            UUID id = uniqueUuid(requestedId, usedIds, "legacy/function/" + i);
            String name = firstString(raw, "Name", "name");
            CompoundTag body = firstCompound(raw, "Body", "body", "graph");
            GraphModel graph = migrateLegacyGraph(body, "function/" + id, knownNodeType, diagnostics);
            CompoundTag metadata = copyExcept(raw, Set.of("Id", "id", "Name", "name", "Body", "body", "graph"));
            functions.add(new FunctionModel(id, name, graph, metadata, raw));
        }
        return functions;
    }

    private static CompoundTag toLegacyGraph(GraphModel graph, List<ProgramDiagnostic> diagnostics) {
        CompoundTag legacy = graph.rawTag();
        ListTag nodes = new ListTag();
        Map<UUID, NodeModel> nodeIndex = new HashMap<>();
        for (NodeModel node : graph.nodes()) {
            nodeIndex.putIfAbsent(node.id(), node);
            CompoundTag tag = node.rawTag();
            tag.putString("typeId", node.typeId());
            tag.putString("id", node.id().toString());
            tag.putString("title", node.title());
            tag.putInt("x", node.x());
            tag.putInt("y", node.y());

            tag.put("inputs", toLegacyPortList(graph, node, Direction.INPUT, diagnostics));
            tag.put("outputs", toLegacyPortList(graph, node, Direction.OUTPUT, diagnostics));

            CompoundTag properties = node.properties();
            if (properties.contains("legacyElements", Tag.TAG_LIST)) {
                tag.put("elements", properties.getList("legacyElements", Tag.TAG_COMPOUND).copy());
            } else if (properties.contains("elements", Tag.TAG_LIST)) {
                tag.put("elements", properties.getList("elements", Tag.TAG_COMPOUND).copy());
            }
            if (!properties.isEmpty()) {
                tag.put("computedPropertiesV2", properties.copy());
            }

            CompoundTag state = node.state();
            Set<String> structuralKeys = Set.of(
                    "typeId", "type", "id", "title", "x", "y", "inputs", "outputs", "elements", "properties");
            for (String key : state.getAllKeys()) {
                if (!structuralKeys.contains(key) && !"innerGraph".equals(key)) {
                    Tag value = state.get(key);
                    if (value != null) tag.put(key, value.copy());
                }
            }
            if (!state.isEmpty()) {
                tag.put("computedStateV2", state.copy());
            }
            if (state.contains("innerGraph", Tag.TAG_COMPOUND)) {
                try {
                    GraphModel inner = decodeV2Graph(
                            state.getCompound("innerGraph"),
                            "legacy-bridge/node/" + node.id() + "/inner",
                            ASSUME_TYPES_AVAILABLE,
                            diagnostics);
                    tag.put("inner", toLegacyGraph(inner, diagnostics));
                } catch (RuntimeException exception) {
                    diagnostics.add(new ProgramDiagnostic(
                            Severity.ERROR,
                            "legacy_inner_graph_unrepresentable",
                            "Nested function graph could not be adapted to the transitional runtime: "
                                    + exception.getMessage(),
                            graph.id(),
                            node.id(),
                            null));
                }
            }
            if (node.isPlaceholder()) {
                tag.putBoolean("ComputedMissingType", true);
                diagnostics.add(new ProgramDiagnostic(
                        Severity.WARNING,
                        "legacy_placeholder_unavailable",
                        "The transitional runtime cannot instantiate missing node type " + node.originalTypeId()
                                + "; its raw tag remains in the bundle",
                        graph.id(),
                        node.id(),
                        null));
            }
            nodes.add(tag);
        }
        legacy.put("nodes", nodes);

        ListTag connections = new ListTag();
        for (ConnectionModel connection : graph.connections()) {
            NodeModel sourceNode = nodeIndex.get(connection.sourceNode());
            NodeModel targetNode = nodeIndex.get(connection.targetNode());
            Integer sourceIndex = legacyPortIndex(sourceNode, connection.sourcePort(), Direction.OUTPUT);
            Integer targetIndex = legacyPortIndex(targetNode, connection.targetPort(), Direction.INPUT);
            boolean validEndpoint = sourceNode != null
                    && targetNode != null
                    && sourceIndex != null
                    && targetIndex != null
                    && sourceNode.port(connection.sourcePort(), Direction.OUTPUT).isPresent()
                    && targetNode.port(connection.targetPort(), Direction.INPUT).isPresent();
            if (!validEndpoint) {
                CompoundTag details = new CompoundTag();
                details.putString("sourcePort", connection.sourcePort().value());
                details.putString("targetPort", connection.targetPort().value());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "legacy_connection_unrepresentable",
                        "Connection was skipped because the positional runtime cannot safely address its stable ports",
                        graph.id(),
                        null,
                        connection.id(),
                        details));
                continue;
            }

            CompoundTag tag = connection.rawTag();
            tag.putString("src", connection.sourceNode().toString());
            tag.putInt("srcP", sourceIndex);
            tag.putString("sourcePort", connection.sourcePort().value());
            tag.putString("tgt", connection.targetNode().toString());
            tag.putInt("tgtP", targetIndex);
            tag.putString("targetPort", connection.targetPort().value());
            if (connection.waypoints().isEmpty()) {
                tag.remove("wps");
            } else {
                ListTag waypoints = new ListTag();
                for (Waypoint waypoint : connection.waypoints()) {
                    CompoundTag point = new CompoundTag();
                    point.putInt("x", roundedInt(waypoint.x()));
                    point.putInt("y", roundedInt(waypoint.y()));
                    waypoints.add(point);
                }
                tag.put("wps", waypoints);
            }
            connections.add(tag);
        }
        legacy.put("conns", connections);
        legacy.remove("connections");

        ListTag sections = new ListTag();
        for (SectionModel section : graph.sections()) {
            CompoundTag tag = section.rawTag();
            tag.putString("id", section.id().toString());
            tag.putString("name", section.name());
            tag.putInt("x", section.x());
            tag.putInt("y", section.y());
            tag.putInt("w", section.width());
            tag.putInt("h", section.height());
            tag.putInt("bodyArgb", section.bodyColorArgb());
            tag.putInt("layer", section.layer());
            sections.add(tag);
        }
        legacy.put("sections", sections);
        return legacy;
    }

    private static ListTag toLegacyPortList(
            GraphModel graph,
            NodeModel node,
            Direction direction,
            List<ProgramDiagnostic> diagnostics) {
        ListTag result = new ListTag();
        Set<String> seen = new HashSet<>();
        for (PortModel port : node.ports()) {
            if (port.direction() != direction) continue;
            if (!seen.add(port.id().value())) {
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "legacy_port_key_collision",
                        "Multiple ports share stable id " + port.id() + " and cannot be addressed safely",
                        graph.id(),
                        node.id(),
                        null));
                continue;
            }
            CompoundTag tag = port.data();
            tag.putString("portKey", port.id().value());
            tag.putString("name", port.label());
            if (!tag.contains("dataType", Tag.TAG_STRING)) {
                tag.putString("dataType", port.valueType());
            }
            result.add(tag);
        }
        return result;
    }

    private static Integer legacyPortIndex(NodeModel node, PortId portId, Direction direction) {
        if (node == null) return null;
        int index = 0;
        for (PortModel port : node.ports()) {
            if (port.direction() != direction) continue;
            if (port.id().equals(portId)) return index;
            index++;
        }
        return null;
    }

    private static int roundedInt(double value) {
        if (!Double.isFinite(value)) return 0;
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(value);
    }

    private static CompoundTag encodeFunction(FunctionModel function) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", function.id());
        tag.putString("name", function.name());
        tag.put("graph", encodeGraph(function.graph()));
        tag.put("metadata", function.metadata());
        tag.put("raw", function.rawTag());
        return tag;
    }

    private static CompoundTag encodeGraph(GraphModel graph) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", graph.id());

        ListTag nodes = new ListTag();
        for (NodeModel node : graph.nodes()) {
            CompoundTag nodeTag = new CompoundTag();
            nodeTag.putUUID("id", node.id());
            nodeTag.putString("type", node.typeId());
            nodeTag.putString("originalType", node.originalTypeId());
            nodeTag.putString("title", node.title());
            nodeTag.putInt("x", node.x());
            nodeTag.putInt("y", node.y());
            nodeTag.putString("placeholder", node.placeholderStatus().name());
            nodeTag.put("properties", node.properties());
            nodeTag.put("state", node.state());
            nodeTag.put("raw", node.rawTag());

            ListTag ports = new ListTag();
            for (PortModel port : node.ports()) {
                CompoundTag portTag = new CompoundTag();
                portTag.putString("id", port.id().value());
                portTag.putString("direction", port.direction().name());
                portTag.putString("valueType", port.valueType());
                portTag.putString("label", port.label());
                portTag.put("data", port.data());
                ports.add(portTag);
            }
            nodeTag.put("ports", ports);
            nodes.add(nodeTag);
        }
        tag.put("nodes", nodes);

        ListTag connections = new ListTag();
        for (ConnectionModel connection : graph.connections()) {
            CompoundTag connectionTag = new CompoundTag();
            connectionTag.putUUID("id", connection.id());
            connectionTag.putUUID("sourceNode", connection.sourceNode());
            connectionTag.putString("sourcePort", connection.sourcePort().value());
            connectionTag.putUUID("targetNode", connection.targetNode());
            connectionTag.putString("targetPort", connection.targetPort().value());
            ListTag waypoints = new ListTag();
            for (Waypoint waypoint : connection.waypoints()) {
                CompoundTag waypointTag = new CompoundTag();
                waypointTag.putDouble("x", waypoint.x());
                waypointTag.putDouble("y", waypoint.y());
                waypoints.add(waypointTag);
            }
            connectionTag.put("waypoints", waypoints);
            connectionTag.put("raw", connection.rawTag());
            connections.add(connectionTag);
        }
        tag.put("connections", connections);

        ListTag sections = new ListTag();
        for (SectionModel section : graph.sections()) {
            CompoundTag sectionTag = new CompoundTag();
            sectionTag.putUUID("id", section.id());
            sectionTag.putString("name", section.name());
            sectionTag.putInt("x", section.x());
            sectionTag.putInt("y", section.y());
            sectionTag.putInt("width", section.width());
            sectionTag.putInt("height", section.height());
            sectionTag.putInt("bodyColorArgb", section.bodyColorArgb());
            sectionTag.putInt("layer", section.layer());
            sectionTag.put("raw", section.rawTag());
            sections.add(sectionTag);
        }
        tag.put("sections", sections);
        tag.put("metadata", graph.metadata());
        tag.put("raw", graph.rawTag());
        return tag;
    }

    private static CompoundTag encodeDiagnostic(ProgramDiagnostic diagnostic) {
        CompoundTag tag = new CompoundTag();
        tag.putString("severity", diagnostic.severity().name());
        tag.putString("code", diagnostic.code());
        tag.putString("message", diagnostic.message());
        if (diagnostic.graphId() != null) tag.putUUID("graphId", diagnostic.graphId());
        if (diagnostic.nodeId() != null) tag.putUUID("nodeId", diagnostic.nodeId());
        if (diagnostic.connectionId() != null) tag.putUUID("connectionId", diagnostic.connectionId());
        tag.put("details", diagnostic.details());
        return tag;
    }

    private static List<ProgramDiagnostic> decodeDiagnostics(ListTag tags) {
        List<ProgramDiagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.getCompound(i);
            diagnostics.add(new ProgramDiagnostic(
                    parseEnum(Severity.class, tag.getString("severity"), Severity.WARNING),
                    tag.getString("code"),
                    tag.getString("message"),
                    optionalUuid(tag, "graphId"),
                    optionalUuid(tag, "nodeId"),
                    optionalUuid(tag, "connectionId"),
                    copyCompound(tag, "details")));
        }
        return diagnostics;
    }

    private static SectionModel decodeSection(CompoundTag tag, UUID id, String rawKey) {
        return new SectionModel(
                id,
                tag.getString("name"),
                tag.getInt("x"),
                tag.getInt("y"),
                tag.getInt("width"),
                tag.getInt("height"),
                tag.contains("bodyColorArgb")
                        ? tag.getInt("bodyColorArgb")
                        : SectionModel.DEFAULT_BODY_COLOR_ARGB,
                tag.getInt("layer"),
                copyCompound(tag, rawKey));
    }

    private static SectionModel decodeLegacySection(CompoundTag raw, UUID id) {
        int width = raw.contains("w") ? raw.getInt("w") : raw.getInt("width");
        int height = raw.contains("h") ? raw.getInt("h") : raw.getInt("height");
        int color = raw.contains("bodyArgb")
                ? raw.getInt("bodyArgb")
                : raw.contains("bodyColorArgb")
                        ? raw.getInt("bodyColorArgb")
                        : SectionModel.DEFAULT_BODY_COLOR_ARGB;
        return new SectionModel(
                id,
                raw.getString("name"),
                raw.getInt("x"),
                raw.getInt("y"),
                width,
                height,
                color,
                raw.getInt("layer"),
                raw);
    }

    private static void migrateLegacyPorts(ListTag tags, Direction direction, List<PortModel> destination) {
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag raw = tags.getCompound(i).copy();
            PortId fallback = direction == Direction.INPUT ? PortId.legacyInput(i) : PortId.legacyOutput(i);
            PortId id = fallback;
            if (raw.contains("portKey", Tag.TAG_STRING)) {
                try {
                    id = new PortId(raw.getString("portKey"));
                } catch (IllegalArgumentException ignored) {
                    id = fallback;
                }
            }
            String label = raw.contains("name", Tag.TAG_STRING)
                    ? raw.getString("name")
                    : (direction == Direction.INPUT ? "Input " : "Output ") + i;
            destination.add(new PortModel(id, direction, inferLegacyValueType(raw), label, raw));
        }
    }

    private static String inferLegacyValueType(CompoundTag raw) {
        if (raw.contains("dataType", Tag.TAG_STRING)) return raw.getString("dataType").toLowerCase();
        if (raw.contains("s", Tag.TAG_STRING)) return "string";
        if (raw.contains("value")) return "number";
        return "widget";
    }

    private static CompoundTag extractLegacyProperties(CompoundTag raw) {
        CompoundTag properties = raw.contains("computedPropertiesV2", Tag.TAG_COMPOUND)
                ? raw.getCompound("computedPropertiesV2").copy()
                : copyCompound(raw, "properties");
        if (raw.contains("elements", Tag.TAG_LIST)) {
            properties.put("legacyElements", raw.getList("elements", Tag.TAG_COMPOUND).copy());
        }
        return properties;
    }

    private static CompoundTag extractLegacyState(CompoundTag raw) {
        CompoundTag state = raw.contains("computedStateV2", Tag.TAG_COMPOUND)
                ? raw.getCompound("computedStateV2").copy()
                : copyCompound(raw, "state");
        Set<String> structural = Set.of(
                "typeId", "type", "id", "title", "x", "y", "inputs", "outputs", "elements", "properties", "state",
                "computedPropertiesV2", "computedStateV2", "ComputedMissingType");
        for (String key : raw.getAllKeys()) {
            if (!structural.contains(key)) {
                Tag value = raw.get(key);
                if (value != null) state.put(key, value.copy());
            }
        }
        return state;
    }

    private static PortId legacyPortId(CompoundTag raw, boolean source, NodeModel endpoint) {
        String stableKey = firstString(
                raw,
                source ? "sourcePort" : "targetPort",
                source ? "srcPort" : "tgtPort");
        int index = raw.contains(source ? "srcP" : "tgtP") ? raw.getInt(source ? "srcP" : "tgtP") : 0;
        if (!stableKey.isBlank()) {
            try {
                PortId candidate = new PortId(stableKey);
                Direction direction = source ? Direction.OUTPUT : Direction.INPUT;
                if (endpoint != null && endpoint.port(candidate, direction).isPresent()) {
                    return candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the lossless positional migration key.
            }
        }
        if (endpoint != null && index >= 0) {
            Direction direction = source ? Direction.OUTPUT : Direction.INPUT;
            int positionalIndex = 0;
            for (PortModel port : endpoint.ports()) {
                if (port.direction() != direction) continue;
                if (positionalIndex++ == index) return port.id();
            }
        }
        return source ? PortId.legacyOutput(index) : PortId.legacyInput(index);
    }

    private static List<Waypoint> decodeWaypoints(ListTag tags) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.getCompound(i);
            waypoints.add(new Waypoint(tag.getDouble("x"), tag.getDouble("y")));
        }
        return waypoints;
    }

    private static ProgramDiagnostic missingTypeDiagnostic(
            UUID graphId, UUID nodeId, String originalType, PlaceholderStatus status) {
        String message = status == PlaceholderStatus.MALFORMED_TYPE
                ? "Node has a missing or malformed type id and was preserved as a placeholder"
                : "Node type " + originalType + " is unavailable and was preserved as a placeholder";
        return new ProgramDiagnostic(
                Severity.ERROR,
                status == PlaceholderStatus.MALFORMED_TYPE ? "malformed_node_type" : "missing_node_type",
                message,
                graphId,
                nodeId,
                null);
    }

    private static PlaceholderStatus placeholderStatus(
            String canonicalType, String originalType, Predicate<String> knownNodeType) {
        if (originalType == null || originalType.isBlank() || !isValidType(originalType)) {
            return PlaceholderStatus.MALFORMED_TYPE;
        }
        try {
            return knownNodeType.test(canonicalType) ? PlaceholderStatus.RESOLVED : PlaceholderStatus.MISSING_TYPE;
        } catch (RuntimeException ignored) {
            return PlaceholderStatus.MISSING_TYPE;
        }
    }

    /** Rewrites only the old built-in namespace; arbitrary addon namespaces are left untouched. */
    public static String canonicalType(String rawType) {
        if (rawType == null || rawType.isBlank()) return "computed:missing";
        try {
            ResourceLocation id = ResourceLocation.parse(rawType);
            if ("websnodelib".equals(id.getNamespace())) {
                return ResourceLocation.fromNamespaceAndPath("computed", id.getPath()).toString();
            }
            return id.toString();
        } catch (RuntimeException ignored) {
            return "computed:missing";
        }
    }

    private static boolean isValidType(String type) {
        try {
            ResourceLocation.parse(type);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static PortId readPortId(
            String raw,
            PortId fallback,
            UUID graphId,
            UUID connectionId,
            List<ProgramDiagnostic> diagnostics) {
        try {
            return new PortId(raw);
        } catch (RuntimeException ignored) {
            diagnostics.add(new ProgramDiagnostic(
                    Severity.ERROR,
                    "invalid_port_id",
                    "Invalid stable port id was replaced with " + fallback,
                    graphId,
                    null,
                    connectionId));
            return fallback;
        }
    }

    private static UUID resolveLegacyEndpoint(String raw, Map<String, UUID> aliases, String fallbackSeed) {
        UUID alias = aliases.get(raw);
        if (alias != null) return alias;
        return parseUuid(raw, stableUuid(fallbackSeed));
    }

    private static boolean containsNode(List<NodeModel> nodes, UUID id) {
        return nodes.stream().anyMatch(node -> node.id().equals(id));
    }

    private static NodeModel findNode(List<NodeModel> nodes, UUID id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    private static UUID uniqueUuid(UUID requested, Set<UUID> used, String fallbackSeed) {
        UUID candidate = requested;
        int attempt = 0;
        while (!used.add(candidate)) {
            candidate = stableUuid(fallbackSeed + "/duplicate/" + attempt++);
        }
        return candidate;
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(("computed:" + seed).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID parseUuid(String raw, UUID fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static UUID readUuid(CompoundTag tag, String key, UUID fallback) {
        UUID value = optionalUuid(tag, key);
        return value == null ? fallback : value;
    }

    private static UUID optionalUuid(CompoundTag tag, String key) {
        try {
            if (tag.hasUUID(key)) return tag.getUUID(key);
            if (tag.contains(key, Tag.TAG_STRING)) return UUID.fromString(tag.getString(key));
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static String rawUuidText(CompoundTag tag, String key) {
        UUID uuid = optionalUuid(tag, key);
        if (uuid != null) return uuid.toString();
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : "";
    }

    private static String firstUuidText(CompoundTag tag, String... keys) {
        for (String key : keys) {
            String value = rawUuidText(tag, key);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String firstString(CompoundTag tag, String... keys) {
        for (String key : keys) {
            if (tag.contains(key, Tag.TAG_STRING)) return tag.getString(key);
        }
        return "";
    }

    private static CompoundTag firstCompound(CompoundTag tag, String... keys) {
        for (String key : keys) {
            if (tag.contains(key, Tag.TAG_COMPOUND)) return tag.getCompound(key);
        }
        return new CompoundTag();
    }

    private static CompoundTag copyCompound(CompoundTag owner, String key) {
        return owner.contains(key, Tag.TAG_COMPOUND) ? owner.getCompound(key).copy() : new CompoundTag();
    }

    private static CompoundTag copyExcept(CompoundTag source, Set<String> excludedKeys) {
        CompoundTag result = new CompoundTag();
        for (String key : source.getAllKeys()) {
            if (!excludedKeys.contains(key)) {
                Tag value = source.get(key);
                if (value != null) result.put(key, value.copy());
            }
        }
        return result;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
