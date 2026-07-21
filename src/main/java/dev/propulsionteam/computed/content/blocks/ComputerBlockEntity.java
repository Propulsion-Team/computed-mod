package dev.propulsionteam.computed.content.blocks;

import dev.propulsionteam.computed.internal.node.api.FunctionCardNode;
import dev.propulsionteam.computed.internal.node.api.FunctionDefinitionStore;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.MissingNode;
import dev.propulsionteam.computed.internal.node.ProgramBridge;
import dev.propulsionteam.computed.node.program.ComputedProgram;
import dev.propulsionteam.computed.node.program.ProgramCodec;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.nodes.vanilla.RedstonePortNode;
import dev.propulsionteam.computed.integration.CreateRedstoneLinkBridge;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.NbtIo;

public class ComputerBlockEntity extends BaseContainerBlockEntity {
    public static final int CONTAINER_SIZE = 9;
    private static final int MAX_PROGRAM_NODES = 4096;
    private static final int MAX_PROGRAM_CONNECTIONS = 20_000;
    private static final int MAX_PROGRAM_FUNCTIONS = 256;
    private static final int MAX_NESTED_GRAPH_DEPTH = 16;
    private static final int MAX_PROGRAM_BYTES = 4 * 1024 * 1024;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private WGraph graph = new WGraph();
    /** Saved function bodies keyed by id (parallel to graph function cards). */
    private FunctionDefinitionStore functionDefinitions = new FunctionDefinitionStore();
    /** Weak redstone emitted toward each {@link Direction} (neighbor on that side sees this level). */
    private final int[] redstoneEmitted = new int[6];
    private final CreateRedstoneLinkBridge createRedstoneLinks = new CreateRedstoneLinkBridge();
    private UUID computerUuid;
    private long programRevision;
    /** Canonical v2 source retained so unsupported addon data survives the transitional runtime. */
    private ComputedProgram persistedProgram;
    /**
     * Program fields that could not be decoded (for example, a newer format version). They are
     * written back verbatim until an explicitly validated editor save replaces them.
     */
    private CompoundTag unreadableProgramData;
    private transient boolean dropsHandled;

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.COMPUTER_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Runs the node graph on the server world thread at 20 TPS. Evaluators are not safe for arbitrary
     * background threads without a numeric snapshot pipeline, so stepping stays synchronous here.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        // Sable's sub-level tick dispatcher doesn't drop removed BEs from its ticker list the way
        // vanilla chunks do, so the graph would keep executing after the computer is broken.
        if (be.isRemoved()) {
            return;
        }
        Level lvl = be.getLevel();
        if (CreateRedstoneLinkBridge.isCreateLoaded() && lvl != null && !lvl.isClientSide) {
            be.createRedstoneLinks.ensureSynced(lvl, be, be.graph);
        }
        ComputedGraphExecution.withHost(be, () -> be.graph.advanceSimulationInWorld(1.0 / WGraph.MAX_TICK_RATE));
        if (CreateRedstoneLinkBridge.isCreateLoaded() && lvl != null && !lvl.isClientSide) {
            be.createRedstoneLinks.pushTransmitters(lvl);
        }
        be.mutePeripheralsWithoutHardware(be.graph);
        be.refreshRedstoneFromGraph();
    }

    /**
     * Returns the weak signal emitted from the given face of the computer. Minecraft's
     * {@code getSignal(..., direction)} passes {@code direction} as the direction from the querying
     * neighbor toward this block, so the face being queried is its opposite.
     */
    public int getEmittedRedstone(Direction fromNeighborTowardSelf) {
        return redstoneEmitted[fromNeighborTowardSelf.getOpposite().ordinal()];
    }

    /** Zeros outputs for peripheral nodes with no matching item in this computer (including nested function graphs). */
    private void mutePeripheralsWithoutHardware(WGraph g) {
        for (WNode n : g.getNodes()) {
            if (n instanceof FunctionCardNode fc) {
                mutePeripheralsWithoutHardware(fc.getInnerGraph());
            }
            if (Peripherals.isPeripheralNodeType(n.getTypeId()) && !hasPeripheralEquipped(n.getTypeId())) {
                for (var out : n.getOutputs()) {
                    out.setValue(0.0);
                }
            }
        }
    }

    private void refreshRedstoneFromGraph() {
        Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        int[] next = new int[6];
        List<RedstonePortNode> ports = new ArrayList<>();
        collectRedstonePorts(graph, ports);
        BlockState st = getBlockState();
        Direction facing = st.getValue(ComputerBlock.FACING);
        for (RedstonePortNode rp : ports) {
            if (!hasPeripheralEquipped(RedstonePortNode.TYPE_ID)) {
                continue;
            }
            WNode n = rp;
            if (n.getInputs().size() < 2) {
                continue;
            }
            double tick = n.getInputs().get(0).getValue();
            double lv = n.getInputs().get(1).getValue();
            if (tick > 0.5) {
                int p = net.minecraft.util.Mth.clamp((int) Math.round(lv), 0, 15);
                int o = rp.getEmitFace().toWorld(facing).ordinal();
                next[o] = Math.max(next[o], p);
            }
        }
        if (!Arrays.equals(next, redstoneEmitted)) {
            System.arraycopy(next, 0, redstoneEmitted, 0, 6);
            setChanged();
            lvl.updateNeighborsAt(worldPosition, st.getBlock());
            for (Direction d : Direction.values()) {
                lvl.neighborChanged(worldPosition.relative(d), st.getBlock(), worldPosition);
            }
        }
    }

    private static void collectRedstonePorts(WGraph g, List<RedstonePortNode> out) {
        for (WNode n : g.getNodes()) {
            if (n instanceof RedstonePortNode rp) {
                out.add(rp);
            } else if (n instanceof FunctionCardNode fc) {
                collectRedstonePorts(fc.getInnerGraph(), out);
            }
        }
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.computed.computer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items.clear();
        for (int i = 0; i < Math.min(newItems.size(), items.size()); i++) {
            items.set(i, newItems.get(i));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ComputerPeripheralMenu(ComputedRegistries.COMPUTER_PERIPHERAL_MENU.get(), containerId, playerInventory, this);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
    }

    public WGraph getGraph() {
        return graph;
    }

    public CompoundTag getGraphData() {
        return ProgramBridge.writeEnvelope(snapshotProgram());
    }

    public long getProgramRevision() {
        return programRevision;
    }

    public record ApplyGraphResult(boolean accepted, long serverRevision, String message) {
        static ApplyGraphResult accepted(long revision) {
            return new ApplyGraphResult(true, revision, "ok");
        }

        static ApplyGraphResult rejected(long revision, String message) {
            return new ApplyGraphResult(false, revision, message);
        }
    }

    /** Validates into temporary objects and swaps them only when the complete program is valid. */
    public ApplyGraphResult applyGraphFromNetwork(CompoundTag tag, long expectedRevision) {
        if (expectedRevision != programRevision) {
            return ApplyGraphResult.rejected(
                    programRevision,
                    "stale editor revision (expected " + programRevision + ", received " + expectedRevision + ")");
        }
        String sizeError = validateEncodedSize(tag);
        if (sizeError != null) {
            return ApplyGraphResult.rejected(programRevision, sizeError);
        }
        CompoundTag copy = tag.copy();
        Peripherals.stripEditorOnlyTags(copy);
        java.util.Map<UUID, PinSnapshot[]> inputSnap = new java.util.HashMap<>();
        java.util.Map<UUID, PinSnapshot[]> outputSnap = new java.util.HashMap<>();
        snapshotPinValues(graph, inputSnap, outputSnap);
        ComputedProgram liveProgram = snapshotProgram();
        ProgramBridge.RuntimeProgram decoded;
        try {
            decoded = ProgramBridge.decode(copy);
        } catch (RuntimeException exception) {
            return ApplyGraphResult.rejected(programRevision, "program could not be decoded: " + exception.getMessage());
        }
        String validationError = validateProgram(decoded.program());
        if (validationError != null) {
            return ApplyGraphResult.rejected(programRevision, validationError);
        }
        ComputedProgram stateMerged = ProgramBridge.preserveRuntimeState(decoded.program(), liveProgram);
        ProgramBridge.RuntimeProgram stateDecoded;
        try {
            stateDecoded = ProgramBridge.decode(ProgramBridge.writeEnvelope(stateMerged));
        } catch (RuntimeException exception) {
            return ApplyGraphResult.rejected(
                    programRevision, "program could not preserve authoritative runtime state: " + exception.getMessage());
        }
        WGraph nextGraph = stateDecoded.graph();
        FunctionDefinitionStore nextFunctions = stateDecoded.functions();
        restorePinValues(nextGraph, inputSnap, outputSnap);
        graph = nextGraph;
        functionDefinitions = nextFunctions;
        programRevision++;
        persistedProgram = stateDecoded.program().withRevision(programRevision);
        unreadableProgramData = null;
        createRedstoneLinks.markGraphDirty();
        setChanged();
        return ApplyGraphResult.accepted(programRevision);
    }

    private String validateProgram(ComputedProgram program) {
        if (program.functions().size() > MAX_PROGRAM_FUNCTIONS) {
            return "program exceeds the function limit of " + MAX_PROGRAM_FUNCTIONS;
        }
        long modelNodes = program.rootGraph().nodes().size();
        long modelConnections = program.rootGraph().connections().size();
        for (var function : program.functions()) {
            modelNodes += function.graph().nodes().size();
            modelConnections += function.graph().connections().size();
        }
        if (modelNodes > MAX_PROGRAM_NODES) {
            return "program exceeds the node limit of " + MAX_PROGRAM_NODES;
        }
        if (modelConnections > MAX_PROGRAM_CONNECTIONS) {
            return "program exceeds the connection limit of " + MAX_PROGRAM_CONNECTIONS;
        }
        CompoundTag legacyBundle = ProgramCodec.toLegacyBundleTag(program);
        String structuralError = validateProgramTag(legacyBundle);
        if (structuralError != null) return structuralError;

        var incomingAnalyses = ProgramBridge.analyzeAll(program);
        var previousAnalyses = persistedProgram == null ? List.<ProgramBridge.AnalyzedGraph>of() : ProgramBridge.analyzeAll(persistedProgram);
        java.util.Set<GraphCycleKey> existingCycles = new java.util.HashSet<>();
        for (var analyzedGraph : previousAnalyses) {
            for (List<UUID> cycle : analyzedGraph.analysis().combinationalCycles()) {
                existingCycles.add(new GraphCycleKey(analyzedGraph.graphId(), java.util.Set.copyOf(cycle)));
            }
        }
        for (var analyzedGraph : incomingAnalyses) {
            for (List<UUID> cycle : analyzedGraph.analysis().combinationalCycles()) {
                if (!existingCycles.contains(new GraphCycleKey(analyzedGraph.graphId(), java.util.Set.copyOf(cycle)))) {
                    return "program introduces a new combinational cycle in graph " + analyzedGraph.graphId();
                }
            }
        }
        java.util.Set<String> existingStructuralDiagnostics = new java.util.HashSet<>();
        for (var analyzedGraph : previousAnalyses) {
            for (var diagnostic : analyzedGraph.analysis().diagnostics()) {
                existingStructuralDiagnostics.add(structuralDiagnosticKey(diagnostic));
            }
        }
        for (var analyzedGraph : incomingAnalyses) {
            for (var diagnostic : analyzedGraph.analysis().diagnostics()) {
                if (diagnostic.severity()
                                != dev.propulsionteam.computed.node.program.ProgramDiagnostic.Severity.ERROR
                        || "placeholder_node_disabled".equals(diagnostic.code())
                        || "combinational_cycle".equals(diagnostic.code())
                        || "combinational_self_loop".equals(diagnostic.code())) {
                    continue;
                }
                if (!existingStructuralDiagnostics.contains(structuralDiagnosticKey(diagnostic))) {
                    return "program validation failed in graph " + analyzedGraph.graphId() + ": " + diagnostic.message();
                }
            }
        }
        return null;
    }

    private record GraphCycleKey(UUID graphId, java.util.Set<UUID> nodeIds) {}

    private static String structuralDiagnosticKey(
            dev.propulsionteam.computed.node.program.ProgramDiagnostic diagnostic) {
        return diagnostic.code() + "|" + diagnostic.graphId() + "|" + diagnostic.nodeId() + "|"
                + diagnostic.connectionId();
    }

    private static String validateEncodedSize(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return bytes.size() > MAX_PROGRAM_BYTES
                    ? "program exceeds the encoded size limit of " + MAX_PROGRAM_BYTES + " bytes"
                    : null;
        } catch (IOException | RuntimeException exception) {
            return "program NBT could not be measured safely";
        }
    }

    private static String validateProgramTag(CompoundTag bundle) {
        CompoundTag graphTag = bundle.contains("ComputerGraph", Tag.TAG_COMPOUND)
                ? bundle.getCompound("ComputerGraph")
                : bundle;
        int[] totals = new int[2];
        String graphError = validateGraphTag(graphTag, 0, totals);
        if (graphError != null) {
            return graphError;
        }
        ListTag functions = bundle.getList("ComputerFunctions", Tag.TAG_COMPOUND);
        if (functions.size() > MAX_PROGRAM_FUNCTIONS) {
            return "program exceeds the function limit of " + MAX_PROGRAM_FUNCTIONS;
        }
        for (int i = 0; i < functions.size(); i++) {
            String error = validateGraphTag(functions.getCompound(i).getCompound("Body"), 1, totals);
            if (error != null) {
                return "function " + i + ": " + error;
            }
        }
        return null;
    }

    private static String validateGraphTag(CompoundTag graphTag, int depth, int[] totals) {
        if (depth > MAX_NESTED_GRAPH_DEPTH) {
            return "nested functions exceed depth " + MAX_NESTED_GRAPH_DEPTH;
        }
        ListTag nodes = graphTag.getList("nodes", Tag.TAG_COMPOUND);
        ListTag connections = graphTag.getList("conns", Tag.TAG_COMPOUND);
        totals[0] += nodes.size();
        totals[1] += connections.size();
        if (totals[0] > MAX_PROGRAM_NODES) {
            return "program exceeds the node limit of " + MAX_PROGRAM_NODES;
        }
        if (totals[1] > MAX_PROGRAM_CONNECTIONS) {
            return "program exceeds the connection limit of " + MAX_PROGRAM_CONNECTIONS;
        }
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag node = nodes.getCompound(i);
            try {
                ResourceLocation type = ResourceLocation.parse(node.getString("typeId"));
                if (!NodeRegistry.isRegistered(type) && !node.getBoolean(MissingNode.MISSING_MARKER)) {
                    return "unknown node type " + type;
                }
            } catch (RuntimeException exception) {
                return "node " + i + " has an invalid type ID";
            }
            if (node.contains("inner", Tag.TAG_COMPOUND)) {
                String error = validateGraphTag(node.getCompound("inner"), depth + 1, totals);
                if (error != null) {
                    return error;
                }
            }
        }
        return null;
    }

    private static void snapshotPinValues(WGraph g,
                                          java.util.Map<UUID, PinSnapshot[]> ins,
                                          java.util.Map<UUID, PinSnapshot[]> outs) {
        for (WNode n : g.getNodes()) {
            PinSnapshot[] in = new PinSnapshot[n.getInputs().size()];
            for (int i = 0; i < in.length; i++) in[i] = PinSnapshot.capture(n.getInputs().get(i));
            PinSnapshot[] out = new PinSnapshot[n.getOutputs().size()];
            for (int i = 0; i < out.length; i++) out[i] = PinSnapshot.capture(n.getOutputs().get(i));
            ins.put(n.getId(), in);
            outs.put(n.getId(), out);
            if (n instanceof FunctionCardNode fc) {
                snapshotPinValues(fc.getInnerGraph(), ins, outs);
            }
        }
    }

    private static void restorePinValues(WGraph g,
                                         java.util.Map<UUID, PinSnapshot[]> ins,
                                         java.util.Map<UUID, PinSnapshot[]> outs) {
        for (WNode n : g.getNodes()) {
            PinSnapshot[] in = ins.get(n.getId());
            if (in != null) {
                restorePins(n.getInputs(), in);
            }
            PinSnapshot[] out = outs.get(n.getId());
            if (out != null) {
                restorePins(n.getOutputs(), out);
            }
            if (n instanceof FunctionCardNode fc) {
                restorePinValues(fc.getInnerGraph(), ins, outs);
            }
        }
    }

    private static void restorePins(List<WPin> pins, PinSnapshot[] snapshots) {
        java.util.Map<String, PinSnapshot> byStableKey = new java.util.HashMap<>();
        for (PinSnapshot snapshot : snapshots) {
            if (snapshot.stableKey() != null) byStableKey.putIfAbsent(snapshot.stableKey(), snapshot);
        }
        for (int i = 0; i < pins.size(); i++) {
            WPin pin = pins.get(i);
            PinSnapshot snapshot = pin.getStableKey() == null
                    ? (i < snapshots.length ? snapshots[i] : null)
                    : byStableKey.get(pin.getStableKey());
            if (snapshot != null) snapshot.restoreTo(pin);
        }
    }

    private record PinSnapshot(
            String stableKey, WPin.DataType type, double numberValue, String stringValue, Object widgetValue) {
        static PinSnapshot capture(WPin pin) {
            return new PinSnapshot(
                    pin.getStableKey(), pin.getDataType(), pin.getValue(), pin.getStringValue(), pin.getWidgetValue());
        }

        void restoreTo(WPin pin) {
            if (pin.getDataType() != type) {
                return;
            }
            switch (type) {
                case NUMBER -> pin.setValue(numberValue);
                case STRING -> pin.setStringValue(stringValue);
                case WIDGET -> pin.setWidgetValue(widgetValue);
            }
        }
    }

    private void hydrateFunctionCardsFromLibrary() {
        FunctionCardNode.applyLibraryToInnerGraphs(graph, functionDefinitions);
    }

    /**
     * Shift-use: place one peripheral into the first valid empty slot (unique types only).
     */
    public boolean tryInsertPeripheralFromHand(ItemStack stack) {
        if (!Peripherals.isPeripheral(stack)) {
            return false;
        }
        ItemStack one = stack.split(1);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (getItem(i).isEmpty() && Peripherals.mayPlaceInComputer(this, i, one)) {
                setItem(i, one);
                return true;
            }
        }
        stack.grow(1);
        return false;
    }

    /** Always returns true: there are no hardware-gated nodes after the peripheral simplification. */
    public boolean hasPeripheralEquipped(ResourceLocation nodeTypeId) {
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        computerUuid = tag.hasUUID("ComputerUUID") ? tag.getUUID("ComputerUUID") : null;
        loadProgramData(tag);
        createRedstoneLinks.markGraphDirty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        writeStoredProgram(tag);
        if (computerUuid != null) {
            tag.putUUID("ComputerUUID", computerUuid);
        }
    }

    public UUID getOrCreateUuid() {
        if (computerUuid == null) {
            computerUuid = UUID.randomUUID();
            setChanged();
        }
        return computerUuid;
    }

    public boolean hasStoredState() {
        if (unreadableProgramData != null && !unreadableProgramData.isEmpty()) return true;
        if (!graph.getNodes().isEmpty()) return true;
        if (!functionDefinitions.isEmpty()) return true;
        for (ItemStack s : items) {
            if (!s.isEmpty()) return true;
        }
        return false;
    }

    public void markDropsHandled() {
        dropsHandled = true;
    }

    public boolean dropsHandled() {
        return dropsHandled;
    }

    @Override
    public void setRemoved() {
        Level lvl = this.level;
        if (lvl != null && !lvl.isClientSide) {
            createRedstoneLinks.clear(lvl);
        }
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeStoredProgram(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadProgramData(tag);
    }

    private ComputedProgram snapshotProgram() {
        ComputedProgram snapshot = ProgramBridge.snapshot(graph, functionDefinitions, programRevision);
        persistedProgram = ProgramBridge.reconcile(persistedProgram, snapshot).withRevision(programRevision);
        return persistedProgram;
    }

    private void writeStoredProgram(CompoundTag target) {
        if (unreadableProgramData != null && !unreadableProgramData.isEmpty()) {
            for (String key : unreadableProgramData.getAllKeys()) {
                Tag value = unreadableProgramData.get(key);
                if (value != null) target.put(key, value.copy());
            }
            return;
        }
        target.put(ProgramBridge.PROGRAM_TAG, ProgramCodec.write(snapshotProgram()));
    }

    private void loadProgramData(CompoundTag tag) {
        if (!ProgramBridge.containsProgram(tag)) {
            graph = new WGraph();
            functionDefinitions = new FunctionDefinitionStore();
            persistedProgram = null;
            unreadableProgramData = null;
            programRevision = 0L;
            return;
        }
        try {
            ProgramBridge.RuntimeProgram decoded = ProgramBridge.decode(tag);
            graph = decoded.graph();
            functionDefinitions = decoded.functions();
            long legacyRevision = tag.contains("ComputedProgramRevision")
                    ? Math.max(0L, tag.getLong("ComputedProgramRevision"))
                    : 0L;
            programRevision = Math.max(decoded.program().revision(), legacyRevision);
            persistedProgram = decoded.program().withRevision(programRevision);
            unreadableProgramData = null;
            hydrateFunctionCardsFromLibrary();
        } catch (RuntimeException exception) {
            dev.propulsionteam.computed.Computed.LOGGER.error(
                    "Could not load Computed program at {}; preserving its raw NBT with an empty runtime",
                    worldPosition,
                    exception);
            graph = new WGraph();
            functionDefinitions = new FunctionDefinitionStore();
            persistedProgram = null;
            unreadableProgramData = copyProgramFields(tag);
            programRevision = rawProgramRevision(tag);
        }
    }

    private static CompoundTag copyProgramFields(CompoundTag source) {
        CompoundTag preserved = new CompoundTag();
        for (String key : List.of(
                ProgramBridge.PROGRAM_TAG,
                "ComputedProgramRevision",
                "ComputerGraph",
                "ComputerFunctions",
                "formatVersion",
                "revision",
                "graph",
                "functions",
                "diagnostics",
                "metadata",
                "nodes",
                "conns",
                "sections",
                "waypoints")) {
            Tag value = source.get(key);
            if (value != null) preserved.put(key, value.copy());
        }
        return preserved;
    }

    private static long rawProgramRevision(CompoundTag source) {
        long revision = Math.max(0L, source.getLong("ComputedProgramRevision"));
        revision = Math.max(revision, Math.max(0L, source.getLong("revision")));
        if (source.contains(ProgramBridge.PROGRAM_TAG, Tag.TAG_COMPOUND)) {
            revision = Math.max(
                    revision,
                    Math.max(0L, source.getCompound(ProgramBridge.PROGRAM_TAG).getLong("revision")));
        }
        return revision;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
