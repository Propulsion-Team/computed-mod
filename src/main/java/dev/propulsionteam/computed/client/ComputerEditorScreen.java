package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.client.editor.canvas.LuaEditorGraphAdapter;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import dev.propulsionteam.computed.network.SaveComputerGraphPayload;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class ComputerEditorScreen extends WNodeScreen {
    private static final int AUTO_SAVE_INTERVAL_TICKS = 20;

    private final BlockPos computerPos;
    private final WGraph editorGraph;
    private final Map<Long, ComputedProgramV3> pendingPrograms = new HashMap<>();
    private final Map<Long, Long> pendingHistoryRevisions = new HashMap<>();
    private ComputedProgramV3 baseProgram;
    private int autoSaveCountdown;
    private long serverRevision;
    private long acknowledgedEditorRevision;
    private long acknowledgedHistoryRevision;
    private long inFlightEditorRevision = -1;
    private boolean saveInFlight;
    private boolean saveBlocked;
    private long blockedEditorRevision = -1;
    private long blockedHistoryRevision = -1;

    public ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            long serverRevision) {
        this(computerPos, program, LuaEditorGraphAdapter.toEditorGraph(program), serverRevision);
    }

    private ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            WGraph editorGraph,
            long serverRevision) {
        super(editorGraph, null, ignored -> false);
        this.computerPos = computerPos;
        this.editorGraph = editorGraph;
        this.serverRevision = serverRevision;
        baseProgram = program.withRevision(serverRevision);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            ComputerEditorViewState.load(
                            minecraft.player.getUUID(),
                            minecraft.level.dimension(),
                            computerPos,
                            EDITOR_VIEWPORT_ROOT)
                    .ifPresent(view -> restoreEditorViewport(view.panX(), view.panY(), view.zoom()));
        }
    }

    @Override
    protected void persistEditorViewport(String contextKey) {
        saveEditorViewport();
    }

    @Override
    protected boolean loadEditorViewport(String contextKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        return ComputerEditorViewState.load(
                        minecraft.player.getUUID(),
                        minecraft.level.dimension(),
                        computerPos,
                        contextKey)
                .map(view -> {
                    restoreEditorViewport(view.panX(), view.panY(), view.zoom());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (--autoSaveCountdown <= 0) {
            autoSaveCountdown = AUTO_SAVE_INTERVAL_TICKS;
            sendDirtyProgram(false);
        }
    }

    public void onServerSaveResult(
            boolean accepted,
            long newServerRevision,
            long savedEditorRevision,
            String message) {
        if (accepted || newServerRevision >= 0) {
            serverRevision = newServerRevision;
        }
        long savedHistoryRevision = pendingHistoryRevisions.getOrDefault(savedEditorRevision, -1L);
        ComputedProgramV3 acknowledged = pendingPrograms.remove(savedEditorRevision);
        pendingHistoryRevisions.remove(savedEditorRevision);
        saveInFlight = !pendingPrograms.isEmpty();
        if (savedEditorRevision == inFlightEditorRevision) {
            inFlightEditorRevision = -1;
        }
        if (accepted) {
            if (acknowledged != null) {
                baseProgram = acknowledged.withRevision(newServerRevision);
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
                    Component.literal("Computed graph was not saved: " + message),
                    false);
        }
    }

    public boolean editsComputer(BlockPos pos) {
        return computerPos.equals(pos);
    }

    @Override
    public void removed() {
        saveEditorViewport();
        super.removed();
        sendDirtyProgram(true);
    }

    private ComputedProgramV3 programForNetwork(long revision) {
        return LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, revision);
    }

    private void sendDirtyProgram(boolean closing) {
        long localRevision = editorRevision();
        long historyRevision = editorHistoryRevision();
        if (saveBlocked) {
            if (localRevision == blockedEditorRevision && historyRevision == blockedHistoryRevision) {
                return;
            }
            saveBlocked = false;
            clearEditorSaveFailureDiagnostic();
        }
        if (localRevision == acknowledgedEditorRevision
                && historyRevision == acknowledgedHistoryRevision
                && !editorHistoryDirty()) {
            return;
        }
        if (saveInFlight && !closing) {
            return;
        }
        long expectedRevision = serverRevision + (saveInFlight && closing ? 1 : 0);
        ComputedProgramV3 outgoing = programForNetwork(expectedRevision);
        CompoundTag envelope = new CompoundTag();
        envelope.put(ComputerBlockEntity.PROGRAM_TAG, ProgramV3Codec.encode(outgoing));
        PacketDistributor.sendToServer(new SaveComputerGraphPayload(
                computerPos,
                expectedRevision,
                localRevision,
                envelope));
        saveInFlight = true;
        pendingPrograms.put(localRevision, outgoing);
        pendingHistoryRevisions.put(localRevision, historyRevision);
        inFlightEditorRevision = localRevision;
        saveEditorViewport();
    }

    private void saveEditorViewport() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            ComputerEditorViewState.save(
                    minecraft.player.getUUID(),
                    minecraft.level.dimension(),
                    computerPos,
                    editorViewportContextKey(),
                    editorPanX(),
                    editorPanY(),
                    editorZoom());
        }
    }
}
