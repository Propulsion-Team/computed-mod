package dev.propulsionteam.computed.client;

import dev.devce.websnodelib.api.FunctionDefinitionStore;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.client.ui.WNodeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.network.SaveComputerGraphPayload;
import java.nio.file.Path;
import java.util.List;
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

    public ComputerEditorScreen(
            BlockPos computerPos,
            WGraph graph,
            FunctionDefinitionStore functionStore,
            Set<ResourceLocation> peripheralUnlock,
            List<Component> placedPeripheralHud) {
        super(graph, functionStore, Peripherals.hardwareMissingPredicate(peripheralUnlock));
        this.computerPos = computerPos;
        this.editorGraph = graph;
        this.peripheralUnlock = Set.copyOf(peripheralUnlock);
        this.placedPeripheralHud = List.copyOf(placedPeripheralHud);
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

    private CompoundTag bundleForNetwork() {
        functionStore.syncBodiesFromGraph(editorGraph);
        CompoundTag bundle = new CompoundTag();
        bundle.put("ComputerGraph", editorGraph.save());
        bundle.put("ComputerFunctions", functionStore.saveList());
        return bundle;
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
            PacketDistributor.sendToServer(new SaveComputerGraphPayload(computerPos, bundleForNetwork()));
            saveEditorViewportIfPossible();
        }
    }

    @Override
    public void removed() {
        saveEditorViewportIfPossible();
        super.removed();
        PacketDistributor.sendToServer(new SaveComputerGraphPayload(computerPos, bundleForNetwork()));
    }
}
