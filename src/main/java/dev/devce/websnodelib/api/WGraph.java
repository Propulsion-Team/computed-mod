package dev.devce.websnodelib.api;

import dev.devce.websnodelib.api.elements.WSlider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The core data structure for the node system.
 * A WGraph manages a collection of nodes and the connections between them.
 * It is responsible for logical updates (ticking) and data flow propagation.
 */
public class WGraph {

    /** Node type id for the graph tick driver (menu "Tick"). */
    public static final ResourceLocation TICK_NODE_TYPE =
            ResourceLocation.fromNamespaceAndPath("websnodelib", "tick");

    /** Maximum updates per second for the tick node's Rate slider (matches default Minecraft TPS). */
    public static final int MAX_TICK_RATE = 20;

    private final List<WNode> nodes = new ArrayList<>();
    private final List<WConnection> connections = new ArrayList<>();
    private final List<WSection> sections = new ArrayList<>();

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

        private net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", id.toString());
            tag.putString("name", name);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("w", width);
            tag.putInt("h", height);
            tag.putInt("bodyArgb", bodyColorArgb);
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
            return s;
        }
    }

    /** Seconds accumulated toward the next pulse, per tick-node id. */
    private final Map<UUID, double[]> tickAccumSec = new HashMap<>();

    /** Increments on each logical propagate+evaluate when a tick driver is active and a pulse fired. */
    private int simulationStepCounter = 0;

    /**
     * Adds a new node to the graph and recalculates the topological structure.
     * @param node The node instance to add.
     */
    public void addNode(WNode node) {
        nodes.add(node);
        updateTopology();
    }

    /**
     * Removes a node and all its associated connections from the graph.
     * @param node The node to remove.
     */
    public void removeNode(WNode node) {
        nodes.remove(node);
        connections.removeIf(c -> c.sourceNode().equals(node.getId()) || c.targetNode().equals(node.getId()));
        updateTopology();
    }

    /**
     * Serializes the entire graph state into a NBT CompoundTag.
     * @return A tag containing all nodes, their internal data, and connections.
     */
    public net.minecraft.nbt.CompoundTag save() {
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

    /**
     * Reconstructs the graph state from a NBT CompoundTag.
     * @param tag The tag containing serialized graph data.
     */
    public void load(net.minecraft.nbt.CompoundTag tag) {
        nodes.clear();
        connections.clear();
        sections.clear();
        
        net.minecraft.nbt.ListTag nodesTag = tag.getList("nodes", 10);
        for (int i = 0; i < nodesTag.size(); i++) {
            net.minecraft.nbt.CompoundTag nTag = nodesTag.getCompound(i);
            net.minecraft.resources.ResourceLocation type = net.minecraft.resources.ResourceLocation.parse(nTag.getString("typeId"));
            WNode node = NodeRegistry.createNode(type, nTag.getInt("x"), nTag.getInt("y"));
            if (node != null) {
                node.load(nTag);
                nodes.add(node);
            }
        }
        
        net.minecraft.nbt.ListTag connsTag = tag.getList("conns", 10);
        for (int i = 0; i < connsTag.size(); i++) {
            net.minecraft.nbt.CompoundTag c = connsTag.getCompound(i);
            connect(java.util.UUID.fromString(c.getString("src")), c.getInt("srcP"), 
                    java.util.UUID.fromString(c.getString("tgt")), c.getInt("tgtP"));
        }
        net.minecraft.nbt.ListTag sectionsTag = tag.getList("sections", 10);
        for (int i = 0; i < sectionsTag.size(); i++) {
            sections.add(WSection.load(sectionsTag.getCompound(i)));
        }
        tickAccumSec.clear();
        simulationStepCounter = 0;
        updateTopology();
    }

    /**
     * Establishes a connection between an output pin of a source node and an input pin of a target node.
     * @param sourceNode UUID of the source node.
     * @param sourcePin Index of the output pin.
     * @param targetNode UUID of the target node.
     * @param targetPin Index of the input pin.
     */
    public void connect(UUID sourceNode, int sourcePin, UUID targetNode, int targetPin) {
        connections.add(new WConnection(sourceNode, sourcePin, targetNode, targetPin));
        updateTopology();
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

    public List<WSection> getSections() {
        return sections;
    }

    public int getSimulationStepCounter() {
        return simulationStepCounter;
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
            stepConnectionsAndEval();
            simulationStepCounter++;
            return;
        }
        boolean pulse = prepareTickDrivers(deltaSeconds);
        if (pulse) {
            stepConnectionsAndEval();
            simulationStepCounter++;
        }
    }

    /**
     * Block/world execution: if the graph has tick drivers, updates their outputs for this timestep, then always
     * runs one full wire propagation and evaluation pass.
     * <p>
     * Unlike {@link #advanceSimulation(double)}, this does <strong>not</strong> skip evaluation when no tick
     * pulse fired. Gating the entire graph on pulses breaks in-world redstone, function cards, and
     * peripherals that must see fresh values every game tick.
     */
    public void advanceSimulationInWorld(double deltaSeconds) {
        if (deltaSeconds <= 0) {
            deltaSeconds = 1.0e-4;
        }
        if (usesTickDriver()) {
            prepareTickDrivers(deltaSeconds);
        }
        stepConnectionsAndEval();
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
     */
    public void propagateAndEvaluate() {
        stepConnectionsAndEval();
    }

    /** One logical step: propagate all connections, then evaluate every node. */
    private void stepConnectionsAndEval() {
        for (WConnection conn : connections) {
            WNode source = findNode(conn.sourceNode());
            WNode target = findNode(conn.targetNode());
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
            double val = source.getOutputs().get(sp).getValue();
            target.getInputs().get(tp).setValue(val);
            target.getInputs().get(tp).setConnected(true);
            source.getOutputs().get(sp).setConnected(true);
        }
        for (WNode node : nodes) {
            node.evaluate();
        }
    }

    /**
     * Internal helper to find a node by its unique identifier.
     * @param id UUID of the node.
     * @return The node instance or null if not found.
     */
    private WNode findNode(UUID id) {
        return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Updates the topological structure of the graph.
     * Calculates the "depth" of each node starting from root nodes (nodes with no inputs).
     * This depth is used for animation synchronization and processing order.
     */
    public void updateTopology() {
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
        
        // BFS to propagate depth
        while (!queue.isEmpty()) {
            WNode current = queue.poll();
            int nextDepth = current.getTopoDepth() + 1;
            
            for (WConnection conn : connections) {
                if (conn.sourceNode().equals(current.getId())) {
                    WNode target = findNode(conn.targetNode());
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

    /**
     * @return A list of all connections in the graph.
     */
    public List<WConnection> getConnections() {
        return connections;
    }
}
