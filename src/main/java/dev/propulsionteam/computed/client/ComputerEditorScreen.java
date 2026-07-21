package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.internal.node.api.FunctionDefinitionStore;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.ProgramBridge;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import dev.propulsionteam.computed.node.program.ComputedProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.network.SaveComputerGraphPayload;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-block node UI; graph edits are sent back to the {@link dev.propulsionteam.computed.content.blocks.ComputerBlockEntity} when the screen closes.
 */
public class ComputerEditorScreen extends WNodeScreen {
    private static final int AUTO_SAVE_INTERVAL_TICKS = 20;

    private final BlockPos computerPos;
    private final WGraph editorGraph;
    private final Set<ResourceLocation> peripheralUnlock;
    private final List<Component> placedPeripheralHud;
    private int autoSaveCountdown;
    private long serverRevision;
    private long acknowledgedEditorRevision;
    private long acknowledgedHistoryRevision;
    private long inFlightEditorRevision = -1;
    private long inFlightHistoryRevision = -1;
    private boolean saveInFlight;
    private boolean saveBlocked;
    private long blockedEditorRevision = -1;
    private long blockedHistoryRevision = -1;
    private ComputedProgram baseProgram;
    private final Map<Long, ComputedProgram> pendingPrograms = new HashMap<>();
    private final Map<Long, Long> pendingHistoryRevisions = new HashMap<>();

    public ComputerEditorScreen(
            BlockPos computerPos,
            WGraph graph,
            FunctionDefinitionStore functionStore,
            Set<ResourceLocation> peripheralUnlock,
            List<Component> placedPeripheralHud,
            long serverRevision,
            ComputedProgram baseProgram) {
        super(graph, functionStore, Peripherals.hardwareMissingPredicate(peripheralUnlock));
        this.computerPos = computerPos;
        this.editorGraph = graph;
        this.peripheralUnlock = Set.copyOf(peripheralUnlock);
        this.placedPeripheralHud = List.copyOf(placedPeripheralHud);
        this.serverRevision = serverRevision;
        this.baseProgram = baseProgram == null ? null : baseProgram.withRevision(serverRevision);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            ComputerEditorViewState.load(mc.player.getUUID(), mc.level.dimension(), computerPos, EDITOR_VIEWPORT_ROOT)
                    .ifPresent(v -> restoreEditorViewport(v.panX(), v.panY(), v.zoom()));
        }
    }

    @Override
    protected boolean isFunctionLibraryDefinitionHardwareLocked(FunctionDefinitionStore.Definition def) {
        return Peripherals.graphNbtUsesMissingPeripheral(def.body(), peripheralUnlock);
    }

    @Override
    protected List<Component> placedPeripheralHudLines() {
        return placedPeripheralHud;
    }

    @Override
    protected void persistEditorViewport(String contextKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            ComputerEditorViewState.save(
                    mc.player.getUUID(),
                    mc.level.dimension(),
                    computerPos,
                    contextKey,
                    editorPanX(),
                    editorPanY(),
                    editorZoom());
        }
    }

    @Override
    protected Path clientNestedFunctionsDirectory() {
        return ClientFunctionLibraryFiles.ensureRoot();
    }

    @Override
    protected void clientRevealNestedFunctionsFolder(Path directory) {
        ClientFunctionLibraryFiles.openFolder(directory);
    }

    @Override
    protected boolean loadEditorViewport(String contextKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        return ComputerEditorViewState.load(mc.player.getUUID(), mc.level.dimension(), computerPos, contextKey)
                .map(
                        v -> {
                            restoreEditorViewport(v.panX(), v.panY(), v.zoom());
                            return true;
                        })
                .orElse(false);
    }

    private ComputedProgram programForNetwork(long revision) {
        ComputedProgram snapshot = ProgramBridge.snapshot(editorGraph, functionStore, revision);
        return ProgramBridge.reconcile(baseProgram, snapshot).withRevision(revision);
    }

    private void saveEditorViewportIfPossible() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            ComputerEditorViewState.save(
                    mc.player.getUUID(),
                    mc.level.dimension(),
                    computerPos,
                    editorViewportContextKey(),
                    editorPanX(),
                    editorPanY(),
                    editorZoom());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (--autoSaveCountdown <= 0) {
            autoSaveCountdown = AUTO_SAVE_INTERVAL_TICKS;
            sendDirtyProgram(false);
        }
    }

    private void sendDirtyProgram(boolean closing) {
        long localRevision = editorRevision();
        long localHistoryRevision = editorHistoryRevision();
        if (saveBlocked) {
            if (localRevision == blockedEditorRevision && localHistoryRevision == blockedHistoryRevision) {
                return;
            }
            saveBlocked = false;
            clearEditorSaveFailureDiagnostic();
        }
        if ((localRevision == acknowledgedEditorRevision
                        && localHistoryRevision == acknowledgedHistoryRevision
                        && !editorHistoryDirty())) {
            return;
        }
        if (saveInFlight && !closing) {
            return;
        }
        long expectedRevision = serverRevision + (saveInFlight && closing ? 1 : 0);
        ComputedProgram outgoing = programForNetwork(expectedRevision);
        PacketDistributor.sendToServer(new SaveComputerGraphPayload(
                computerPos, expectedRevision, localRevision, ProgramBridge.writeEnvelope(outgoing)));
        saveInFlight = true;
        pendingPrograms.put(localRevision, outgoing);
        pendingHistoryRevisions.put(localRevision, localHistoryRevision);
        inFlightEditorRevision = localRevision;
        inFlightHistoryRevision = localHistoryRevision;
        saveEditorViewportIfPossible();
    }

    /** Applies the server acknowledgement without replacing or discarding the local editor graph. */
    public void onServerSaveResult(boolean accepted, long newServerRevision, long savedEditorRevision, String message) {
        if (accepted || newServerRevision >= 0) serverRevision = newServerRevision;
        long savedHistoryRevision = pendingHistoryRevisions.getOrDefault(savedEditorRevision, -1L);
        ComputedProgram acknowledgedProgram = pendingPrograms.remove(savedEditorRevision);
        pendingHistoryRevisions.remove(savedEditorRevision);
        saveInFlight = !pendingPrograms.isEmpty();
        if (savedEditorRevision == inFlightEditorRevision) {
            inFlightEditorRevision = -1;
            inFlightHistoryRevision = -1;
        }
        if (accepted) {
            if (acknowledgedProgram != null) {
                baseProgram = acknowledgedProgram.withRevision(newServerRevision);
            }
            acknowledgedEditorRevision = Math.max(acknowledgedEditorRevision, savedEditorRevision);
            if (savedHistoryRevision >= 0) {
                acknowledgedHistoryRevision = savedHistoryRevision;
            }
            acknowledgeEditorHistorySaved(savedEditorRevision);
            clearEditorSaveFailureDiagnostic();
            saveBlocked = false;
            return;
        }
        saveBlocked = true;
        blockedEditorRevision = editorRevision();
        blockedHistoryRevision = editorHistoryRevision();
        setEditorSaveFailureDiagnostic("Save rejected: " + message);
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("Computed graph was not saved: " + message), false);
        }
    }

    public boolean editsComputer(BlockPos pos) {
        return computerPos.equals(pos);
    }

    @Override
    public void removed() {
        saveEditorViewportIfPossible();
        super.removed();
        sendDirtyProgram(true);
    }
}
