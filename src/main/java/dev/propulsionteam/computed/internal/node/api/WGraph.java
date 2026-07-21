package dev.propulsionteam.computed.internal.node.api;

import dev.propulsionteam.computed.internal.node.MissingNode;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The core data structure for the node system.
 * A WGraph manages a collection of nodes and the connections between them.
 * It is responsible for logical updates (ticking) and data flow propagation.
 */
public class WGraph {

    public enum DiagnosticSeverity { WARNING, ERROR }

    /** Runtime/editor diagnostic that never removes the source graph data. */
    public record GraphDiagnostic(
            DiagnosticSeverity severity, String code, String message, Set<UUID> nodeIds) {}

    /** Node type id for the graph tick driver (menu "Tick"). */
    public static final ResourceLocation TICK_NODE_TYPE =
            ResourceLocation.fromNamespaceAndPath("computed", "tick");

    /** Maximum updates per second for the tick node's Rate slider (matches default Minecraft TPS). */
    public static final int MAX_TICK_RATE = 20;

    private final List<WNode> nodes = new ArrayList<>();
    private final List<WConnection> connections = new ArrayList<>();
    private final List<WSection> sections = new ArrayList<>();
    /** UUID→node lookup; kept in sync with {@link #nodes} to avoid O(n) stream scans in hot render paths. */
    private final Map<UUID, WNode> nodeIndex = new HashMap<>();
    private boolean pinSchemaRefreshPending;
    private long connectionGeometryRevision;
    private final Map<UUID, List<WConnection>> outgoingConnections = new HashMap<>();
    private final Map<UUID, List<WConnection>> incomingConnections = new HashMap<>();
    private final Set<UUID> forcedDirtySources = new HashSet<>();
    private final Set<UUID> disabledNodeIds = new HashSet<>();
    private final List<GraphDiagnostic> diagnostics = new ArrayList<>();
    private final List<WNode> evaluationOrder = new ArrayList<>();
    private boolean forceFullWorldStep = true;

    /** O(1) node lookup by id. Returns null if not present. */
    public WNode getNode(UUID id) {
        return id == null ? null : nodeIndex.get(id);
    }

    /** Grouping rectangle shown in the editor. */
    public static class WSection {
        /** Default body fill (ARGB) matching the original editor theme. */
        public static final int DEFAULT_BODY_COLOR_ARGB = 0x221F2A40;

        private UUID id;
        private String name;
        private int x;
        private int y;
        private int width;
        private int height;
        /** Editor-only: section background tint (ARGB). */
        private int bodyColorArgb = DEFAULT_BODY_COLOR_ARGB;
        /**
         * Draw / hit-test order for nested sections: 0 = root band, larger = more nested (drawn on top,
         * receives header clicks first).
         */
        private int layer;

        public WSection(String name, int x, int y, int width, int height) {
            this.id = UUID.randomUUID();
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public UUID getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public void setPos(int x, int y) { this.x = x; this.y = y; }
        public void setSize(int width, int height) { this.width = width; this.height = height; }

        public int getBodyColorArgb() {
            return bodyColorArgb;
        }

        public void setBodyColorArgb(int argb) {
            this.bodyColorArgb = argb;
        }

        public int getLayer() {
            return layer;
        }

        public void setLayer(int layer) {
            this.layer = Math.max(0, layer);
        }

        private net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", id.toString());
            tag.putString("name", name);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("w", width);
            tag.putInt("h", height);
            tag.putInt("bodyArgb", bodyColorArgb);
            tag.putInt("layer", layer);
            return tag;
        }

        public net.minecraft.nbt.CompoundTag toNbt() {
            return save();
        }

        public static WSection fromNbt(net.minecraft.nbt.CompoundTag tag) {
            return load(tag);
        }

        private static WSection load(net.minecraft.nbt.CompoundTag tag) {
            WSection s = new WSection(
                    tag.getString("name"),
                    tag.getInt("x"),
                    tag.getInt("y"),
                    Math.max(24, tag.getInt("w")),
                    Math.max(24, tag.getInt("h")));
            if (tag.contains("id")) {
                s.id = UUID.fromString(tag.getString("id"));
            }
            if (tag.contains("bodyArgb")) {
                s.bodyColorArgb = tag.getInt("bodyArgb");
            }
            if (tag.contains("layer")) {
                s.layer = Math.max(0, tag.getInt("layer"));
            }
            return s;
        }
    }

    /** Seconds accumulated toward the next pulse, per tick-node id. */
    private final Map<UUID, double[]> tickAccumSec = new HashMap<>();

    /**
     * Increments once after each full {@link #stepConnectionsAndEval(boolean)} (root graph world ticks,
     * {@link #advanceSimulation(double)} steps, and each {@link #propagateAndEvaluate()} for nested graphs).
     * Nodes can use it to emit at most once per logical graph step.
     */
    private int simulationStepCounter = 0;

    /**
     * While nodes evaluate after wire propagation: whether this pass counts as a tick-node pulse (matches Tick
     * output high) or, with no tick driver, is always true for that pass.
     */
    private boolean evalTickPulseGate;

    /**
     * Adds a new node to the graph and recalculates the topological structure.
     * @param node The node instance to add.
     */
    public void addNode(WNode node) {
        nodes.add(node);
        nodeIndex.put(node.getId(), node);
        node.bindOwningGraph(this);
        dedupeFunctionBoundaryNodes();
        pruneDanglingConnections();
        updateTopology();
    }

    /**
     * Removes a node and all its associated connections from the graph.
     * @param node The node to remove.
     */
    public void removeNode(WNode node) {
        nodes.remove(node);
        nodeIndex.remove(node.getId());
        node.bindOwningGraph(null);
        connections.removeIf(c -> c.sourceNode().equals(node.getId()) || c.targetNode().equals(node.getId()));
        updateTopology();
    }

    /**
     * Serializes the entire graph state into a NBT CompoundTag.
     * @return A tag containing all nodes, their internal data, and connections.
     */
    public net.minecraft.nbt.CompoundTag save() {
        refreshStableConnectionPins();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        
        net.minecraft.nbt.ListTag nodesTag = new net.minecraft.nbt.ListTag();
        for (WNode node : nodes) nodesTag.add(node.save());
        tag.put("nodes", nodesTag);
        
        net.minecraft.nbt.ListTag connsTag = new net.minecraft.nbt.ListTag();
        for (WConnection conn : connections) {
            net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
            c.putString("src", conn.sourceNode().toString());
            c.putInt("srcP", conn.sourcePin());
            c.putString("tgt", conn.targetNode().toString());
            c.putInt("tgtP", conn.targetPin());
            WNode source = nodeIndex.get(conn.sourceNode());
            WNode target = nodeIndex.get(conn.targetNode());
            String sourcePort = conn.sourcePortKey();
            if (sourcePort == null && source != null && conn.sourcePin() >= 0 && conn.sourcePin() < source.getOutputs().size()) {
                sourcePort = stablePortId(source.getOutputs(), conn.sourcePin(), "output");
            }
            if (sourcePort != null) c.putString("sourcePort", sourcePort);
            String targetPort = conn.targetPortKey();
            if (targetPort == null && target != null && conn.targetPin() >= 0 && conn.targetPin() < target.getInputs().size()) {
                targetPort = stablePortId(target.getInputs(), conn.targetPin(), "input");
            }
            if (targetPort != null) c.putString("targetPort", targetPort);
            if (conn.waypointXs().length > 0) {
                net.minecraft.nbt.ListTag wps = new net.minecraft.nbt.ListTag();
                for (int j = 0; j < conn.waypointXs().length; j++) {
                    net.minecraft.nbt.CompoundTag w = new net.minecraft.nbt.CompoundTag();
                    w.putInt("x", conn.waypointXs()[j]);
                    w.putInt("y", conn.waypointYs()[j]);
                    wps.add(w);
                }
                c.put("wps", wps);
            }
            connsTag.add(c);
        }
        tag.put("conns", connsTag);

        net.minecraft.nbt.ListTag sectionsTag = new net.minecraft.nbt.ListTag();
        for (WSection s : sections) {
            sectionsTag.add(s.save());
        }
        tag.put("sections", sectionsTag);
        
        return tag;
    }

    private static String stablePortId(List<WPin> pins, int index, String direction) {
        return WNode.stablePortId(pins, index, direction);
    }

    /**
     * Reconstructs the graph state from a NBT CompoundTag.
     * @param tag The tag containing serialized graph data.
     */
    public void load(net.minecraft.nbt.CompoundTag tag) {
        nodes.clear();
        nodeIndex.clear();
        pinSchemaRefreshPending = false;
        connections.clear();
        sections.clear();
        
        net.minecraft.nbt.ListTag nodesTag = tag.getList("nodes", 10);
        for (int i = 0; i < nodesTag.size(); i++) {
            net.minecraft.nbt.CompoundTag nTag = nodesTag.getCompound(i);
            net.minecraft.resources.ResourceLocation type = net.minecraft.resources.ResourceLocation.parse(nTag.getString("typeId"));
            WNode node = NodeRegistry.createNode(type, nTag.getInt("x"), nTag.getInt("y"));
            if (node == null) node = MissingNode.fromLegacyTag(type, nTag);
            node.load(nTag);
            nodes.add(node);
            nodeIndex.put(node.getId(), node);
            node.bindOwningGraph(this);
        }
        
        net.minecraft.nbt.ListTag connsTag = tag.getList("conns", 10);
        for (int i = 0; i < connsTag.size(); i++) {
            net.minecraft.nbt.CompoundTag c = connsTag.getCompound(i);
            java.util.UUID src = java.util.UUID.fromString(c.getString("src"));
            int sp = c.getInt("srcP");
            java.util.UUID tgt = java.util.UUID.fromString(c.getString("tgt"));
            int tp = c.getInt("tgtP");
            String sourcePort = c.contains("sourcePort") ? c.getString("sourcePort") : null;
            String targetPort = c.contains("targetPort") ? c.getString("targetPort") : null;
            if (c.contains("sourcePort")) {
                sp = stablePortIndex(nodeIndex.get(src), true, c.getString("sourcePort"), sp);
            }
            if (c.contains("targetPort")) {
                tp = stablePortIndex(nodeIndex.get(tgt), false, c.getString("targetPort"), tp);
            }
            if (c.contains("wps")) {
                net.minecraft.nbt.ListTag wps = c.getList("wps", 10);
                int[] wx = new int[wps.size()];
                int[] wy = new int[wps.size()];
                for (int j = 0; j < wps.size(); j++) {
                    net.minecraft.nbt.CompoundTag w = wps.getCompound(j);
                    wx[j] = w.getInt("x");
                    wy[j] = w.getInt("y");
                }
                connections.add(new WConnection(src, sp, tgt, tp, wx, wy, sourcePort, targetPort));
            } else {
                connections.add(new WConnection(src, sp, tgt, tp, null, null, sourcePort, targetPort));
            }
        }
        net.minecraft.nbt.ListTag sectionsTag = tag.getList("sections", 10);
        for (int i = 0; i < sectionsTag.size(); i++) {
            sections.add(WSection.load(sectionsTag.getCompound(i)));
        }
        dedupeFunctionBoundaryNodes();
        pruneDanglingConnections();
        tickAccumSec.clear();
        simulationStepCounter = 0;
        updateTopology();
    }

    private static int stablePortIndex(WNode node, boolean output, String key, int fallback) {
        if (node == null || key == null || key.isBlank()) return fallback;
        List<WPin> pins = output ? node.getOutputs() : node.getInputs();
        String direction = output ? "output" : "input";
        for (int i = 0; i < pins.size(); i++) {
            if (key.equals(stablePortId(pins, i, direction))) return i;
        }
        return fallback;
    }

    private static int stablePortIndex(WNode node, boolean output, String key) {
        return stablePortIndex(node, output, key, -1);
    }

    /** Remaps positional caches only after a node reports a schema generation change. */
    private void refreshStableConnectionPins() {
        if (!pinSchemaRefreshPending) return;
        pinSchemaRefreshPending = false;
        for (WConnection connection : connections) {
            int sourcePin = connection.sourcePortKey() == null
                    ? connection.sourcePin()
                    : stablePortIndex(nodeIndex.get(connection.sourceNode()), true, connection.sourcePortKey());
            int targetPin = connection.targetPortKey() == null
                    ? connection.targetPin()
                    : stablePortIndex(nodeIndex.get(connection.targetNode()), false, connection.targetPortKey());
            connection.resolvePins(sourcePin, targetPin);
        }
        rebuildConnectionIndexes();
        analyzeCombinationalCycles();
        rebuildEvaluationOrder();
        forceFullWorldStep = true;
        connectionGeometryRevision++;
    }

    void onNodePinSchemaChanged(WNode node) {
        if (node != null && nodeIndex.get(node.getId()) == node) pinSchemaRefreshPending = true;
    }

    public long getConnectionGeometryRevision() {
        refreshStableConnectionPins();
        return connectionGeometryRevision;
    }

    /**
     * Function inner graphs must have at most one {@link FunctionStartNode} and one {@link FunctionEndNode}.
     * Keeps the first of each in list order and removes extras (and connections touching them).
     */
    private void dedupeFunctionBoundaryNodes() {
        boolean haveStart = false;
        boolean haveEnd = false;
        List<WNode> extras = new ArrayList<>();
        for (WNode n : nodes) {
            if (n instanceof FunctionStartNode) {
                if (haveStart) {
                    extras.add(n);
                } else {
                    haveStart = true;
                }
            } else if (n instanceof FunctionEndNode) {
                if (haveEnd) {
                    extras.add(n);
                } else {
                    haveEnd = true;
                }
            }
        }
        if (extras.isEmpty()) {
            return;
        }
        Set<UUID> extraIds = new HashSet<>();
        for (WNode n : extras) {
            extraIds.add(n.getId());
            nodeIndex.remove(n.getId());
            n.bindOwningGraph(null);
        }
        connections.removeIf(
                c -> extraIds.contains(c.sourceNode()) || extraIds.contains(c.targetNode()));
        nodes.removeAll(extras);
    }

    /** Removes connections whose endpoints are not present (e.g. skipped nodes while loading). */
    private void pruneDanglingConnections() {
        if (connections.isEmpty()) {
            return;
        }
        Set<UUID> ids = new HashSet<>();
        for (WNode n : nodes) {
            ids.add(n.getId());
        }
        connections.removeIf(c -> !ids.contains(c.sourceNode()) || !ids.contains(c.targetNode()));
    }

    /** Returns true when the candidate closes a same-step dependency cycle. */
    public boolean wouldIntroduceCombinationalCycle(WConnection candidate) {
        WNode source = nodeIndex.get(candidate.sourceNode());
        WNode target = nodeIndex.get(candidate.targetNode());
        if (source == null || target == null || source.isStateBoundary()) {
            return false;
        }
        if (source.getId().equals(target.getId())) {
            return true;
        }
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(target.getId());
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(source.getId())) {
                return true;
            }
            WNode currentNode = nodeIndex.get(current);
            if (currentNode != null && currentNode.isStateBoundary()) {
                continue;
            }
            for (WConnection connection : connections) {
                if (!connection.sourceNode().equals(current)) {
                    continue;
                }
                WNode next = nodeIndex.get(connection.targetNode());
                if (next != null) {
                    pending.addLast(next.getId());
                }
            }
        }
        return false;
    }

    /**
     * Establishes a connection between an output pin of a source node and an input pin of a target node.
     * @param sourceNode UUID of the source node.
     * @param sourcePin Index of the output pin.
     * @param targetNode UUID of the target node.
     * @param targetPin Index of the input pin.
     */
    public boolean connect(UUID sourceNode, int sourcePin, UUID targetNode, int targetPin) {
        return connect(WConnection.withoutWaypoints(sourceNode, sourcePin, targetNode, targetPin));
    }

    /** Like {@link #connect(UUID, int, UUID, int)} but preserves editor spline waypoints (paste, tools). */
    public boolean connect(WConnection connection) {
        connection = withStablePortKeys(connection);
        if (wouldIntroduceCombinationalCycle(connection)) {
            diagnostics.add(new GraphDiagnostic(
                    DiagnosticSeverity.ERROR,
                    "computed.cycle.rejected",
                    "Connection rejected: combinational cycles require a state or delay node",
                    Set.of(connection.sourceNode(), connection.targetNode())));
            return false;
        }
        connections.add(connection);
        updateTopology();
        return true;
    }

    private WConnection withStablePortKeys(WConnection connection) {
        String sourceKey = connection.sourcePortKey();
        String targetKey = connection.targetPortKey();
        WNode source = nodeIndex.get(connection.sourceNode());
        WNode target = nodeIndex.get(connection.targetNode());
        if (sourceKey == null && source != null && connection.sourcePin() >= 0
                && connection.sourcePin() < source.getOutputs().size()) {
            sourceKey = stablePortId(source.getOutputs(), connection.sourcePin(), "output");
        }
        if (targetKey == null && target != null && connection.targetPin() >= 0
                && connection.targetPin() < target.getInputs().size()) {
            targetKey = stablePortId(target.getInputs(), connection.targetPin(), "input");
        }
        return connection.withStablePorts(sourceKey, targetKey);
    }

    private boolean isConnectionUsable(WConnection connection) {
        WNode source = nodeIndex.get(connection.sourceNode());
        WNode target = nodeIndex.get(connection.targetNode());
        if (source == null
                || target == null
                || connection.sourcePin() < 0
                || connection.sourcePin() >= source.getOutputs().size()
                || connection.targetPin() < 0
                || connection.targetPin() >= target.getInputs().size()) {
            return false;
        }
        WPin.DataType sourceType = source.getOutputs().get(connection.sourcePin()).getDataType();
        WPin.DataType targetType = target.getInputs().get(connection.targetPin()).getDataType();
        return sourceType == targetType || (sourceType == WPin.DataType.NUMBER && targetType == WPin.DataType.STRING);
    }

    /**
     * Moves editor spline control points for every connection whose source or target is in {@code nodeIds}.
     * Call with incremental {@code dx}/{@code dy} while dragging those nodes (selection, section bundle, etc.).
     */
    public void shiftWaypointsForConnectionsTouching(Collection<UUID> nodeIds, int dx, int dy) {
        if (nodeIds == null || nodeIds.isEmpty() || (dx == 0 && dy == 0)) {
            return;
        }
        for (int i = 0; i < connections.size(); i++) {
            WConnection c = connections.get(i);
            if (!nodeIds.contains(c.sourceNode()) && !nodeIds.contains(c.targetNode())) {
                continue;
            }
            if (c.waypointXs().length == 0) {
                continue;
            }
            int[] nxs = java.util.Arrays.copyOf(c.waypointXs(), c.waypointXs().length);
            int[] nys = java.util.Arrays.copyOf(c.waypointYs(), c.waypointYs().length);
            for (int j = 0; j < nxs.length; j++) {
                nxs[j] += dx;
                nys[j] += dy;
            }
            connections.set(i, c.withWaypoints(nxs, nys));
        }
    }

    /**
     * Removes every connection that touches any of the given node ids (inputs and outputs).
     */
    public void disconnectNodes(Collection<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        connections.removeIf(
                c -> nodeIds.contains(c.sourceNode()) || nodeIds.contains(c.targetNode()));
        updateTopology();
    }

    /**
     * @return An unmodifiable view of all nodes currently in the graph.
     */
    public List<WNode> getNodes() {
        return nodes;
    }

    public List<GraphDiagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    public boolean isNodeExecutionDisabled(UUID nodeId) {
        return disabledNodeIds.contains(nodeId);
    }

    public List<WSection> getSections() {
        return sections;
    }

    public int getSimulationStepCounter() {
        return simulationStepCounter;
    }

    /** Executes one detached node with graph-step services for the public API compatibility adapter. */
    static void evaluateIsolated(WNode node, long graphStep, boolean tickPulseGate) {
        WGraph scope = new WGraph();
        scope.simulationStepCounter = (int) graphStep;
        scope.evalTickPulseGate = tickPulseGate;
        node.bindEvaluationGraph(scope);
        try {
            node.evaluate();
        } finally {
            node.bindEvaluationGraph(null);
            scope.evalTickPulseGate = false;
        }
    }

    /**
     * While a node {@link WNode#evaluate()} runs inside this graph, returns whether this step is a tick pulse
     * (same instants as the Tick node's output) or always true when the graph has no tick driver.
     */
    public boolean isEvalTickPulseGate() {
        return evalTickPulseGate;
    }

    /** True if this graph contains a {@link #TICK_NODE_TYPE} node (stepped simulation). */
    public boolean usesTickDriver() {
        for (WNode n : nodes) {
            if (TICK_NODE_TYPE.equals(n.getTypeId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Advances simulation by {@code deltaSeconds} of wall time.
     * <ul>
     *   <li>With no tick driver: propagates wires and evaluates all nodes every call (editor "live" mode).
     *   <li>With a tick driver: only propagates and evaluates on a pulse; tick nodes set outputs every call.
     * </ul>
     */
    public void advanceSimulation(double deltaSeconds) {
        if (deltaSeconds <= 0) {
            deltaSeconds = 1.0e-4;
        }
        if (!usesTickDriver()) {
            stepConnectionsAndEval(true);
            simulationStepCounter++;
            return;
        }
        boolean pulse = prepareTickDrivers(deltaSeconds);
        if (pulse) {
            stepConnectionsAndEval(true);
            simulationStepCounter++;
        }
    }

    /**
     * Block/world execution: source nodes are polled every game tick, then only the downstream graph section
     * whose driver changed (or emitted an active tick/event pulse) is propagated and evaluated.
     */
    public void advanceSimulationInWorld(double deltaSeconds) {
        if (deltaSeconds <= 0) {
            deltaSeconds = 1.0e-4;
        }
        boolean tickPulse = true;
        if (usesTickDriver()) {
            Map<UUID, PinSnapshot[]> tickDriverSnapshots = snapshotTickDrivers();
            tickPulse = prepareTickDrivers(deltaSeconds);
            markChangedTickDrivers(tickDriverSnapshots);
        } else {
            forcedDirtySources.clear();
        }
        if (forceFullWorldStep) {
            stepConnectionsAndEval(tickPulse);
            forceFullWorldStep = false;
        } else {
            stepSparseConnectionsAndEval(tickPulse);
        }
        simulationStepCounter++;
    }

    /**
     * @deprecated Use {@link #advanceSimulation(double)} with an appropriate delta time.
     */
    @Deprecated
    public void tick() {
        advanceSimulation(1.0 / MAX_TICK_RATE);
    }

    private boolean prepareTickDrivers(double dt) {
        boolean anyPulse = false;
        tickAccumSec.keySet().removeIf(id -> findNode(id) == null);
        for (WNode n : nodes) {
            if (!TICK_NODE_TYPE.equals(n.getTypeId())) {
                continue;
            }
            if (n.getOutputs().size() < 2) {
                continue;
            }
            UUID id = n.getId();
            double[] acc = tickAccumSec.computeIfAbsent(id, k -> new double[1]);
            acc[0] += dt;
            double rate = readTickRateSlider(n);
            boolean pulse = false;
            if (rate > 1e-9) {
                double period = 1.0 / rate;
                if (acc[0] >= period) {
                    pulse = true;
                    n.getOutputs().get(1).setValue(acc[0]);
                    acc[0] = 0.0;
                }
            }
            n.getOutputs().get(0).setValue(pulse ? 1.0 : 0.0);
            if (!pulse) {
                n.getOutputs().get(1).setValue(acc[0]);
            }
            anyPulse |= pulse;
        }
        return anyPulse;
    }

    private static double readTickRateSlider(WNode n) {
        for (var el : n.getElements()) {
            if (el instanceof WSlider s) {
                return Mth.clamp(s.getValue(), 0.0, MAX_TICK_RATE);
            }
        }
        return MAX_TICK_RATE;
    }

    /**
     * Single propagation + evaluation pass (used by nested {@link FunctionCardNode} bodies and live preview).
     * Increments {@link #getSimulationStepCounter()} afterward so nested graphs advance a logical step counter
     * every time the inner graph runs.
     */
    public void propagateAndEvaluate() {
        stepConnectionsAndEval(true);
        simulationStepCounter++;
    }

    /** One logical step: propagate all connections, then evaluate every node. */
    private void stepConnectionsAndEval(boolean tickPulseGate) {
        refreshStableConnectionPins();
        evalTickPulseGate = tickPulseGate;
        try {
            // Seed inputs from the previous committed outputs. Evaluation then walks the compiled DAG and
            // immediately forwards each new output, so pure chains settle in one deterministic pass.
            propagateConnections();
            for (WNode node : evaluationOrder) {
                evaluateNode(node);
                if (!node.isStateBoundary()) {
                    propagateOutgoingValues(node);
                }
            }
        } finally {
            evalTickPulseGate = false;
        }
    }

    private void stepSparseConnectionsAndEval(boolean tickPulseGate) {
        refreshStableConnectionPins();
        evalTickPulseGate = tickPulseGate;
        ArrayDeque<WNode> queue = new ArrayDeque<>();
        Set<UUID> queued = new HashSet<>();
        try {
            // Publish every state boundary's previously committed outputs before evaluating any pure node.
            for (WNode node : evaluationOrder) {
                if (!node.isStateBoundary()) continue;
                if (disabledNodeIds.contains(node.getId())) resetOutputs(node);
                propagateOutgoing(node, queue, queued);
            }
            for (WNode node : evaluationOrder) {
                if (node.isStateBoundary()) continue;
                List<WConnection> incoming = incomingConnections.get(node.getId());
                boolean always = node.executionPolicy()
                        != dev.propulsionteam.computed.api.node.ExecutionPolicy.INPUT_DRIVEN;
                if (!always && incoming != null && !incoming.isEmpty()) {
                    continue;
                }
                if (queued.remove(node.getId())) queue.remove(node);
                PinSnapshot[] before = snapshotOutputs(node);
                evaluateNode(node);
                if (outputsChanged(node, before)
                        || hasActivePulseOutput(node)
                        || forcedDirtySources.contains(node.getId())) {
                    propagateOutgoing(node, queue, queued);
                }
            }

            int remaining = Math.max(1, nodes.size() * Math.max(1, connections.size() + 1));
            while (!queue.isEmpty() && remaining-- > 0) {
                WNode node = queue.removeFirst();
                queued.remove(node.getId());
                if (node.isStateBoundary()) continue;
                PinSnapshot[] before = snapshotOutputs(node);
                evaluateNode(node);
                if (outputsChanged(node, before) || hasActivePulseOutput(node)) {
                    propagateOutgoing(node, queue, queued);
                }
            }
            // Inputs are now settled. Compute and commit next state, but do not publish it until the next step.
            for (WNode node : evaluationOrder) {
                if (node.isStateBoundary()) evaluateNode(node);
            }
        } finally {
            evalTickPulseGate = false;
        }
    }

    private void evaluateNode(WNode node) {
        if (disabledNodeIds.contains(node.getId())) {
            resetOutputs(node);
            return;
        }
        node.bindEvaluationGraph(this);
        try {
            node.evaluate();
        } finally {
            node.bindEvaluationGraph(null);
        }
    }

    private static void resetOutputs(WNode node) {
        for (WPin output : node.getOutputs()) {
            output.setConnected(false);
            switch (output.getDataType()) {
                case NUMBER -> output.setValue(0.0);
                case STRING -> output.setStringValue("");
                case WIDGET -> output.setWidgetValue(null);
            }
        }
    }

    private Map<UUID, PinSnapshot[]> snapshotTickDrivers() {
        Map<UUID, PinSnapshot[]> snapshots = new HashMap<>();
        for (WNode node : nodes) {
            if (TICK_NODE_TYPE.equals(node.getTypeId())) {
                snapshots.put(node.getId(), snapshotOutputs(node));
            }
        }
        return snapshots;
    }

    private void markChangedTickDrivers(Map<UUID, PinSnapshot[]> before) {
        forcedDirtySources.clear();
        for (WNode node : nodes) {
            if (!TICK_NODE_TYPE.equals(node.getTypeId())) {
                continue;
            }
            PinSnapshot[] snapshot = before.get(node.getId());
            if (snapshot == null || outputsChanged(node, snapshot) || hasActivePulseOutput(node)) {
                forcedDirtySources.add(node.getId());
            }
        }
    }

    private void propagateOutgoing(WNode source, ArrayDeque<WNode> queue, Set<UUID> queued) {
        List<WConnection> outgoing = outgoingConnections.get(source.getId());
        if (outgoing == null || outgoing.isEmpty()) {
            return;
        }
        for (WConnection conn : outgoing) {
            WNode target = nodeIndex.get(conn.targetNode());
            if (target == null || !copyConnectionValue(source, target, conn)) {
                continue;
            }
            if (target.isStateBoundary()) {
                // The new input is committed for the next graph step; state boundaries run once per step.
                continue;
            }
            if (queued.add(target.getId())) {
                queue.addLast(target);
            }
        }
    }

    private void propagateOutgoingValues(WNode source) {
        List<WConnection> outgoing = outgoingConnections.get(source.getId());
        if (outgoing == null) {
            return;
        }
        for (WConnection connection : outgoing) {
            WNode target = nodeIndex.get(connection.targetNode());
            if (target != null && !disabledNodeIds.contains(target.getId())) {
                copyConnectionValue(source, target, connection);
            }
        }
    }

    private boolean copyConnectionValue(WNode source, WNode target, WConnection conn) {
        int sp = conn.sourcePin();
        int tp = conn.targetPin();
        if (sp < 0
                || sp >= source.getOutputs().size()
                || tp < 0
                || tp >= target.getInputs().size()) {
            return false;
        }
        WPin srcPin = source.getOutputs().get(sp);
        WPin tgtPin = target.getInputs().get(tp);
        if (srcPin.getDataType() != tgtPin.getDataType()) {
            if (srcPin.getDataType() == WPin.DataType.NUMBER
                    && tgtPin.getDataType() == WPin.DataType.STRING) {
                tgtPin.setStringValue(formatNumberForString(srcPin.getValue()));
                tgtPin.setConnected(true);
                srcPin.setConnected(true);
                return true;
            }
            return false;
        }
        switch (srcPin.getDataType()) {
            case NUMBER -> tgtPin.setValue(srcPin.getValue());
            case STRING -> tgtPin.setStringValue(srcPin.getStringValue());
            case WIDGET -> tgtPin.setWidgetValue(srcPin.getWidgetValue());
        }
        tgtPin.setConnected(true);
        srcPin.setConnected(true);
        return true;
    }

    private static boolean hasActivePulseOutput(WNode node) {
        for (WPin pin : node.getOutputs()) {
            if (pin.getDataType() != WPin.DataType.NUMBER || pin.getValue() <= 0.5) {
                continue;
            }
            String name = pin.getName();
            if ("Tick".equalsIgnoreCase(name) || "Event".equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static PinSnapshot[] snapshotOutputs(WNode node) {
        PinSnapshot[] out = new PinSnapshot[node.getOutputs().size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = PinSnapshot.capture(node.getOutputs().get(i));
        }
        return out;
    }

    private static boolean outputsChanged(WNode node, PinSnapshot[] before) {
        if (before.length != node.getOutputs().size()) {
            return true;
        }
        for (int i = 0; i < before.length; i++) {
            if (!before[i].matches(node.getOutputs().get(i))) {
                return true;
            }
        }
        return false;
    }

    private record PinSnapshot(WPin.DataType type, double numberValue, String stringValue, Object widgetValue) {
        static PinSnapshot capture(WPin pin) {
            return new PinSnapshot(pin.getDataType(), pin.getValue(), pin.getStringValue(), pin.getWidgetValue());
        }

        boolean matches(WPin pin) {
            if (pin.getDataType() != type) {
                return false;
            }
            return switch (type) {
                case NUMBER -> Double.compare(numberValue, pin.getValue()) == 0;
                case STRING -> stringValue.equals(pin.getStringValue());
                case WIDGET -> widgetValue == pin.getWidgetValue();
            };
        }
    }

    private static String formatNumberForString(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0e-9 && Math.abs(v) < 1e15) {
            return Long.toString((long) Math.rint(v));
        }
        return Double.toString(v);
    }

    private void propagateConnections() {
        for (WNode node : nodes) {
            for (WPin pin : node.getInputs()) {
                pin.setConnected(false);
                switch (pin.getDataType()) {
                    case NUMBER -> pin.setValue(0.0);
                    case STRING -> pin.setStringValue("");
                    case WIDGET -> pin.setWidgetValue(null);
                }
            }
            for (WPin pin : node.getOutputs()) {
                pin.setConnected(false);
            }
            if (disabledNodeIds.contains(node.getId())) {
                resetOutputs(node);
            }
        }
        for (WConnection conn : connections) {
            WNode source = nodeIndex.get(conn.sourceNode());
            WNode target = nodeIndex.get(conn.targetNode());
            if (source == null || target == null) {
                continue;
            }
            int sp = conn.sourcePin();
            int tp = conn.targetPin();
            if (sp < 0
                    || sp >= source.getOutputs().size()
                    || tp < 0
                    || tp >= target.getInputs().size()) {
                continue;
            }
            WPin srcPin = source.getOutputs().get(sp);
            WPin tgtPin = target.getInputs().get(tp);
            if (srcPin.getDataType() != tgtPin.getDataType()) {
                if (srcPin.getDataType() == WPin.DataType.NUMBER
                        && tgtPin.getDataType() == WPin.DataType.STRING) {
                    tgtPin.setStringValue(formatNumberForString(srcPin.getValue()));
                    tgtPin.setConnected(true);
                    srcPin.setConnected(true);
                }
                continue;
            }
            switch (srcPin.getDataType()) {
                case NUMBER -> tgtPin.setValue(srcPin.getValue());
                case STRING -> tgtPin.setStringValue(srcPin.getStringValue());
                case WIDGET -> tgtPin.setWidgetValue(srcPin.getWidgetValue());
            }
            tgtPin.setConnected(true);
            srcPin.setConnected(true);
        }
    }

    /**
     * Internal helper to find a node by its unique identifier.
     * @param id UUID of the node.
     * @return The node instance or null if not found.
     */
    private WNode findNode(UUID id) {
        return id == null ? null : nodeIndex.get(id);
    }

    /**
     * Updates the topological structure of the graph.
     * Assigns each node a depth starting from roots (no incoming connections). Depth is used for animation
     * sync; it is capped at {@code nodes.size() - 1} so feedback cycles cannot drive unbounded growth (which
     * would hang this BFS).
     */
    public void updateTopology() {
        rebuildConnectionIndexes();
        analyzeCombinationalCycles();
        rebuildEvaluationOrder();
        forceFullWorldStep = true;

        // Reset depths
        for (WNode node : nodes) node.setTopoDepth(-1);
        
        java.util.Queue<WNode> queue = new java.util.LinkedList<>();
        
        // Find roots (nodes with no connected inputs)
        for (WNode node : nodes) {
            boolean hasInputs = false;
            for (WConnection conn : connections) {
                if (conn.targetNode().equals(node.getId())) {
                    hasInputs = true;
                    break;
                }
            }
            if (!hasInputs) {
                node.setTopoDepth(0);
                queue.add(node);
            }
        }
        
        // BFS to propagate depth (longest-ish path from roots). Capped so cycles cannot grow depth forever
        // (otherwise the queue never empties and the client/server hangs on every connect).
        int maxDepth = Math.max(0, nodes.size() - 1);
        while (!queue.isEmpty()) {
            WNode current = queue.poll();
            int nextDepth = Math.min(current.getTopoDepth() + 1, maxDepth);

            for (WConnection conn : connections) {
                if (conn.sourceNode().equals(current.getId())) {
                    WNode target = nodeIndex.get(conn.targetNode());
                    if (target != null && (target.getTopoDepth() == -1 || target.getTopoDepth() < nextDepth)) {
                        target.setTopoDepth(nextDepth);
                        queue.add(target);
                    }
                }
            }
        }
        
        // Handle remaining nodes (those in cycles with no external roots)
        for (WNode node : nodes) {
            if (node.getTopoDepth() == -1) node.setTopoDepth(0);
        }
    }

    private void rebuildConnectionIndexes() {
        outgoingConnections.clear();
        incomingConnections.clear();
        for (WNode node : nodes) {
            outgoingConnections.put(node.getId(), new ArrayList<>());
            incomingConnections.put(node.getId(), new ArrayList<>());
        }
        for (WConnection conn : connections) {
            if (!isConnectionUsable(conn)) continue;
            List<WConnection> out = outgoingConnections.get(conn.sourceNode());
            List<WConnection> in = incomingConnections.get(conn.targetNode());
            if (out == null || in == null) {
                continue;
            }
            out.add(conn);
            in.add(conn);
        }
    }

    private void analyzeCombinationalCycles() {
        disabledNodeIds.clear();
        diagnostics.clear();
        Map<UUID, Integer> indexes = new HashMap<>();
        Map<UUID, Integer> lowLinks = new HashMap<>();
        ArrayDeque<UUID> stack = new ArrayDeque<>();
        Set<UUID> onStack = new HashSet<>();
        int[] nextIndex = {0};
        for (WNode node : nodes) {
            if (node.isMissingType()) {
                disabledNodeIds.add(node.getId());
                diagnostics.add(new GraphDiagnostic(
                        DiagnosticSeverity.ERROR,
                        "computed.missing_node",
                        "Node implementation is unavailable; raw data is preserved",
                        Set.of(node.getId())));
            }
            if (!indexes.containsKey(node.getId())) {
                strongConnect(node.getId(), indexes, lowLinks, stack, onStack, nextIndex);
            }
        }
    }

    private void rebuildEvaluationOrder() {
        evaluationOrder.clear();
        Map<UUID, Integer> indegree = new HashMap<>();
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        for (WNode node : nodes) {
            if (!disabledNodeIds.contains(node.getId())) {
                indegree.put(node.getId(), 0);
                adjacency.put(node.getId(), new HashSet<>());
            }
        }
        for (WConnection connection : connections) {
            if (!isConnectionUsable(connection)) continue;
            WNode source = nodeIndex.get(connection.sourceNode());
            WNode target = nodeIndex.get(connection.targetNode());
            if (source == null
                    || target == null
                    || source.isStateBoundary()
                    || !indegree.containsKey(source.getId())
                    || !indegree.containsKey(target.getId())) {
                continue;
            }
            if (adjacency.get(source.getId()).add(target.getId())) {
                indegree.merge(target.getId(), 1, Integer::sum);
            }
        }
        java.util.PriorityQueue<UUID> ready = new java.util.PriorityQueue<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) ready.add(id);
        });
        while (!ready.isEmpty()) {
            UUID id = ready.remove();
            WNode node = nodeIndex.get(id);
            if (node != null) evaluationOrder.add(node);
            for (UUID target : adjacency.getOrDefault(id, Set.of())) {
                int remaining = indegree.merge(target, -1, Integer::sum);
                if (remaining == 0) ready.add(target);
            }
        }
    }

    private void strongConnect(
            UUID nodeId,
            Map<UUID, Integer> indexes,
            Map<UUID, Integer> lowLinks,
            ArrayDeque<UUID> stack,
            Set<UUID> onStack,
            int[] nextIndex) {
        int index = nextIndex[0]++;
        indexes.put(nodeId, index);
        lowLinks.put(nodeId, index);
        stack.push(nodeId);
        onStack.add(nodeId);

        List<WConnection> outgoing = outgoingConnections.get(nodeId);
        WNode sourceNode = nodeIndex.get(nodeId);
        if (outgoing != null && (sourceNode == null || !sourceNode.isStateBoundary())) {
            for (WConnection connection : outgoing) {
                WNode target = nodeIndex.get(connection.targetNode());
                if (target == null) {
                    continue;
                }
                UUID targetId = target.getId();
                if (!indexes.containsKey(targetId)) {
                    strongConnect(targetId, indexes, lowLinks, stack, onStack, nextIndex);
                    lowLinks.put(nodeId, Math.min(lowLinks.get(nodeId), lowLinks.get(targetId)));
                } else if (onStack.contains(targetId)) {
                    lowLinks.put(nodeId, Math.min(lowLinks.get(nodeId), indexes.get(targetId)));
                }
            }
        }

        if (!lowLinks.get(nodeId).equals(indexes.get(nodeId))) {
            return;
        }
        Set<UUID> component = new HashSet<>();
        UUID member;
        do {
            member = stack.pop();
            onStack.remove(member);
            component.add(member);
        } while (!member.equals(nodeId));

        boolean selfLoop = component.size() == 1 && connections.stream().anyMatch(
                connection -> isConnectionUsable(connection)
                        && connection.sourceNode().equals(nodeId)
                        && connection.targetNode().equals(nodeId)
                        && !nodeIndex.get(nodeId).isStateBoundary());
        if (component.size() > 1 || selfLoop) {
            disabledNodeIds.addAll(component);
            diagnostics.add(new GraphDiagnostic(
                    DiagnosticSeverity.ERROR,
                    "computed.cycle.legacy",
                    "Legacy combinational cycle is preserved but disabled; insert a state or delay node",
                    Set.copyOf(component)));
        }
    }

    /**
     * @return A list of all connections in the graph.
     */
    public List<WConnection> getConnections() {
        refreshStableConnectionPins();
        return connections;
    }
}
