package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Phase;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Severity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class GraphAnalyzer {
    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);

    private GraphAnalyzer() {}

    public static GraphAnalysisResult analyze(
            ComputedGraph graph,
            Predicate<GraphNode> stateBoundary) {
        Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
        List<ComputedDiagnostic> diagnostics = new ArrayList<>();
        for (GraphNode node : graph.nodes()) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                diagnostics.add(error("duplicate_node", "Duplicate node id " + node.id(), node.id()));
            }
        }
        Map<UUID, Set<UUID>> outgoing = new HashMap<>();
        Map<UUID, Integer> indegree = new HashMap<>();
        nodes.keySet().forEach(id -> {
            outgoing.put(id, new LinkedHashSet<>());
            indegree.put(id, 0);
        });
        Set<String> occupiedInputs = new HashSet<>();
        for (GraphConnection connection : graph.connections()) {
            GraphNode source = nodes.get(connection.sourceNode());
            GraphNode target = nodes.get(connection.targetNode());
            if (source == null || target == null) {
                diagnostics.add(new ComputedDiagnostic(
                        Severity.ERROR,
                        Phase.GRAPH,
                        "dangling_connection",
                        "Connection references a missing node",
                        null,
                        null,
                        null));
                continue;
            }
            PortSnapshot sourcePort = findPort(source, connection.sourcePort(), PortDirection.OUTPUT);
            PortSnapshot targetPort = findPort(target, connection.targetPort(), PortDirection.INPUT);
            if (sourcePort == null || targetPort == null) {
                diagnostics.add(error(
                        "missing_port",
                        "Connection references a missing port",
                        target.id()));
                continue;
            }
            if (sourcePort.type() != targetPort.type()) {
                diagnostics.add(error(
                        "incompatible_ports",
                        "Cannot connect " + sourcePort.type() + " to " + targetPort.type(),
                        target.id()));
                continue;
            }
            String inputIdentity = target.id() + "\u0000" + targetPort.id();
            if (!occupiedInputs.add(inputIdentity)) {
                diagnostics.add(error(
                        "multiple_input_connections",
                        "Input " + targetPort.id() + " has more than one connection",
                        target.id()));
                continue;
            }
            if (!stateBoundary.test(target) && outgoing.get(source.id()).add(target.id())) {
                indegree.compute(target.id(), (id, value) -> value + 1);
            }
        }

        PriorityQueue<UUID> ready = new PriorityQueue<>(UUID_ORDER);
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        List<UUID> order = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            UUID node = ready.remove();
            order.add(node);
            outgoing.get(node).stream().sorted(UUID_ORDER).forEach(target -> {
                int next = indegree.compute(target, (id, degree) -> degree - 1);
                if (next == 0) {
                    ready.add(target);
                }
            });
        }

        Set<UUID> unresolved = new HashSet<>(nodes.keySet());
        unresolved.removeAll(order);
        List<List<UUID>> cycles = stronglyConnected(outgoing, unresolved);
        cycles.forEach(cycle -> diagnostics.add(error(
                "combinational_cycle",
                "Combinational cycle contains " + cycle.size() + " nodes",
                cycle.getFirst())));
        unresolved.stream().sorted(UUID_ORDER).forEach(order::add);
        return new GraphAnalysisResult(order, cycles, diagnostics);
    }

    private static PortSnapshot findPort(GraphNode node, String id, PortDirection direction) {
        return node.ports().stream()
                .filter(port -> port.direction() == direction && port.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static List<List<UUID>> stronglyConnected(
            Map<UUID, Set<UUID>> outgoing,
            Set<UUID> candidates) {
        Tarjan tarjan = new Tarjan(outgoing, candidates);
        candidates.stream().sorted(UUID_ORDER).forEach(tarjan::visitIfNeeded);
        return tarjan.cycles.stream()
                .sorted(Comparator.comparing(cycle -> cycle.getFirst().toString()))
                .toList();
    }

    private static ComputedDiagnostic error(String code, String message, UUID nodeId) {
        return new ComputedDiagnostic(Severity.ERROR, Phase.GRAPH, code, message, nodeId, null, null);
    }

    private static final class Tarjan {
        private final Map<UUID, Set<UUID>> outgoing;
        private final Set<UUID> candidates;
        private final Map<UUID, Integer> indices = new HashMap<>();
        private final Map<UUID, Integer> lowLinks = new HashMap<>();
        private final ArrayDeque<UUID> stack = new ArrayDeque<>();
        private final Set<UUID> stacked = new HashSet<>();
        private final List<List<UUID>> cycles = new ArrayList<>();
        private int index;

        private Tarjan(Map<UUID, Set<UUID>> outgoing, Set<UUID> candidates) {
            this.outgoing = outgoing;
            this.candidates = candidates;
        }

        private void visitIfNeeded(UUID node) {
            if (!indices.containsKey(node)) {
                visit(node);
            }
        }

        private void visit(UUID node) {
            indices.put(node, index);
            lowLinks.put(node, index++);
            stack.push(node);
            stacked.add(node);
            outgoing.get(node).stream()
                    .filter(candidates::contains)
                    .sorted(UUID_ORDER)
                    .forEach(target -> {
                        if (!indices.containsKey(target)) {
                            visit(target);
                            lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(target)));
                        } else if (stacked.contains(target)) {
                            lowLinks.put(node, Math.min(lowLinks.get(node), indices.get(target)));
                        }
                    });
            if (!lowLinks.get(node).equals(indices.get(node))) {
                return;
            }
            List<UUID> component = new ArrayList<>();
            UUID current;
            do {
                current = stack.pop();
                stacked.remove(current);
                component.add(current);
            } while (!current.equals(node));
            component.sort(UUID_ORDER);
            boolean selfLoop = component.size() == 1 && outgoing.get(component.getFirst()).contains(component.getFirst());
            if (component.size() > 1 || selfLoop) {
                cycles.add(List.copyOf(component));
            }
        }
    }
}
