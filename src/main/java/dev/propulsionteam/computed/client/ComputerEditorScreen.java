package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.client.editor.canvas.LuaEditorGraphAdapter;
import dev.propulsionteam.computed.client.editor.canvas.LuaEditorNode;
import dev.propulsionteam.computed.client.editor.explorer.ExplorerNode;
import dev.propulsionteam.computed.client.editor.explorer.ExplorerRow;
import dev.propulsionteam.computed.client.editor.explorer.NodeExplorerModel;
import dev.propulsionteam.computed.client.editor.TextInputHandler;
import dev.propulsionteam.computed.client.editor.lua.LuaNodeStarter;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import dev.propulsionteam.computed.network.SaveComputerGraphPayload;
import dev.propulsionteam.computed.network.ComputerEditPolicy;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import dev.propulsionteam.computed.persistence.ComputedProgramPackage;
import dev.propulsionteam.computed.persistence.ComputedPackageStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ComputerEditorScreen extends WNodeScreen {
    private static final int AUTO_SAVE_INTERVAL_TICKS = 20;
    private static final int EXPLORER_WIDTH = 168;
    private static final int EXPLORER_ROW_HEIGHT = 15;
    private static final String NEW_LUA_NODE_ACTION = "computed:editor/new_lua_node";
    private static final String COMMAND_NODE_ID = "computed:command";

    private final BlockPos computerPos;
    private WGraph editorGraph;
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
    private NodeExplorerModel explorer;
    private boolean explorerOpen = true;
    private boolean explorerSearchFocused;
    private String explorerSearch = "";
    private final TextInputHandler explorerSearchInput = new TextInputHandler(64);
    private int explorerAnchorX;
    private int explorerAnchorY;
    private int explorerScroll;
    private boolean contextOpen;
    private int contextX;
    private int contextY;
    private ExplorerNode explorerPressNode;
    private double explorerPressX;
    private double explorerPressY;
    private boolean explorerDragging;
    private ExplorerNode selectedExplorerNode;
    private int explorerHorizontalScroll;
    private Modal modal = Modal.NONE;
    private List<Path> packageChoices = List.of();
    private final TextInputHandler exportNameInput = new TextInputHandler(96);
    private Path pendingExport;
    private ExplorerNode contextExplorerNode;
    private int explorerContextX;
    private int explorerContextY;
    private boolean transferMenuOpen;

    private enum Modal { NONE, IMPORT_GRAPH, IMPORT_NODE, EXPORT_GRAPH_NAME, OVERWRITE_GRAPH, OVERWRITE_NODE, DELETE_NODE }

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
        super(editorGraph);
        this.computerPos = computerPos;
        this.editorGraph = editorGraph;
        this.serverRevision = serverRevision;
        baseProgram = program.withRevision(serverRevision);
        explorer = new NodeExplorerModel(explorerNodes(program));
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
    protected boolean minimalCanvasMode() {
        return true;
    }

    @Override
    protected void openNodeExplorer(int screenX, int screenY, int graphX, int graphY) {
        explorerAnchorX = graphX;
        explorerAnchorY = graphY;
        if (selectNodeAtGraphPoint(graphX, graphY) || hasSelectedNodes()) {
            contextOpen = true;
            contextX = screenX;
            contextY = screenY;
            return;
        }
        contextOpen = true;
        contextX = screenX;
        contextY = screenY;
    }

    @Override
    protected WNode createDuplicateNode(WNode source, int x, int y) {
        return source instanceof LuaEditorNode luaNode
                ? LuaEditorGraphAdapter.duplicateEditorNode(luaNode, x, y)
                : super.createDuplicateNode(source, x, y);
    }

    @Override
    protected boolean copySelectedNodesToClipboard() {
        var selectedIds = selectedCanvasNodeIds();
        if (selectedIds.isEmpty()) {
            return false;
        }
        try {
            ComputedProgramV3 fragment = ComputedProgramPackage.copySelection(
                    LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision),
                    selectedIds);
            minecraft.keyboardHandler.setClipboard(ComputedProgramPackage.exportClipboard(fragment));
        } catch (IllegalArgumentException exception) {
            notify("Could not copy nodes: " + exception.getMessage());
        }
        return true;
    }

    @Override
    protected boolean pasteNodesFromClipboard(int graphX, int graphY) {
        try {
            ComputedProgramV3 current =
                    LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision);
            ComputedProgramV3 fragment = ComputedProgramPackage.importClipboard(
                    minecraft.keyboardHandler.getClipboard());
            ComputedProgramPackage.PasteResult pasted =
                    ComputedProgramPackage.pasteSelection(current, fragment, graphX, graphY);
            String typeError =
                    ComputerEditPolicy.computerType(pasted.program(), isCreativeComputer());
            if (typeError != null) {
                throw new IllegalArgumentException(typeError);
            }
            baseProgram = pasted.program();
            editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
            replaceCanvasGraph(editorGraph);
            selectCanvasNodes(pasted.pastedNodeIds());
            explorer = new NodeExplorerModel(explorerNodes(baseProgram));
            acknowledgedEditorRevision = -1;
        } catch (IllegalArgumentException exception) {
            notify("Could not paste nodes: " + exception.getMessage());
        }
        return true;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 5000);
        if (explorerOpen) {
            renderExplorer(graphics, mouseX, mouseY);
        }
        renderControls(graphics, mouseX, mouseY);
        if (contextOpen) {
            renderContext(graphics, mouseX, mouseY);
        }
        if (contextExplorerNode != null) {
            renderExplorerContext(graphics, mouseX, mouseY);
        }
        if (transferMenuOpen) {
            renderTransferMenu(graphics, mouseX, mouseY);
        }
        if (modal != Modal.NONE) {
            renderModal(graphics, mouseX, mouseY);
        }
        if (explorerDragging && explorerPressNode != null) {
            graphics.fill(mouseX + 5, mouseY + 5, mouseX + 17, mouseY + 17, ComputedEditorTheme.ACCENT);
            graphics.drawString(
                    font,
                    explorerPressNode.title(),
                    mouseX + 21,
                    mouseY + 7,
                    ComputedEditorTheme.TEXT_HEADER,
                    true);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modal != Modal.NONE) return handleModalClick(mouseX, mouseY, button);
        if (isEditorModalOpen()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (explorerSearchFocused
                && !contains(mouseX, mouseY, 6, 27, EXPLORER_WIDTH - 12, 16)) {
            explorerSearchFocused = false;
            explorerSearchInput.blur();
        }
        if (button == 0 && handleControlsClick(mouseX, mouseY)) {
            return true;
        }
        if (transferMenuOpen) {
            return handleTransferMenuClick(mouseX, mouseY, button);
        }
        if (contextOpen) {
            return handleContextClick(mouseX, mouseY, button);
        }
        if (contextExplorerNode != null) {
            return handleExplorerContextClick(mouseX, mouseY, button);
        }
        if (explorerOpen && mouseX < EXPLORER_WIDTH) {
            return handleExplorerClick(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (modal != Modal.NONE) {
            return true;
        }
        if (button == 0 && explorerOpen && explorerSearchFocused) {
            explorerSearchInput.dragTo(searchColumn(mouseX));
            return true;
        }
        if (button == 0 && explorerPressNode != null) {
            if (Math.hypot(mouseX - explorerPressX, mouseY - explorerPressY) >= 4) {
                explorerDragging = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modal != Modal.NONE) {
            return true;
        }
        if (button == 0 && explorerPressNode != null) {
            ExplorerNode released = explorerPressNode;
            boolean dragged = explorerDragging;
            explorerPressNode = null;
            explorerDragging = false;
            if (dragged && mouseX >= EXPLORER_WIDTH) {
                explorerAnchorX = editorGraphX(mouseX);
                explorerAnchorY = editorGraphY(mouseY);
                place(released);
            } else if (!dragged) {
                explorerAnchorX = editorGraphX(width / 2.0);
                explorerAnchorY = editorGraphY(height / 2.0);
                place(released);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if (modal != Modal.NONE) {
            return true;
        }
        if (isEditorModalOpen()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (explorerOpen && mouseX < EXPLORER_WIDTH) {
            if (hasShiftDown()) {
                explorerHorizontalScroll = net.minecraft.util.Mth.clamp(explorerHorizontalScroll - (int) Math.signum(scrollY) * 12, 0, explorerMaxHorizontalScroll());
                return true;
            }
            int visible = Math.max(1, (height - 49) / EXPLORER_ROW_HEIGHT);
            explorerScroll = net.minecraft.util.Mth.clamp(
                    explorerScroll - (int) Math.signum(scrollY),
                    0,
                    Math.max(0, explorer.visibleRows().size() - visible));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modal != Modal.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeModal(); return true; }
            if (modal == Modal.EXPORT_GRAPH_NAME && exportNameInput.keyPressed(keyCode)) return true;
            if (modal == Modal.EXPORT_GRAPH_NAME && keyCode == GLFW.GLFW_KEY_ENTER) { beginGraphExport(); return true; }
            return true;
        }
        if (isEditorModalOpen()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (contextOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            contextOpen = false;
            return true;
        }
        if (transferMenuOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            transferMenuOpen = false;
            return true;
        }
        if (explorerOpen && explorerSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                explorerSearchFocused = false;
                explorerSearchInput.blur();
                return true;
            }
            if (explorerSearchInput.keyPressed(keyCode)) {
                updateExplorerSearch();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modal == Modal.EXPORT_GRAPH_NAME && exportNameInput.charTyped(codePoint)) return true;
        if (isEditorModalOpen()) {
            return super.charTyped(codePoint, modifiers);
        }
        if (explorerOpen
                && explorerSearchFocused
                && explorerSearchInput.charTyped(codePoint)) {
            updateExplorerSearch();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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

    String definitionHash(String id) {
        LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(id);
        return source == null ? "" : source.hash();
    }

    void applyLuaSource(
            String source,
            String id,
            boolean creationMode,
            int placementX,
            int placementY) {
        LuaDefinitionSource replacement = LuaDefinitionSource.embedded(1, id, source);
        ComputedProgramV3 current = LuaEditorGraphAdapter.fromEditorGraph(
                editorGraph,
                baseProgram,
                serverRevision);
        baseProgram = creationMode
                ? LuaEditorGraphAdapter.addDefinitionAndNode(
                        current,
                        replacement,
                        placementX,
                        placementY)
                : LuaEditorGraphAdapter.replaceDefinition(current, replacement);
        editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
        replaceCanvasGraph(editorGraph);
        explorer = new NodeExplorerModel(explorerNodes(baseProgram));
        explorerSearch = "";
        explorerSearchInput.setText("");
        explorer.search("");
        explorerScroll = 0;
        acknowledgedEditorRevision = -1;
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
                    EDITOR_VIEWPORT_ROOT,
                    editorPanX(),
                    editorPanY(),
                    editorZoom());
        }
    }

    private void renderControls(GuiGraphics graphics, int mouseX, int mouseY) {
        drawButton(graphics, 6, 6, 22, 18, explorerOpen ? "«" : "»", mouseX, mouseY);
        drawButton(graphics, width - 158, 6, 98, 18, "Import / Export", mouseX, mouseY);
        drawButton(graphics, width - 54, 6, 22, 18, "−", mouseX, mouseY);
        drawButton(graphics, width - 28, 6, 22, 18, "+", mouseX, mouseY);
    }

    private void renderExplorer(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, EXPLORER_WIDTH, height, 0xF2111111);
        graphics.vLine(EXPLORER_WIDTH - 1, 0, height, ComputedEditorTheme.BORDER_MENU);
        graphics.drawString(font, "Node Explorer", 34, 11, ComputedEditorTheme.TEXT_HEADER, false);
        int searchColor = explorerSearchFocused
                ? ComputedEditorTheme.ACCENT
                : ComputedEditorTheme.BORDER_DEFAULT;
        graphics.fill(6, 27, EXPLORER_WIDTH - 6, 43, ComputedEditorTheme.BACKGROUND_INPUT);
        graphics.renderOutline(6, 27, EXPLORER_WIDTH - 12, 16, searchColor);
        boolean cursorVisible = explorerSearchFocused
                && (System.currentTimeMillis() / 500) % 2 == 0;
        String searchText = explorerSearch.isEmpty() && !explorerSearchFocused
                ? "Search nodes"
                : explorerSearch;
        graphics.drawString(
                font,
                searchText,
                11,
                31,
                explorerSearch.isEmpty()
                        ? ComputedEditorTheme.TEXT_TERTIARY
                        : ComputedEditorTheme.TEXT_PRIMARY,
                false);
        if (explorerSearchFocused) {
            int start = explorerSearchInput.selectionStart();
            int end = explorerSearchInput.selectionEnd();
            if (start != end) {
                int startX = 11 + font.width(explorerSearch.substring(0, start));
                int endX = 11 + font.width(explorerSearch.substring(0, end));
                graphics.fill(startX, 29, endX, 41, ComputedEditorTheme.SELECTION_TEXT_BACKGROUND);
                graphics.drawString(font, explorerSearch, 11, 31, ComputedEditorTheme.TEXT_PRIMARY, false);
            }
            if (cursorVisible) {
                int cursorX = 11 + font.width(explorerSearch.substring(0, explorerSearchInput.cursor()));
                graphics.vLine(cursorX, 29, 40, ComputedEditorTheme.TEXT_HEADER);
            }
        }
        List<ExplorerRow> rows = explorer.visibleRows();
        explorerHorizontalScroll = net.minecraft.util.Mth.clamp(explorerHorizontalScroll, 0, explorerMaxHorizontalScroll());
        int visible = Math.max(1, (height - 49) / EXPLORER_ROW_HEIGHT);
        explorerScroll = net.minecraft.util.Mth.clamp(
                explorerScroll,
                0,
                Math.max(0, rows.size() - visible));
        String unavailableTooltip = "";
        graphics.enableScissor(0, 46, EXPLORER_WIDTH - 1, height - 4);
        for (int index = explorerScroll; index < Math.min(rows.size(), explorerScroll + visible); index++) {
            ExplorerRow row = rows.get(index);
            int y = 46 + (index - explorerScroll) * EXPLORER_ROW_HEIGHT;
            boolean hovered = mouseX >= 0
                    && mouseX < EXPLORER_WIDTH
                    && mouseY >= y
                    && mouseY < y + EXPLORER_ROW_HEIGHT;
            if (hovered) {
                graphics.fill(1, y, EXPLORER_WIDTH - 1, y + EXPLORER_ROW_HEIGHT, ComputedEditorTheme.MENU_HOVER);
            }
            int x = 8 + row.depth() * 12 - explorerHorizontalScroll;
            if (row.depth() > 0) {
                graphics.vLine(x - 5, y, y + EXPLORER_ROW_HEIGHT, ComputedEditorTheme.BORDER_SUBTLE);
            }
            if (row.folder()) {
                graphics.drawString(
                        font,
                        row.expanded() ? "▾" : "▸",
                        x,
                        y + 3,
                        ComputedEditorTheme.TEXT_SECONDARY,
                        false);
                graphics.drawString(
                        font,
                        row.label(),
                        x + 10,
                        y + 3,
                        ComputedEditorTheme.TEXT_PRIMARY,
                        false);
            } else {
                int color = row.node().available()
                        ? ComputedEditorTheme.ACCENT
                        : ComputedEditorTheme.TEXT_DISABLED;
                graphics.fill(x + 1, y + 4, x + 7, y + 10, color);
                graphics.drawString(
                        font,
                        row.label(),
                        x + 11,
                        y + 3,
                        row.node().available()
                                ? ComputedEditorTheme.TEXT_PRIMARY
                                : ComputedEditorTheme.TEXT_DISABLED,
                        false);
                if (hovered && !row.node().available()) {
                    unavailableTooltip = row.node().unavailableReason();
                }
            }
        }
        if (!unavailableTooltip.isEmpty()) {
            graphics.renderTooltip(font, Component.literal(unavailableTooltip), mouseX, mouseY);
        }
        graphics.disableScissor();
        int maximumHorizontal = explorerMaxHorizontalScroll();
        if (maximumHorizontal > 0) {
            int track = EXPLORER_WIDTH - 12;
            int thumb = Math.max(20, track * track / (track + maximumHorizontal));
            int thumbX = 6 + (track - thumb) * explorerHorizontalScroll / maximumHorizontal;
            graphics.fill(6, height - 4, 6 + track, height - 2, ComputedEditorTheme.BORDER_SUBTLE);
            graphics.fill(thumbX, height - 4, thumbX + thumb, height - 2, ComputedEditorTheme.BORDER_HIGHLIGHT);
        }
    }

    private void renderContext(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean selected = hasSelectedNodes();
        List<String> rows = selected
                ? List.of("Clone", "Unlink", "Delete")
                : List.of("Open Node Explorer", "New Lua Node…", "Paste Lua");
        int menuWidth = selected ? 92 : 132;
        int menuHeight = rows.size() * 18 + 4;
        int x = Math.min(contextX, width - menuWidth - 2);
        int y = Math.min(contextY, height - menuHeight - 2);
        graphics.fill(x, y, x + menuWidth, y + menuHeight, ComputedEditorTheme.MENU_BACKGROUND);
        graphics.renderOutline(x, y, menuWidth, menuHeight, ComputedEditorTheme.BORDER_MENU);
        for (int index = 0; index < rows.size(); index++) {
            int rowY = y + 2 + index * 18;
            if (mouseX >= x && mouseX < x + menuWidth && mouseY >= rowY && mouseY < rowY + 18) {
                graphics.fill(x + 1, rowY, x + menuWidth - 1, rowY + 18, ComputedEditorTheme.MENU_HOVER);
            }
            graphics.drawString(
                    font,
                    rows.get(index),
                    x + 7,
                    rowY + 5,
                    selected && index == 2
                            ? ComputedEditorTheme.STATUS_ERROR_TEXT
                            : ComputedEditorTheme.TEXT_PRIMARY,
                    false);
        }
    }

    private boolean handleControlsClick(double mouseX, double mouseY) {
        if (contains(mouseX, mouseY, 6, 6, 22, 18)) {
            explorerOpen = !explorerOpen;
            explorerSearchFocused = false;
            explorerSearchInput.blur();
            return true;
        }
        if (contains(mouseX, mouseY, width - 54, 6, 22, 18)) {
            transferMenuOpen = false;
            adjustEditorZoom(-0.1, width / 2.0, height / 2.0);
            return true;
        }
        if (contains(mouseX, mouseY, width - 28, 6, 22, 18)) {
            transferMenuOpen = false;
            adjustEditorZoom(0.1, width / 2.0, height / 2.0);
            return true;
        }
        if (contains(mouseX, mouseY, width - 158, 6, 98, 18)) {
            transferMenuOpen = !transferMenuOpen;
            contextOpen = false;
            contextExplorerNode = null;
            return true;
        }
        return false;
    }

    private boolean handleExplorerClick(double mouseX, double mouseY, int button) {
        if (contains(mouseX, mouseY, 6, 27, EXPLORER_WIDTH - 12, 16)) {
            if (button == 0) {
                explorerSearchFocused = true;
                if (!explorerSearchInput.focused()) {
                    explorerSearchInput.focus(explorerSearch);
                }
                explorerSearchInput.click(searchColumn(mouseX), hasShiftDown());
            }
            return true;
        }
        if (mouseY < 46 || mouseY >= height - 4) {
            return true;
        }
        List<ExplorerRow> rows = explorer.visibleRows();
        int index = explorerScroll + ((int) mouseY - 46) / EXPLORER_ROW_HEIGHT;
        if (index < 0 || index >= rows.size()) {
            return true;
        }
        ExplorerRow row = rows.get(index);
        if (!row.folder()) selectedExplorerNode = row.node();
        if (!row.folder()
                && row.node().available()
                && row.node().id().equals(NEW_LUA_NODE_ACTION)
                && button == 0) {
            explorerAnchorX = editorGraphX(width / 2.0);
            explorerAnchorY = editorGraphY(height / 2.0);
            openNewLuaNode(explorerAnchorX, explorerAnchorY);
            return true;
        }
        if (row.folder() && button == 0) {
            explorer.setExpanded(row.stablePath(), !row.expanded());
        } else if (!row.folder() && button == 1) {
            contextExplorerNode = row.node();
            explorerContextX = (int) mouseX;
            explorerContextY = (int) mouseY;
        } else if (!row.folder() && row.node().available() && button == 0) {
            explorerPressNode = row.node();
            explorerPressX = mouseX;
            explorerPressY = mouseY;
            explorerDragging = false;
        }
        return true;
    }

    private boolean handleContextClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            contextOpen = false;
            return true;
        }
        boolean selected = hasSelectedNodes();
        int menuWidth = selected ? 92 : 132;
        int rowCount = 3;
        int menuHeight = rowCount * 18 + 4;
        int x = Math.min(contextX, width - menuWidth - 2);
        int y = Math.min(contextY, height - menuHeight - 2);
        if (!contains(mouseX, mouseY, x, y, menuWidth, menuHeight)) {
            contextOpen = false;
            return true;
        }
        int row = ((int) mouseY - y - 2) / 18;
        contextOpen = false;
        if (selected) {
            if (row == 0) {
                cloneSelectedNodes();
            } else if (row == 1) {
                unlinkSelectedNodes();
            } else if (row == 2) {
                removeSelectedNodes();
            }
        } else if (row == 0) {
            explorerOpen = true;
            explorerSearchFocused = true;
        } else if (row == 1) {
            openNewLuaNode(explorerAnchorX, explorerAnchorY);
        } else if (row == 2) {
            openLuaFromClipboard();
        }
        return true;
    }

    private void place(ExplorerNode node) {
        if (node == null || !node.available()) {
            return;
        }
        if (node.id().equals(NEW_LUA_NODE_ACTION)) {
            explorerAnchorX = editorGraphX(width / 2.0);
            explorerAnchorY = editorGraphY(height / 2.0);
            openNewLuaNode(explorerAnchorX, explorerAnchorY);
            return;
        }
        LuaEditorNode placed = LuaEditorGraphAdapter.createEditorNode(
                baseProgram,
                node.id(),
                explorerAnchorX,
                explorerAnchorY);
        addNodeToCanvas(placed);
    }

    private void openLuaFromClipboard() {
        String source;
        try {
            source = dev.propulsionteam.computed.persistence.LuaDefinitionClipboard.importSource(
                    minecraft.keyboardHandler.getClipboard());
        } catch (IllegalArgumentException exception) {
            source = """
                    local node = computed.node(1, "example:new_node", "New Node")

                    node:category("lua")
                    node:input("value", "number", { default = 0 })
                    node:output("result", "number")
                    node:on_run(function(ctx)
                        ctx:output("result", ctx:input("value"))
                    end)

                    return node
                    """;
        }
        minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source));
    }

    private void openNewLuaNode(int x, int y) {
        LuaNodeStarter.Starter starter = LuaNodeStarter.create();
        minecraft.setScreen(new LuaNodeEditorScreen(
                this,
                baseProgram,
                starter.source(),
                true,
                x,
                y));
    }

    private void exportSelectedNode() {
        if (selectedExplorerNode == null || selectedExplorerNode.id().equals(NEW_LUA_NODE_ACTION)) {
            notify("Select a user node to export");
            return;
        }
        LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(selectedExplorerNode.id());
        if (source == null || source.origin() != LuaDefinitionSource.Origin.EMBEDDED) {
            notify("Only user nodes can be exported");
            return;
        }
        try {
            pendingExport = storage().target(ComputedPackageStorage.Kind.NODES, selectedExplorerNode.title());
            if (Files.exists(pendingExport)) { modal = Modal.OVERWRITE_NODE; return; }
            writeNodeExport(pendingExport, source);
        } catch (IOException | IllegalArgumentException exception) { notify("Node export failed: " + exception.getMessage()); }
    }

    private void importGraph(Path file) {
        try {
            ComputedProgramV3 imported = ComputedProgramPackage.importArchive(Files.readAllBytes(file));
            String typeError =
                    ComputerEditPolicy.computerType(imported, isCreativeComputer());
            if (typeError != null) {
                throw new IllegalArgumentException(typeError);
            }
            baseProgram = imported.withRevision(serverRevision);
            editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
            replaceCanvasGraph(editorGraph);
            explorer = new NodeExplorerModel(explorerNodes(baseProgram));
            selectedExplorerNode = null;
            explorerScroll = 0;
            acknowledgedEditorRevision = -1;
            notify("Computed graph imported");
        } catch (IOException | IllegalArgumentException exception) {
            notify("Graph import failed: " + exception.getMessage());
        }
    }

    private void importNode(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source));
        } catch (IOException | IllegalArgumentException exception) { notify("Node import failed: " + exception.getMessage()); }
    }

    private ComputedPackageStorage storage() { return new ComputedPackageStorage(minecraft.gameDirectory.toPath()); }

    private void writeNodeExport(Path target, LuaDefinitionSource source) throws IOException {
        Files.writeString(target, source.source(), StandardCharsets.UTF_8);
        notify("Node exported as " + target.getFileName());
    }

    private void openImport(Modal type, ComputedPackageStorage.Kind kind) {
        try { packageChoices = storage().list(kind); modal = type; }
        catch (IOException exception) { notify("Could not open " + kind.name().toLowerCase() + ": " + exception.getMessage()); }
    }

    private void beginGraphExport() {
        try {
            pendingExport = storage().target(ComputedPackageStorage.Kind.GRAPHS, exportNameInput.text());
            if (Files.exists(pendingExport)) { modal = Modal.OVERWRITE_GRAPH; return; }
            writeGraphExport(pendingExport);
            closeModal();
        } catch (IOException | IllegalArgumentException exception) { notify("Graph export failed: " + exception.getMessage()); }
    }

    private void writeGraphExport(Path target) throws IOException {
        Files.write(target, ComputedProgramPackage.exportArchive(
                LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision)));
        notify("Graph exported as " + target.getFileName());
    }

    private void closeModal() { modal = Modal.NONE; packageChoices = List.of(); pendingExport = null; exportNameInput.blur(); }

    private void renderTransferMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int menuWidth = 132;
        int menuHeight = 76;
        int x = Math.max(2, width - menuWidth - 60);
        int y = 26;
        boolean canExportNode = canExportSelectedNode();
        List<String> rows = List.of("Import Graph", "Export Graph", "Import Node", "Export Selected Node");
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, menuWidth, menuHeight);
        for (int index = 0; index < rows.size(); index++) {
            int rowY = y + 2 + index * 18;
            boolean enabled = index != 3 || canExportNode;
            boolean hovered = enabled && contains(mouseX, mouseY, x, rowY, menuWidth, 18);
            ComputedEditorStyle.drawMenuRow(graphics, x, rowY, menuWidth, 18, hovered, false);
            graphics.drawString(
                    font,
                    rows.get(index),
                    x + 7,
                    rowY + 5,
                    enabled ? ComputedEditorTheme.TEXT_PRIMARY : ComputedEditorTheme.TEXT_DISABLED,
                    false);
        }
    }

    private boolean handleTransferMenuClick(double mouseX, double mouseY, int button) {
        int menuWidth = 132;
        int menuHeight = 76;
        int x = Math.max(2, width - menuWidth - 60);
        int y = 26;
        if (button != 0 || !contains(mouseX, mouseY, x, y, menuWidth, menuHeight)) {
            transferMenuOpen = false;
            return true;
        }
        int row = ((int) mouseY - y - 2) / 18;
        if (row < 0
                || row >= 4
                || !contains(mouseX, mouseY, x, y + 2 + row * 18, menuWidth, 18)) {
            transferMenuOpen = false;
            return true;
        }
        transferMenuOpen = false;
        if (row == 0) {
            openImport(Modal.IMPORT_GRAPH, ComputedPackageStorage.Kind.GRAPHS);
        } else if (row == 1) {
            exportNameInput.focus("New Graph");
            modal = Modal.EXPORT_GRAPH_NAME;
        } else if (row == 2) {
            openImport(Modal.IMPORT_NODE, ComputedPackageStorage.Kind.NODES);
        } else if (row == 3 && canExportSelectedNode()) {
            exportSelectedNode();
        }
        return true;
    }

    private boolean canExportSelectedNode() {
        LuaDefinitionSource source = selectedSource();
        return selectedExplorerNode != null
                && !selectedExplorerNode.id().equals(NEW_LUA_NODE_ACTION)
                && source != null
                && source.origin() == LuaDefinitionSource.Origin.EMBEDDED;
    }

    private void renderModal(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, ComputedEditorTheme.BACKGROUND_MODAL_SCRIM);
        int boxWidth = Math.min(300, width - 24);
        int x = (width - boxWidth) / 2;
        int y = Math.max(36, height / 4);
        int rows = importChoiceRows();
        int boxHeight = modalHeight(rows);
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, boxWidth, boxHeight);
        String title = switch (modal) {
            case IMPORT_GRAPH -> "Import Graph";
            case IMPORT_NODE -> "Import Node";
            case EXPORT_GRAPH_NAME -> "Export Graph";
            case OVERWRITE_GRAPH, OVERWRITE_NODE -> "File already exists";
            case DELETE_NODE -> "Delete user node";
            default -> "";
        };
        graphics.drawString(font, title, x + 8, y + 8, ComputedEditorTheme.TEXT_HEADER, false);
        if (modal == Modal.IMPORT_GRAPH || modal == Modal.IMPORT_NODE) {
            int listHeight = importListHeight(rows);
            ComputedEditorStyle.drawField(
                    graphics, x + 6, y + 24, boxWidth - 12, listHeight, false, false);
            if (packageChoices.isEmpty()) {
                graphics.drawString(
                        font,
                        modal == Modal.IMPORT_GRAPH
                                ? "No .computed files found"
                                : "No .lua files found",
                        x + 11,
                        y + 32,
                        ComputedEditorTheme.TEXT_SECONDARY,
                        false);
            }
            for (int index = 0; index < rows; index++) {
                int rowY = y + 26 + index * 18;
                ComputedEditorStyle.drawMenuRow(
                        graphics,
                        x + 7,
                        rowY,
                        boxWidth - 14,
                        18,
                        contains(mouseX, mouseY, x + 7, rowY, boxWidth - 14, 18),
                        false);
                graphics.drawString(
                        font,
                        packageChoices.get(index).getFileName().toString(),
                        x + 11,
                        rowY + 5,
                        ComputedEditorTheme.TEXT_PRIMARY,
                        false);
            }
        } else if (modal == Modal.EXPORT_GRAPH_NAME) {
            ComputedEditorStyle.drawField(
                    graphics,
                    x + 8,
                    y + 27,
                    boxWidth - 16,
                    18,
                    exportNameInput.focused(),
                    contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18));
            graphics.drawString(font, exportNameInput.text(), x + 12, y + 32, ComputedEditorTheme.TEXT_PRIMARY, false);
        } else if (modal == Modal.DELETE_NODE) {
            graphics.drawString(font, "Delete definition and all its instances?", x + 8, y + 28, ComputedEditorTheme.TEXT_SECONDARY, false);
        } else {
            int firstRowY = y + 24;
            int secondRowY = y + 42;
            ComputedEditorStyle.drawMenuRow(
                    graphics, x + 4, firstRowY, boxWidth - 8, 18,
                    contains(mouseX, mouseY, x + 4, firstRowY, boxWidth - 8, 18), false);
            ComputedEditorStyle.drawMenuRow(
                    graphics, x + 4, secondRowY, boxWidth - 8, 18,
                    contains(mouseX, mouseY, x + 4, secondRowY, boxWidth - 8, 18), false);
            graphics.drawString(font, "Overwrite existing file", x + 8, firstRowY + 5, ComputedEditorTheme.STATUS_WARNING_TEXT, false);
            graphics.drawString(
                    font,
                    "Save with next available name",
                    x + 8,
                    secondRowY + 5,
                    ComputedEditorTheme.TEXT_PRIMARY,
                    false);
        }
        int cancelX = x + boxWidth - 66;
        int buttonY = y + boxHeight - 24;
        drawButton(graphics, cancelX, buttonY, 58, 18, "Cancel", mouseX, mouseY);
        if (modal == Modal.EXPORT_GRAPH_NAME) {
            drawButton(graphics, cancelX - 62, buttonY, 58, 18, "Save", mouseX, mouseY);
        } else if (modal == Modal.DELETE_NODE) {
            boolean hovered = contains(mouseX, mouseY, x + 8, buttonY, 58, 18);
            ComputedEditorStyle.drawDangerButton(graphics, x + 8, buttonY, 58, 18, hovered);
            ComputedEditorStyle.drawCenteredString(
                    graphics, font, "Delete", x + 8, buttonY, 58, 18, ComputedEditorTheme.STATUS_ERROR_TEXT);
        }
    }

    private boolean handleModalClick(double mouseX, double mouseY, int button) {
        if (button != 0) { closeModal(); return true; }
        int boxWidth = Math.min(300, width - 24);
        int x = (width - boxWidth) / 2;
        int y = Math.max(36, height / 4);
        int rows = importChoiceRows();
        int boxHeight = modalHeight(rows);
        if (!contains(mouseX, mouseY, x, y, boxWidth, boxHeight)) {
            closeModal();
            return true;
        }
        int cancelX = x + boxWidth - 66;
        int buttonY = y + boxHeight - 24;
        if (contains(mouseX, mouseY, cancelX, buttonY, 58, 18)) {
            closeModal();
            return true;
        }
        if (modal == Modal.IMPORT_GRAPH || modal == Modal.IMPORT_NODE) {
            int index = ((int) mouseY - y - 26) / 18;
            if (index >= 0
                    && index < rows
                    && contains(mouseX, mouseY, x + 7, y + 26 + index * 18, boxWidth - 14, 18)) {
                Path selected = packageChoices.get(index);
                Modal type = modal;
                closeModal();
                if (type == Modal.IMPORT_GRAPH) importGraph(selected);
                else importNode(selected);
            }
        } else if (modal == Modal.EXPORT_GRAPH_NAME) {
            if (contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18)) {
                exportNameInput.click(exportNameColumn(mouseX, x + 12), hasShiftDown());
            } else if (contains(mouseX, mouseY, cancelX - 62, buttonY, 58, 18)) {
                beginGraphExport();
            }
        } else if (modal == Modal.OVERWRITE_GRAPH || modal == Modal.OVERWRITE_NODE) {
            boolean overwrite = contains(mouseX, mouseY, x + 4, y + 24, boxWidth - 8, 18);
            boolean suffix = contains(mouseX, mouseY, x + 4, y + 42, boxWidth - 8, 18);
            if (overwrite || suffix) {
                completePendingExport(overwrite);
            }
        } else if (modal == Modal.DELETE_NODE
                && contains(mouseX, mouseY, x + 8, buttonY, 58, 18)) {
            deleteSelectedNode();
            closeModal();
        }
        return true;
    }

    private void completePendingExport(boolean overwrite) {
        try {
            Path target = pendingExport;
            if (!overwrite) {
                ComputedPackageStorage.Kind kind = modal == Modal.OVERWRITE_GRAPH
                        ? ComputedPackageStorage.Kind.GRAPHS
                        : ComputedPackageStorage.Kind.NODES;
                String extension = kind == ComputedPackageStorage.Kind.GRAPHS
                        ? "(?i)\\.computed$"
                        : "(?i)\\.lua$";
                target = storage().nextAvailable(
                        kind,
                        pendingExport.getFileName().toString().replaceFirst(extension, ""));
            }
            if (modal == Modal.OVERWRITE_GRAPH) writeGraphExport(target);
            else writeNodeExport(target, selectedSource());
        } catch (IOException exception) {
            notify("Export failed: " + exception.getMessage());
        }
        closeModal();
    }

    private int importChoiceRows() {
        return modal == Modal.IMPORT_GRAPH || modal == Modal.IMPORT_NODE
                ? Math.min(8, packageChoices.size())
                : 0;
    }

    private int modalHeight(int importRows) {
        if (modal == Modal.EXPORT_GRAPH_NAME || modal == Modal.DELETE_NODE) return 82;
        if (modal == Modal.OVERWRITE_GRAPH || modal == Modal.OVERWRITE_NODE) return 94;
        return importListHeight(importRows) + 52;
    }

    private int importListHeight(int rows) {
        return Math.max(24, rows * 18 + 4);
    }

    private int exportNameColumn(double mouseX, int textX) {
        int relative = (int) mouseX - textX;
        int measuredWidth = 0;
        String text = exportNameInput.text();
        for (int index = 0; index < text.length(); index++) {
            int characterWidth = font.width(text.substring(index, index + 1));
            if (relative < measuredWidth + characterWidth / 2) {
                return index;
            }
            measuredWidth += characterWidth;
        }
        return text.length();
    }

    private LuaDefinitionSource selectedSource() { return selectedExplorerNode == null ? null : LuaEditorGraphAdapter.definitions(baseProgram).get(selectedExplorerNode.id()); }

    private void renderExplorerContext(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = Math.max(2, Math.min(explorerContextX, width - 94));
        int y = Math.max(2, Math.min(explorerContextY, height - 60));
        boolean user = contextExplorerNode.ownership() == ExplorerNode.Ownership.USER && contextExplorerNode.available();
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, 92, 58);
        String[] labels = {user ? "Edit" : "View", "Export", "Delete"};
        for (int index = 0; index < labels.length; index++) {
            int rowY = y + 2 + index * 18;
            boolean enabled = index == 0 ? contextExplorerNode.available() : user;
            ComputedEditorStyle.drawMenuRow(
                    graphics,
                    x,
                    rowY,
                    92,
                    18,
                    enabled && contains(mouseX, mouseY, x, rowY, 92, 18),
                    false);
            int color = !enabled
                    ? ComputedEditorTheme.TEXT_DISABLED
                    : index == 2 ? ComputedEditorTheme.STATUS_ERROR_TEXT : ComputedEditorTheme.TEXT_PRIMARY;
            graphics.drawString(font, labels[index], x + 7, rowY + 5, color, false);
        }
    }

    private boolean handleExplorerContextClick(double mouseX, double mouseY, int button) {
        ExplorerNode node = contextExplorerNode;
        int x = Math.max(2, Math.min(explorerContextX, width - 94));
        int y = Math.max(2, Math.min(explorerContextY, height - 60));
        contextExplorerNode = null;
        if (button != 0 || !contains(mouseX, mouseY, x, y, 92, 58)) return true;
        int row = ((int) mouseY - y - 2) / 18;
        if (row < 0
                || row >= 3
                || !contains(mouseX, mouseY, x, y + 2 + row * 18, 92, 18)) {
            return true;
        }
        LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(node.id());
        if (row == 0 && source != null) minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source.source(), source.origin(), false, 0, 0));
        else if (row == 1 && node.ownership() == ExplorerNode.Ownership.USER) {
            selectedExplorerNode = node;
            exportSelectedNode();
        } else if (row == 2 && node.ownership() == ExplorerNode.Ownership.USER) {
            selectedExplorerNode = node;
            modal = Modal.DELETE_NODE;
        }
        return true;
    }

    private void deleteSelectedNode() {
        if (selectedExplorerNode == null) return;
        String id = selectedExplorerNode.id();
        ComputedProgramV3 current = LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision);
        baseProgram = LuaEditorGraphAdapter.removeDefinitionAndInstances(current, id);
        editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram); replaceCanvasGraph(editorGraph);
        explorer = new NodeExplorerModel(explorerNodes(baseProgram)); selectedExplorerNode = null; acknowledgedEditorRevision = -1;
        notify("Deleted user node " + id);
    }

    private void notify(String message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private int searchColumn(double mouseX) {
        int relative = (int) mouseX - 11;
        int width = 0;
        for (int index = 0; index < explorerSearch.length(); index++) {
            int characterWidth = font.width(explorerSearch.substring(index, index + 1));
            if (relative < width + characterWidth / 2) {
                return index;
            }
            width += characterWidth;
        }
        return explorerSearch.length();
    }

    private int explorerMaxHorizontalScroll() {
        int widest = explorer.visibleRows().stream()
                .mapToInt(row -> 8 + row.depth() * 12 + font.width(row.label()) + (row.folder() ? 10 : 11))
                .max().orElse(EXPLORER_WIDTH - 12);
        return Math.max(0, widest - (EXPLORER_WIDTH - 12));
    }

    private void updateExplorerSearch() {
        explorerSearch = explorerSearchInput.text();
        explorer.search(explorerSearch);
        explorerScroll = 0;
    }

    private static void drawButton(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            String text,
            int mouseX,
            int mouseY) {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        ComputedEditorStyle.drawButton(graphics, x, y, width, height, hovered, false);
        ComputedEditorStyle.drawCenteredString(
                graphics,
                Minecraft.getInstance().font,
                text,
                x,
                y,
                width,
                height,
                ComputedEditorTheme.TEXT_PRIMARY);
    }

    private static boolean contains(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isCreativeComputer() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null
                && client.level.getBlockState(computerPos)
                        .is(ComputedRegistries.CREATIVE_COMPUTER_BLOCK.get());
    }

    private List<ExplorerNode> explorerNodes(ComputedProgramV3 program) {
        List<ExplorerNode> nodes = new ArrayList<>();
        nodes.add(new ExplorerNode(
                NEW_LUA_NODE_ACTION,
                "New Lua Node…",
                ExplorerNode.Ownership.USER,
                List.of(),
                true,
                ""));
        LuaSourceCompiler compiler = new LuaSourceCompiler();
        LuaDefinitionLoader loader = new LuaDefinitionLoader();
        LuaSandbox sandbox = new LuaSandbox();
        LuaEditorGraphAdapter.definitions(program).forEach((id, source) -> {
            if (id.equals(COMMAND_NODE_ID) && !isCreativeComputer()) {
                return;
            }
            try {
                var definition = loader.load(compiler.compile(source.apiVersion(), source.source()), sandbox);
                ExplorerNode.Ownership ownership = switch (source.origin()) {
                    case BUNDLED -> ExplorerNode.Ownership.BUNDLED;
                    case INTEGRATION -> ExplorerNode.Ownership.INTEGRATION;
                    case EMBEDDED -> ExplorerNode.Ownership.USER;
                };
                List<String> path = java.util.Arrays.stream(definition.category().split("/"))
                        .filter(segment -> !segment.isBlank())
                        .toList();
                String unavailableReason = source.origin() == LuaDefinitionSource.Origin.INTEGRATION
                        ? dev.propulsionteam.computed.lua.node.IntegrationLuaLibrary.unavailableReason(id)
                        : "";
                nodes.add(new ExplorerNode(
                        id,
                        definition.title(),
                        ownership,
                        path,
                        unavailableReason.isEmpty(),
                        unavailableReason));
            } catch (RuntimeException ignored) {
                nodes.add(new ExplorerNode(
                        id,
                        id,
                        source.origin() == LuaDefinitionSource.Origin.INTEGRATION
                                ? ExplorerNode.Ownership.INTEGRATION
                                : ExplorerNode.Ownership.USER,
                        List.of("invalid"),
                        false,
                        "Definition could not be compiled"));
            }
        });
        return List.copyOf(nodes);
    }
}
