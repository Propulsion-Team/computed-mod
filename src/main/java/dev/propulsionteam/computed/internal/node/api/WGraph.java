package dev.propulsionteam.computed.internal.node.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class WGraph {
    private final List<WNode> nodes = new ArrayList<>();
    private final List<WConnection> connections = new ArrayList<>();
    private final Map<UUID, WNode> nodeIndex = new HashMap<>();
    private final Map<UUID, WNode> nodeArchive = new HashMap<>();
    private long connectionGeometryRevision;

    public WNode getNode(UUID id) {
        return id == null ? null : nodeIndex.get(id);
    }

    public List<WNode> getNodes() {
        return nodes;
    }

    public List<WConnection> getConnections() {
        return connections;
    }

    public long getConnectionGeometryRevision() {
        return connectionGeometryRevision;
    }

    public void addNode(WNode node) {
        if (node == null || nodeIndex.containsKey(node.getId())) {
            return;
        }
        nodes.add(node);
        nodeIndex.put(node.getId(), node);
        nodeArchive.put(node.getId(), node);
        node.bindOwningGraph(this);
        updateTopology();
    }

    public void removeNode(WNode node) {
        if (node == null || !nodeIndex.containsKey(node.getId())) {
            return;
        }
        nodes.remove(node);
        nodeIndex.remove(node.getId());
        node.bindOwningGraph(null);
        connections.removeIf(connection ->
                connection.sourceNode().equals(node.getId()) || connection.targetNode().equals(node.getId()));
        updateTopology();
    }

    public boolean connect(UUID sourceNode, int sourcePin, UUID targetNode, int targetPin) {
        return connect(WConnection.withoutWaypoints(sourceNode, sourcePin, targetNode, targetPin));
    }

    public boolean connect(WConnection connection) {
        WNode source = getNode(connection.sourceNode());
        WNode target = getNode(connection.targetNode());
        if (source == null
                || target == null
                || source == target
                || connection.sourcePin() < 0
                || connection.sourcePin() >= source.getOutputs().size()
                || connection.targetPin() < 0
                || connection.targetPin() >= target.getInputs().size()
                || source.getOutputs().get(connection.sourcePin()).getDataType()
                        != target.getInputs().get(connection.targetPin()).getDataType()) {
            return false;
        }
        connections.removeIf(existing ->
                existing.targetNode().equals(connection.targetNode())
                        && existing.targetPin() == connection.targetPin());
        String sourceKey = connection.sourcePortKey();
        String targetKey = connection.targetPortKey();
        if (sourceKey == null) {
            sourceKey = WNode.stablePortId(source.getOutputs(), connection.sourcePin(), "output");
        }
        if (targetKey == null) {
            targetKey = WNode.stablePortId(target.getInputs(), connection.targetPin(), "input");
        }
        connections.add(connection.withStablePorts(sourceKey, targetKey));
        updateTopology();
        return true;
    }

    public void disconnectNodes(Set<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        connections.removeIf(connection ->
                nodeIds.contains(connection.sourceNode()) || nodeIds.contains(connection.targetNode()));
        updateTopology();
    }

    public void shiftWaypointsForConnectionsTouching(Collection<UUID> nodeIds, int deltaX, int deltaY) {
        if (nodeIds == null || nodeIds.isEmpty() || deltaX == 0 && deltaY == 0) {
            return;
        }
        for (int i = 0; i < connections.size(); i++) {
            WConnection connection = connections.get(i);
            if ((!nodeIds.contains(connection.sourceNode()) && !nodeIds.contains(connection.targetNode()))
                    || connection.waypointXs().length == 0) {
                continue;
            }
            int[] xs = Arrays.copyOf(connection.waypointXs(), connection.waypointXs().length);
            int[] ys = Arrays.copyOf(connection.waypointYs(), connection.waypointYs().length);
            for (int waypoint = 0; waypoint < xs.length; waypoint++) {
                xs[waypoint] += deltaX;
                ys[waypoint] += deltaY;
            }
            connections.set(i, connection.withWaypoints(xs, ys));
        }
        markConnectionGeometryChanged();
    }

    public void markConnectionGeometryChanged() {
        connectionGeometryRevision++;
    }

    public void onNodePinSchemaChanged(WNode node) {
        resolveStablePorts();
        updateTopology();
    }

    public void updateTopology() {
        connections.removeIf(connection -> !isValidConnection(connection));
        refreshConnectedPins();
        Map<UUID, Integer> indegree = new HashMap<>();
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        for (WNode node : nodes) {
            indegree.put(node.getId(), 0);
            outgoing.put(node.getId(), new ArrayList<>());
            node.setTopoDepth(0);
        }
        for (WConnection connection : connections) {
            WNode source = getNode(connection.sourceNode());
            if (source != null && !source.isStateBoundary()) {
                outgoing.get(connection.sourceNode()).add(connection.targetNode());
                indegree.computeIfPresent(connection.targetNode(), (id, degree) -> degree + 1);
            }
        }
        ArrayDeque<UUID> ready = new ArrayDeque<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        Set<UUID> visited = new HashSet<>();
        while (!ready.isEmpty()) {
            UUID current = ready.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            WNode source = getNode(current);
            for (UUID targetId : outgoing.getOrDefault(current, List.of())) {
                WNode target = getNode(targetId);
                if (source != null && target != null) {
                    target.setTopoDepth(Math.max(target.getTopoDepth(), source.getTopoDepth() + 1));
                }
                int next = indegree.computeIfPresent(targetId, (id, degree) -> degree - 1);
                if (next == 0) {
                    ready.addLast(targetId);
                }
            }
        }
        for (WNode node : nodes) {
            if (!visited.contains(node.getId())) {
                node.setTopoDepth(Integer.MAX_VALUE);
            }
        }
        connectionGeometryRevision++;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag nodeTags = new ListTag();
        for (WNode node : nodes) {
            nodeTags.add(node.save());
        }
        root.put("nodes", nodeTags);
        ListTag connectionTags = new ListTag();
        for (WConnection connection : connections) {
            CompoundTag tag = new CompoundTag();
            tag.putString("src", connection.sourceNode().toString());
            tag.putInt("srcP", connection.sourcePin());
            tag.putString("tgt", connection.targetNode().toString());
            tag.putInt("tgtP", connection.targetPin());
            if (connection.sourcePortKey() != null) {
                tag.putString("srcKey", connection.sourcePortKey());
            }
            if (connection.targetPortKey() != null) {
                tag.putString("tgtKey", connection.targetPortKey());
            }
            ListTag waypoints = new ListTag();
            for (int i = 0; i < connection.waypointXs().length; i++) {
                CompoundTag waypoint = new CompoundTag();
                waypoint.putInt("x", connection.waypointXs()[i]);
                waypoint.putInt("y", connection.waypointYs()[i]);
                waypoints.add(waypoint);
            }
            tag.put("waypoints", waypoints);
            connectionTags.add(tag);
        }
        root.put("connections", connectionTags);
        return root;
    }

    public void load(CompoundTag root) {
        for (WNode node : nodes) {
            node.bindOwningGraph(null);
        }
        nodes.clear();
        nodeIndex.clear();
        connections.clear();
        ListTag nodeTags = root.getList("nodes", 10);
        for (int i = 0; i < nodeTags.size(); i++) {
            CompoundTag tag = nodeTags.getCompound(i);
            UUID id;
            try {
                id = UUID.fromString(tag.getString("id"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            WNode node = nodeArchive.get(id);
            if (node == null) {
                continue;
            }
            node.load(tag);
            node.bindOwningGraph(this);
            nodes.add(node);
            nodeIndex.put(id, node);
        }
        ListTag connectionTags = root.getList("connections", 10);
        for (int i = 0; i < connectionTags.size(); i++) {
            CompoundTag tag = connectionTags.getCompound(i);
            try {
                ListTag waypoints = tag.getList("waypoints", 10);
                int[] xs = new int[waypoints.size()];
                int[] ys = new int[waypoints.size()];
                for (int waypoint = 0; waypoint < waypoints.size(); waypoint++) {
                    xs[waypoint] = waypoints.getCompound(waypoint).getInt("x");
                    ys[waypoint] = waypoints.getCompound(waypoint).getInt("y");
                }
                WConnection connection = new WConnection(
                        UUID.fromString(tag.getString("src")),
                        tag.getInt("srcP"),
                        UUID.fromString(tag.getString("tgt")),
                        tag.getInt("tgtP"),
                        xs,
                        ys,
                        tag.contains("srcKey") ? tag.getString("srcKey") : null,
                        tag.contains("tgtKey") ? tag.getString("tgtKey") : null);
                if (isValidConnection(connection)) {
                    connections.add(connection);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        resolveStablePorts();
        updateTopology();
    }

    private void resolveStablePorts() {
        for (WConnection connection : connections) {
            WNode source = getNode(connection.sourceNode());
            WNode target = getNode(connection.targetNode());
            int sourcePin = portIndex(source == null ? List.of() : source.getOutputs(), connection.sourcePortKey());
            int targetPin = portIndex(target == null ? List.of() : target.getInputs(), connection.targetPortKey());
            connection.resolvePins(
                    sourcePin >= 0 ? sourcePin : connection.sourcePin(),
                    targetPin >= 0 ? targetPin : connection.targetPin());
        }
    }

    private boolean isValidConnection(WConnection connection) {
        WNode source = getNode(connection.sourceNode());
        WNode target = getNode(connection.targetNode());
        return source != null
                && target != null
                && connection.sourcePin() >= 0
                && connection.sourcePin() < source.getOutputs().size()
                && connection.targetPin() >= 0
                && connection.targetPin() < target.getInputs().size()
                && source.getOutputs().get(connection.sourcePin()).getDataType()
                        == target.getInputs().get(connection.targetPin()).getDataType();
    }

    private void refreshConnectedPins() {
        for (WNode node : nodes) {
            node.getInputs().forEach(pin -> pin.setConnected(false));
            node.getOutputs().forEach(pin -> pin.setConnected(false));
        }
        for (WConnection connection : connections) {
            WNode source = getNode(connection.sourceNode());
            WNode target = getNode(connection.targetNode());
            if (source != null && target != null) {
                source.getOutputs().get(connection.sourcePin()).setConnected(true);
                target.getInputs().get(connection.targetPin()).setConnected(true);
            }
        }
    }

    private static int portIndex(List<WPin> pins, String stableKey) {
        if (stableKey == null) {
            return -1;
        }
        for (int i = 0; i < pins.size(); i++) {
            if (stableKey.equals(pins.get(i).getStableKey())) {
                return i;
            }
        }
        return -1;
    }
}
