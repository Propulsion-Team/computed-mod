package dev.propulsionteam.computed.node.runtime;

import dev.propulsionteam.computed.node.program.ConnectionModel;
import dev.propulsionteam.computed.node.program.GraphModel;
import dev.propulsionteam.computed.node.program.NodeModel;
import dev.propulsionteam.computed.node.program.PortModel;
import dev.propulsionteam.computed.node.program.ProgramDiagnostic;
import dev.propulsionteam.computed.node.program.ProgramDiagnostic.Severity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Deterministic validation, SCC detection, and execution ordering for a persistent graph. */
public final class GraphAnalysis {
    private GraphAnalysis() {}

    /**
     * Analyzes combinational dependencies. Edges leaving a state-boundary node are deliberately not
     * dependencies on that node's current inputs: its prior-state outputs are exposed before the
     * ordered evaluation pass, so such a node safely breaks a feedback loop.
     */
    public static AnalysisResult analyze(GraphModel graph, Predicate<String> isStateBoundaryType) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(isStateBoundaryType, "isStateBoundaryType");

        List<ProgramDiagnostic> diagnostics = new ArrayList<>();
        Set<UUID> disabledNodes = new HashSet<>();
        Set<UUID> invalidConnections = new HashSet<>();
        Map<UUID, NodeModel> nodes = new LinkedHashMap<>();

        List<NodeModel> sortedNodes = new ArrayList<>(graph.nodes());
        sortedNodes.sort(Comparator.comparing(NodeModel::id));
        for (NodeModel node : sortedNodes) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                disabledNodes.add(node.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_node_id",
                        "Duplicate node id makes all instances with that id ambiguous",
                        graph.id(),
                        node.id(),
                        null));
            }
            if (node.isPlaceholder()) {
                disabledNodes.add(node.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "placeholder_node_disabled",
                        "Unavailable node type " + node.originalTypeId() + " is disabled until it can be resolved",
                        graph.id(),
                        node.id(),
                        null));
            }
            validatePortIdentities(graph, node, disabledNodes, diagnostics);
        }

        Map<UUID, SortedSet<UUID>> adjacency = new HashMap<>();
        for (UUID nodeId : nodes.keySet()) adjacency.put(nodeId, new TreeSet<>());

        List<ConnectionModel> sortedConnections = new ArrayList<>(graph.connections());
        sortedConnections.sort(Comparator.comparing(ConnectionModel::id));
        Set<UUID> seenConnectionIds = new HashSet<>();
        for (ConnectionModel connection : sortedConnections) {
            if (!seenConnectionIds.add(connection.id())) {
                invalidConnections.add(connection.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_connection_id",
                        "Duplicate connection id is ambiguous",
                        graph.id(),
                        null,
                        connection.id()));
                continue;
            }
            NodeModel source = nodes.get(connection.sourceNode());
            NodeModel target = nodes.get(connection.targetNode());
            if (source == null || target == null) {
                invalidConnections.add(connection.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "dangling_connection",
                        "Connection references a node that is not present in the graph",
                        graph.id(),
                        null,
                        connection.id()));
                continue;
            }

            PortModel sourcePort = source.port(connection.sourcePort(), PortModel.Direction.OUTPUT).orElse(null);
            PortModel targetPort = target.port(connection.targetPort(), PortModel.Direction.INPUT).orElse(null);
            if (sourcePort == null || targetPort == null) {
                invalidConnections.add(connection.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "invalid_connection_port",
                        "Connection references a missing or directionally invalid port",
                        graph.id(),
                        null,
                        connection.id()));
                continue;
            }
            if (!compatibleValueTypes(sourcePort.valueType(), targetPort.valueType())) {
                invalidConnections.add(connection.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "connection_type_mismatch",
                        "Cannot connect " + sourcePort.valueType() + " to " + targetPort.valueType(),
                        graph.id(),
                        null,
                        connection.id()));
                continue;
            }
            if (disabledNodes.contains(source.id()) || disabledNodes.contains(target.id())) {
                invalidConnections.add(connection.id());
                continue;
            }

            // A state node publishes its previous committed state before this ordered pass. Downstream
            // nodes therefore do not wait for its next-state calculation; incoming dependencies are
            // retained so that next-state calculation observes this step's resolved inputs.
            if (!safePredicateTest(isStateBoundaryType, source.typeId())) {
                adjacency.get(source.id()).add(target.id());
            }
        }

        List<List<UUID>> cycles = findCombinationalCycles(nodes.keySet(), adjacency, disabledNodes);
        for (List<UUID> cycle : cycles) {
            disabledNodes.addAll(cycle);
            CompoundTag details = new CompoundTag();
            ListTag ids = new ListTag();
            for (UUID id : cycle) ids.add(StringTag.valueOf(id.toString()));
            details.put("nodeIds", ids);
            diagnostics.add(new ProgramDiagnostic(
                    Severity.ERROR,
                    cycle.size() == 1 ? "combinational_self_loop" : "combinational_cycle",
                    "Combinational cycle is disabled; its nodes and connections remain editable",
                    graph.id(),
                    cycle.size() == 1 ? cycle.getFirst() : null,
                    null,
                    details));
        }

        for (ConnectionModel connection : sortedConnections) {
            if (disabledNodes.contains(connection.sourceNode()) || disabledNodes.contains(connection.targetNode())) {
                invalidConnections.add(connection.id());
            }
        }

        List<UUID> topologicalOrder = deterministicTopologicalOrder(nodes.keySet(), adjacency, disabledNodes);
        return new AnalysisResult(topologicalOrder, disabledNodes, invalidConnections, cycles, diagnostics);
    }

    private static void validatePortIdentities(
            GraphModel graph,
            NodeModel node,
            Set<UUID> disabledNodes,
            List<ProgramDiagnostic> diagnostics) {
        Set<String> identities = new HashSet<>();
        for (PortModel port : node.ports()) {
            String identity = port.direction() + "\u0000" + port.id().value();
            if (!identities.add(identity)) {
                disabledNodes.add(node.id());
                diagnostics.add(new ProgramDiagnostic(
                        Severity.ERROR,
                        "duplicate_port_id",
                        "Duplicate stable port id " + port.id() + " makes the node schema ambiguous",
                        graph.id(),
                        node.id(),
                        null));
            }
        }
    }

    /** Runtime-compatible value types, including Computed's documented number-to-string coercion. */
    public static boolean compatibleValueTypes(String source, String target) {
        return PortModel.UNKNOWN_VALUE_TYPE.equals(source)
                || PortModel.UNKNOWN_VALUE_TYPE.equals(target)
                || source.equals(target)
                || ("number".equals(source) && "string".equals(target));
    }

    private static boolean safePredicateTest(Predicate<String> predicate, String type) {
        try {
            return predicate.test(type);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<List<UUID>> findCombinationalCycles(
            Set<UUID> nodeIds,
            Map<UUID, SortedSet<UUID>> adjacency,
            Set<UUID> alreadyDisabled) {
        Tarjan tarjan = new Tarjan(adjacency, alreadyDisabled);
        List<UUID> orderedNodes = new ArrayList<>(nodeIds);
        Collections.sort(orderedNodes);
        for (UUID node : orderedNodes) {
            if (!alreadyDisabled.contains(node)) tarjan.visitIfNeeded(node);
        }

        List<List<UUID>> cycles = new ArrayList<>();
        for (List<UUID> component : tarjan.components()) {
            boolean selfLoop = component.size() == 1
                    && adjacency.getOrDefault(component.getFirst(), Collections.emptySortedSet())
                            .contains(component.getFirst());
            if (component.size() > 1 || selfLoop) {
                List<UUID> sorted = new ArrayList<>(component);
                Collections.sort(sorted);
                cycles.add(List.copyOf(sorted));
            }
        }
        cycles.sort(Comparator.comparing(component -> component.getFirst()));
        return List.copyOf(cycles);
    }

    private static List<UUID> deterministicTopologicalOrder(
            Set<UUID> nodeIds,
            Map<UUID, SortedSet<UUID>> adjacency,
            Set<UUID> disabledNodes) {
        Map<UUID, Integer> indegree = new HashMap<>();
        for (UUID node : nodeIds) {
            if (!disabledNodes.contains(node)) indegree.put(node, 0);
        }
        for (Map.Entry<UUID, SortedSet<UUID>> entry : adjacency.entrySet()) {
            if (!indegree.containsKey(entry.getKey())) continue;
            for (UUID target : entry.getValue()) {
                if (indegree.containsKey(target)) indegree.merge(target, 1, Integer::sum);
            }
        }

        PriorityQueue<UUID> ready = new PriorityQueue<>();
        indegree.forEach((node, degree) -> {
            if (degree == 0) ready.add(node);
        });

        List<UUID> order = new ArrayList<>(indegree.size());
        while (!ready.isEmpty()) {
            UUID node = ready.remove();
            order.add(node);
            for (UUID target : adjacency.getOrDefault(node, Collections.emptySortedSet())) {
                if (!indegree.containsKey(target)) continue;
                int remaining = indegree.merge(target, -1, Integer::sum);
                if (remaining == 0) ready.add(target);
            }
        }
        return List.copyOf(order);
    }

    public record AnalysisResult(
            List<UUID> topologicalOrder,
            Set<UUID> disabledNodes,
            Set<UUID> invalidConnections,
            List<List<UUID>> combinationalCycles,
            List<ProgramDiagnostic> diagnostics) {
        public AnalysisResult {
            topologicalOrder = List.copyOf(topologicalOrder);
            disabledNodes = Collections.unmodifiableSet(new LinkedHashSet<>(sorted(disabledNodes)));
            invalidConnections = Collections.unmodifiableSet(new LinkedHashSet<>(sorted(invalidConnections)));
            combinationalCycles = combinationalCycles.stream().map(List::copyOf).toList();
            diagnostics = List.copyOf(diagnostics);
        }

        private static List<UUID> sorted(Set<UUID> ids) {
            List<UUID> sorted = new ArrayList<>(ids);
            Collections.sort(sorted);
            return sorted;
        }

        public boolean executable(UUID nodeId) {
            return !disabledNodes.contains(nodeId);
        }
    }

    private static final class Tarjan {
        private final Map<UUID, SortedSet<UUID>> adjacency;
        private final Set<UUID> excluded;
        private final Map<UUID, Integer> indexByNode = new HashMap<>();
        private final Map<UUID, Integer> lowLinkByNode = new HashMap<>();
        private final Deque<UUID> stack = new ArrayDeque<>();
        private final Set<UUID> onStack = new HashSet<>();
        private final List<List<UUID>> components = new ArrayList<>();
        private int nextIndex;

        private Tarjan(Map<UUID, SortedSet<UUID>> adjacency, Set<UUID> excluded) {
            this.adjacency = adjacency;
            this.excluded = excluded;
        }

        private void visitIfNeeded(UUID node) {
            if (!indexByNode.containsKey(node)) strongConnect(node);
        }

        private void strongConnect(UUID node) {
            int index = nextIndex++;
            indexByNode.put(node, index);
            lowLinkByNode.put(node, index);
            stack.push(node);
            onStack.add(node);

            for (UUID target : adjacency.getOrDefault(node, Collections.emptySortedSet())) {
                if (excluded.contains(target)) continue;
                if (!indexByNode.containsKey(target)) {
                    strongConnect(target);
                    lowLinkByNode.put(node, Math.min(lowLinkByNode.get(node), lowLinkByNode.get(target)));
                } else if (onStack.contains(target)) {
                    lowLinkByNode.put(node, Math.min(lowLinkByNode.get(node), indexByNode.get(target)));
                }
            }

            if (lowLinkByNode.get(node).equals(indexByNode.get(node))) {
                List<UUID> component = new ArrayList<>();
                UUID member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(component);
            }
        }

        private List<List<UUID>> components() {
            return components;
        }
    }
}
