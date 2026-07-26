package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.client.editor.canvas.LuaEditorGraphAdapter;
import dev.propulsionteam.computed.client.editor.canvas.LuaEditorNode;
import dev.propulsionteam.computed.client.editor.explorer.ExplorerNode;
import dev.propulsionteam.computed.client.editor.explorer.ExplorerRow;
import dev.propulsionteam.computed.client.editor.explorer.NodeExplorerModel;
import dev.propulsionteam.computed.client.editor.lua.LuaNodeStarter;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import dev.propulsionteam.computed.network.SaveComputerGraphPayload;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
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
    private static final int EXPLORER_WIDTH = 224;
    private static final int EXPLORER_ROW_HEIGHT = 15;
    private static final String NEW_LUA_NODE_ACTION = "computed:editor/new_lua_node";

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
        renderControls(graphics, mouseX, mouseY);
        if (explorerOpen) {
            renderExplorer(graphics, mouseX, mouseY);
        }
        if (contextOpen) {
            renderContext(graphics, mouseX, mouseY);
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
        if (isEditorModalOpen()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && handleControlsClick(mouseX, mouseY)) {
            return true;
        }
        if (contextOpen) {
            return handleContextClick(mouseX, mouseY, button);
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
        if (isEditorModalOpen()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (explorerOpen && mouseX < EXPLORER_WIDTH) {
            int visible = Math.max(1, (height - 45) / EXPLORER_ROW_HEIGHT);
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
        if (isEditorModalOpen()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (contextOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            contextOpen = false;
            return true;
        }
        if (explorerOpen && explorerSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                explorerSearchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !explorerSearch.isEmpty()) {
                explorerSearch = explorerSearch.substring(0, explorerSearch.length() - 1);
                explorer.search(explorerSearch);
                explorerScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                explorer.moveSelection(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                ExplorerRow selected = explorer.selected();
                if (selected != null) {
                    if (selected.folder()) {
                        explorer.toggleSelected();
                    } else {
                        place(selected.node());
                    }
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isEditorModalOpen()) {
            return super.charTyped(codePoint, modifiers);
        }
        if (explorerOpen
                && explorerSearchFocused
                && !Character.isISOControl(codePoint)
                && explorerSearch.length() < 64) {
            explorerSearch += codePoint;
            explorer.search(explorerSearch);
            explorerScroll = 0;
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
        drawButton(graphics, width - 54, 6, 22, 18, "−", mouseX, mouseY);
        drawButton(graphics, width - 28, 6, 22, 18, "+", mouseX, mouseY);
    }

    private void renderExplorer(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, EXPLORER_WIDTH, height, 0xF2111111);
        graphics.vLine(EXPLORER_WIDTH - 1, 0, height, ComputedEditorTheme.BORDER_MENU);
        graphics.drawString(font, "Node Explorer", 34, 11, ComputedEditorTheme.TEXT_HEADER, false);
        int searchColor = explorerSearchFocused
                ? ComputedEditorTheme.BORDER_HIGHLIGHT
                : ComputedEditorTheme.BORDER_DEFAULT;
        graphics.fill(6, 27, EXPLORER_WIDTH - 6, 43, ComputedEditorTheme.BACKGROUND_INPUT);
        graphics.renderOutline(6, 27, EXPLORER_WIDTH - 12, 16, searchColor);
        String searchText = explorerSearch.isEmpty() && !explorerSearchFocused
                ? "Search nodes"
                : explorerSearch + (explorerSearchFocused ? "_" : "");
        graphics.drawString(
                font,
                searchText,
                11,
                31,
                explorerSearch.isEmpty()
                        ? ComputedEditorTheme.TEXT_TERTIARY
                        : ComputedEditorTheme.TEXT_PRIMARY,
                false);
        List<ExplorerRow> rows = explorer.visibleRows();
        int visible = Math.max(1, (height - 45) / EXPLORER_ROW_HEIGHT);
        explorerScroll = net.minecraft.util.Mth.clamp(
                explorerScroll,
                0,
                Math.max(0, rows.size() - visible));
        String unavailableTooltip = "";
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
            int x = 8 + row.depth() * 12;
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
            return true;
        }
        if (contains(mouseX, mouseY, width - 54, 6, 22, 18)) {
            adjustEditorZoom(-0.1, width / 2.0, height / 2.0);
            return true;
        }
        if (contains(mouseX, mouseY, width - 28, 6, 22, 18)) {
            adjustEditorZoom(0.1, width / 2.0, height / 2.0);
            return true;
        }
        return false;
    }

    private boolean handleExplorerClick(double mouseX, double mouseY, int button) {
        if (contains(mouseX, mouseY, 6, 27, EXPLORER_WIDTH - 12, 16)) {
            if (button == 0) {
                explorerSearchFocused = true;
            }
            return true;
        }
        if (mouseY < 46) {
            return true;
        }
        List<ExplorerRow> rows = explorer.visibleRows();
        int index = explorerScroll + ((int) mouseY - 46) / EXPLORER_ROW_HEIGHT;
        if (index < 0 || index >= rows.size()) {
            return true;
        }
        ExplorerRow row = rows.get(index);
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
        } else if (!row.folder() && row.node().available() && button == 1) {
            LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(row.node().id());
            if (source != null) {
                minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source.source()));
            }
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
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                hovered ? ComputedEditorTheme.BUTTON_HOVER : ComputedEditorTheme.BUTTON_BACKGROUND);
        graphics.renderOutline(x, y, width, height, ComputedEditorTheme.BORDER_MENU);
        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                text,
                x + width / 2,
                y + 5,
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

    private static List<ExplorerNode> explorerNodes(ComputedProgramV3 program) {
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
