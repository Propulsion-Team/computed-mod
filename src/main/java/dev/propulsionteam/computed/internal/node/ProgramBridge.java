package dev.propulsionteam.computed.internal.node;

import dev.propulsionteam.computed.api.node.ComputedNodeApi;
import dev.propulsionteam.computed.internal.node.api.FunctionCardNode;
import dev.propulsionteam.computed.internal.node.api.FunctionDefinitionStore;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.node.program.ComputedProgram;
import dev.propulsionteam.computed.node.program.ConnectionModel;
import dev.propulsionteam.computed.node.program.FunctionModel;
import dev.propulsionteam.computed.node.program.GraphModel;
import dev.propulsionteam.computed.node.program.NodeModel;
import dev.propulsionteam.computed.node.program.PortModel;
import dev.propulsionteam.computed.node.program.SectionModel;
import dev.propulsionteam.computed.node.program.ProgramCodec;
import dev.propulsionteam.computed.node.runtime.GraphAnalysis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Narrow compatibility boundary between the v2 persistent model and the editor/runtime that is
 * being retired. Nothing outside Computed internals should depend on WGraph.
 */
public final class ProgramBridge {
    public static final String PROGRAM_TAG = "ComputedProgram";

    private ProgramBridge() {}

    public record RuntimeProgram(
            ComputedProgram program,
            WGraph graph,
            FunctionDefinitionStore functions,
            boolean migrated) {}

    public static RuntimeProgram decode(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        ProgramCodec.DecodeResult decoded = ProgramCodec.decode(source, ProgramBridge::isKnownNodeType);
        CompoundTag bundle = ProgramCodec.toLegacyBundleTag(decoded.program());
        WGraph graph = new WGraph();
        graph.load(bundle.getCompound("ComputerGraph"));
        FunctionDefinitionStore functions = new FunctionDefinitionStore();
        functions.load(bundle.getList("ComputerFunctions", Tag.TAG_COMPOUND));
        FunctionCardNode.applyLibraryToInnerGraphs(graph, functions);
        return new RuntimeProgram(decoded.program(), graph, functions, decoded.migrated());
    }

    public static ComputedProgram snapshot(
            WGraph graph, FunctionDefinitionStore functions, long revision) {
        Objects.requireNonNull(graph, "graph");
        FunctionDefinitionStore library = functions == null ? new FunctionDefinitionStore() : functions;
        library.syncBodiesFromGraph(graph);
        CompoundTag bundle = new CompoundTag();
        bundle.put("ComputerGraph", graph.save());
        bundle.put("ComputerFunctions", library.saveList());
        return ProgramCodec.decode(bundle, ProgramBridge::isKnownNodeType)
                .program()
                .withRevision(revision);
    }

    /**
     * Applies an editor/runtime snapshot without discarding raw addon data or connections that the
     * transitional UI cannot represent. Valid connections absent from the snapshot stay deleted.
     */
    public static ComputedProgram reconcile(ComputedProgram base, ComputedProgram snapshot) {
        if (base == null) return snapshot;
        GraphModel root = reconcileGraph(base.rootGraph(), snapshot.rootGraph());
        Map<UUID, FunctionModel> baseFunctions = new HashMap<>();
        for (FunctionModel function : base.functions()) baseFunctions.put(function.id(), function);
        List<FunctionModel> functions = new ArrayList<>();
        for (FunctionModel current : snapshot.functions()) {
            FunctionModel previous = baseFunctions.get(current.id());
            functions.add(previous == null
                    ? current
                    : new FunctionModel(
                            current.id(),
                            current.name(),
                            reconcileGraph(previous.graph(), current.graph()),
                            mergeTags(previous.metadata(), current.metadata()),
                            mergeTags(previous.rawTag(), current.rawTag())));
        }
        return new ComputedProgram(
                snapshot.revision(),
                root,
                functions,
                mergeDiagnostics(base, snapshot),
                mergeTags(base.metadata(), snapshot.metadata()));
    }

    /**
     * Keeps runtime state authoritative when an editor saves an older structural snapshot. Matching
     * node ids and types retain the state captured on the server; new or replaced nodes keep the
     * incoming state. Function bodies are matched by stable function id.
     */
    public static ComputedProgram preserveRuntimeState(ComputedProgram incoming, ComputedProgram authoritative) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(authoritative, "authoritative");
        Map<UUID, FunctionModel> authoritativeFunctions = new HashMap<>();
        for (FunctionModel function : authoritative.functions()) {
            authoritativeFunctions.put(function.id(), function);
        }
        List<FunctionModel> functions = new ArrayList<>(incoming.functions().size());
        for (FunctionModel function : incoming.functions()) {
            FunctionModel current = authoritativeFunctions.get(function.id());
            functions.add(current == null
                    ? function
                    : new FunctionModel(
                            function.id(),
                            function.name(),
                            preserveGraphRuntimeState(function.graph(), current.graph()),
                            function.metadata(),
                            function.rawTag()));
        }
        return new ComputedProgram(
                incoming.revision(),
                preserveGraphRuntimeState(incoming.rootGraph(), authoritative.rootGraph()),
                functions,
                incoming.diagnostics(),
                incoming.metadata());
    }

    private static GraphModel preserveGraphRuntimeState(GraphModel incoming, GraphModel authoritative) {
        Map<UUID, NodeModel> authoritativeNodes = new HashMap<>();
        for (NodeModel node : authoritative.nodes()) authoritativeNodes.put(node.id(), node);
        List<NodeModel> nodes = new ArrayList<>(incoming.nodes().size());
        for (NodeModel node : incoming.nodes()) {
            NodeModel current = authoritativeNodes.get(node.id());
            if (current == null || !current.typeId().equals(node.typeId())) {
                nodes.add(node);
                continue;
            }
            CompoundTag state = current.state();
            CompoundTag incomingState = node.state();
            // Function-card bodies are editable program structure, not server-owned temporal state.
            if (incomingState.contains("innerGraph", Tag.TAG_COMPOUND)) {
                state.put("innerGraph", incomingState.getCompound("innerGraph").copy());
            } else {
                state.remove("innerGraph");
            }
            nodes.add(new NodeModel(
                    node.id(),
                    node.typeId(),
                    node.originalTypeId(),
                    node.title(),
                    node.x(),
                    node.y(),
                    node.properties(),
                    state,
                    node.ports(),
                    node.placeholderStatus(),
                    node.rawTag()));
        }
        return new GraphModel(
                incoming.id(),
                nodes,
                incoming.connections(),
                incoming.sections(),
                incoming.metadata(),
                incoming.rawTag());
    }

    private static GraphModel reconcileGraph(GraphModel base, GraphModel current) {
        Map<UUID, NodeModel> baseNodes = new HashMap<>();
        for (NodeModel node : base.nodes()) baseNodes.put(node.id(), node);
        List<NodeModel> nodes = new ArrayList<>();
        for (NodeModel node : current.nodes()) {
            NodeModel previous = baseNodes.get(node.id());
            nodes.add(previous == null
                    ? node
                    : new NodeModel(
                            node.id(),
                            node.typeId(),
                            previous.originalTypeId(),
                            node.title(),
                            node.x(),
                            node.y(),
                            mergeTags(previous.properties(), node.properties()),
                            mergeTags(previous.state(), node.state()),
                            node.ports(),
                            node.placeholderStatus(),
                            mergeTags(previous.rawTag(), node.rawTag())));
        }

        Map<ConnectionIdentity, List<ConnectionModel>> previousByIdentity = new LinkedHashMap<>();
        for (ConnectionModel connection : base.connections()) {
            previousByIdentity.computeIfAbsent(ConnectionIdentity.of(connection), ignored -> new ArrayList<>())
                    .add(connection);
        }
        Set<UUID> consumedPrevious = new HashSet<>();
        List<ConnectionModel> connections = new ArrayList<>();
        for (ConnectionModel connection : current.connections()) {
            List<ConnectionModel> candidates = previousByIdentity.get(ConnectionIdentity.of(connection));
            ConnectionModel previous = candidates == null
                    ? null
                    : candidates.stream().filter(candidate -> !consumedPrevious.contains(candidate.id())).findFirst().orElse(null);
            if (previous == null) {
                connections.add(connection);
            } else {
                consumedPrevious.add(previous.id());
                connections.add(new ConnectionModel(
                        previous.id(),
                        connection.sourceNode(),
                        connection.sourcePort(),
                        connection.targetNode(),
                        connection.targetPort(),
                        connection.waypoints(),
                        mergeTags(previous.rawTag(), connection.rawTag())));
            }
        }
        for (ConnectionModel previous : base.connections()) {
            if (!consumedPrevious.contains(previous.id()) && structurallyUnrepresentable(base, previous)) {
                connections.add(previous);
            }
        }

        Map<UUID, SectionModel> baseSections = new HashMap<>();
        for (SectionModel section : base.sections()) baseSections.put(section.id(), section);
        List<SectionModel> sections = new ArrayList<>();
        for (SectionModel section : current.sections()) {
            SectionModel previous = baseSections.get(section.id());
            sections.add(previous == null
                    ? section
                    : new SectionModel(
                            section.id(),
                            section.name(),
                            section.x(),
                            section.y(),
                            section.width(),
                            section.height(),
                            section.bodyColorArgb(),
                            section.layer(),
                            mergeTags(previous.rawTag(), section.rawTag())));
        }
        return new GraphModel(
                base.id(),
                nodes,
                connections,
                sections,
                mergeTags(base.metadata(), current.metadata()),
                mergeTags(base.rawTag(), current.rawTag()));
    }

    private static boolean structurallyUnrepresentable(GraphModel graph, ConnectionModel connection) {
        NodeModel source = graph.node(connection.sourceNode()).orElse(null);
        NodeModel target = graph.node(connection.targetNode()).orElse(null);
        if (source == null || target == null) return true;
        PortModel sourcePort = source.port(connection.sourcePort(), PortModel.Direction.OUTPUT).orElse(null);
        PortModel targetPort = target.port(connection.targetPort(), PortModel.Direction.INPUT).orElse(null);
        if (sourcePort == null || targetPort == null) return true;
        return !GraphAnalysis.compatibleValueTypes(sourcePort.valueType(), targetPort.valueType());
    }

    private static List<dev.propulsionteam.computed.node.program.ProgramDiagnostic> mergeDiagnostics(
            ComputedProgram base, ComputedProgram current) {
        var merged = new LinkedHashMap<String, dev.propulsionteam.computed.node.program.ProgramDiagnostic>();
        for (var diagnostic : base.diagnostics()) merged.put(diagnosticKey(diagnostic), diagnostic);
        for (var diagnostic : current.diagnostics()) merged.put(diagnosticKey(diagnostic), diagnostic);
        return List.copyOf(merged.values());
    }

    private static String diagnosticKey(dev.propulsionteam.computed.node.program.ProgramDiagnostic diagnostic) {
        return diagnostic.code() + '|' + diagnostic.graphId() + '|' + diagnostic.nodeId() + '|'
                + diagnostic.connectionId() + '|' + diagnostic.message();
    }

    private static CompoundTag mergeTags(CompoundTag base, CompoundTag current) {
        CompoundTag merged = base == null ? new CompoundTag() : base.copy();
        if (current != null) {
            for (String key : current.getAllKeys()) {
                Tag value = current.get(key);
                if (value != null) merged.put(key, value.copy());
            }
        }
        return merged;
    }

    private record ConnectionIdentity(
            UUID sourceNode,
            String sourcePort,
            UUID targetNode,
            String targetPort) {
        static ConnectionIdentity of(ConnectionModel connection) {
            return new ConnectionIdentity(
                    connection.sourceNode(),
                    connection.sourcePort().value(),
                    connection.targetNode(),
                    connection.targetPort().value());
        }
    }

    public static CompoundTag writeEnvelope(ComputedProgram program) {
        CompoundTag envelope = new CompoundTag();
        envelope.put(PROGRAM_TAG, ProgramCodec.write(program));
        return envelope;
    }

    public static boolean containsProgram(CompoundTag source) {
        return source.contains(PROGRAM_TAG, Tag.TAG_COMPOUND)
                || source.contains("formatVersion")
                || source.contains("ComputerGraph", Tag.TAG_COMPOUND)
                || source.contains("nodes", Tag.TAG_LIST)
                || source.contains("graph", Tag.TAG_COMPOUND);
    }

    public static boolean isKnownNodeType(String raw) {
        try {
            ResourceLocation id = NodeRegistry.canonicalize(ResourceLocation.parse(raw));
            return NodeRegistry.isRegistered(id) || ComputedNodeApi.nodeType(id).isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isStateBoundaryType(String raw) {
        try {
            ResourceLocation id = NodeRegistry.canonicalize(ResourceLocation.parse(raw));
            var publicType = ComputedNodeApi.nodeType(id);
            if (publicType.isPresent()) return publicType.get().stateBoundary();
            WNode node = NodeRegistry.createNode(id, 0, 0);
            return node != null && node.isStateBoundary();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static GraphAnalysis.AnalysisResult analyze(ComputedProgram program) {
        return GraphAnalysis.analyze(program.rootGraph(), ProgramBridge::isStateBoundaryType);
    }

    /** Analyzes the root and every saved function graph for transactional server validation. */
    public static List<AnalyzedGraph> analyzeAll(ComputedProgram program) {
        Objects.requireNonNull(program, "program");
        List<AnalyzedGraph> results = new ArrayList<>(program.functions().size() + 1);
        results.add(analyzeGraph(program.rootGraph()));
        for (FunctionModel function : program.functions()) {
            results.add(analyzeGraph(function.graph()));
        }
        return List.copyOf(results);
    }

    private static AnalyzedGraph analyzeGraph(GraphModel graph) {
        return new AnalyzedGraph(graph.id(), GraphAnalysis.analyze(graph, ProgramBridge::isStateBoundaryType));
    }

    public record AnalyzedGraph(UUID graphId, GraphAnalysis.AnalysisResult analysis) {
        public AnalyzedGraph {
            Objects.requireNonNull(graphId, "graphId");
            Objects.requireNonNull(analysis, "analysis");
        }
    }
}
