/** https://github.com/webyep-art/webs_node_lib (MIT, webyep). */
package dev.devce.websnodelib.client.ui;

import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.FunctionDefinitionStore;
import dev.devce.websnodelib.api.UiKeyTextures;
import dev.devce.websnodelib.api.FunctionEndNode;
import dev.devce.websnodelib.api.FunctionStartNode;
import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.WConnection;
import dev.propulsionteam.computed.client.ComputedGraphShareCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import java.util.Base64;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.lwjgl.glfw.GLFW;

/**
 * The main GUI screen for editing node graphs.
 * Supports zooming, panning, multiple node selection, and real-time data flow visualization.
 */
public class WNodeScreen extends Screen {
    /**
     * Client viewport persistence key for the main (non-nested) graph. Inner function bodies use
     * {@link java.util.UUID#toString()} of the {@link FunctionCardNode} id.
     */
    public static final String EDITOR_VIEWPORT_ROOT = "root";

    /** Inset from screen edges so the editor is not fullscreen; world stays visible around it. */
    private static final int VIEW_INSET_NORMAL = 40;
    private static final float OPEN_DURATION_SEC = 0.55f;
    /** Grid lines stay this many GUI pixels apart on screen regardless of zoom or window size. */
    private static final float GRID_SPACING_SCREEN_PX = 20f;
    /** Target minimum stroke width on screen (px); grows in graph space when zoomed out so lines stay visible. */
    private static final float GRID_LINE_WIDTH_SCREEN_PX = 1.35f;

    /** Fills the window; inset becomes 0. Always on — windowed mode just hides content for no gain. */
    private final boolean editorFullscreen = true;
    private static final int FULLSCREEN_BTN = 22;
    private static final int FULLSCREEN_BTN_PAD = 6;
    private static final int CENTER_BTN_W = 96;
    private static final int CENTER_BTN_H = 22;
    private static final int ICON_SIZE = 16;
    private static final ResourceLocation ICON_DUPLICATE =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/duplicate.png");
    private static final ResourceLocation ICON_DELETE =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/delete.png");
    private static final ResourceLocation ICON_DISCONNECT =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/disconnect.png");
    private static final ResourceLocation ICON_MAXIMIZE =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/maximize.png");
    private static final ResourceLocation ICON_MINIMIZE =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/minimize.png");
    /** Sidebar visible: collapse. Sidebar hidden: expand. */
    private static final ResourceLocation ICON_SIDEBAR_OPEN =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/sidebar_open.png");
    private static final ResourceLocation ICON_SIDEBAR_CLOSED =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/sidebar_closed.png");
    /** Functions / library picker (computer editor only when {@link #functionStore} is non-null). */
    private static final ResourceLocation ICON_SCHEMATIC =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/schematic.png");
    private static final ResourceLocation ICON_PLAY =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/play.png");
    private static final ResourceLocation ICON_PAUSE =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/pause.png");
    private static final ResourceLocation ICON_SAVE_DISK =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/save_multicolor.png");
    private static final ResourceLocation ICON_FOLDER =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/folder_multicolor.png");
    private static final ResourceLocation ICON_UPLOAD =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/upload_multicolor.png");
    private static final ResourceLocation ICON_EXPORT =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/export.png");
    private static final ResourceLocation ICON_SCROLLER_MULTICOLOR =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/scroller_multicolor.png");
    private static final ResourceLocation ICON_SCROLLER_DISABLED =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/scroller_disabled.png");
    private static final ResourceLocation ICON_UI_CLICK =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/click.png");
    private static final ResourceLocation ICON_UI_DOUBLE_CLICK =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/double_click.png");
    private static final ResourceLocation KEY_CAP_ALT = UiKeyTextures.key("alt");
    private static final ResourceLocation KEY_CAP_DEL = UiKeyTextures.key("del");
    private static final ResourceLocation KEY_CAP_X = UiKeyTextures.key("x");
    private static final ResourceLocation KEY_CAP_ESC = UiKeyTextures.key("esc");
    private static final ResourceLocation SECTION_TOOL_TYPE =
            ResourceLocation.fromNamespaceAndPath("websnodelib", "tool_section");

    /** Current editing graph (may be a nested {@link FunctionCardNode}'s inner graph). */
    private WGraph graph;

    /** Null outside {@link dev.propulsionteam.computed.client.ComputerEditorScreen}. */
    protected final FunctionDefinitionStore functionStore;

    /**
     * When non-null, {@link #isEditorPeripheralLocked} is true if this predicate holds for the node type id
     * (Computed: peripheral item not in computer inventory). Null in standalone / demo editor.
     */
    private final Predicate<ResourceLocation> editorPeripheralLocked;

    private boolean functionPickerOpen;

    /** Nested function body: live simulation while editing (client preview). */
    private boolean nestedFunctionTestPlaying;

    /** Client {@code config/.../functions/*.nbt} list for the schematic dropdown import row. */
    /** First visible row in the definitions list (5 rows viewport). */
    private int functionLibraryListScroll;
    /** First visible row in the import flyout (5 rows max). */
    private int functionDiscImportListScroll;
    private boolean functionImportSubmenuOpen;
    private final List<Path> functionDiscImportFiles = new ArrayList<>();

    /** Function library row selection / rename (schematic dropdown). */
    private UUID selectedLibraryFunctionId = null;
    private UUID renamingLibraryFunctionId = null;
    private String libraryFnRenameBuffer = "";
    private int libraryFnRenameCursor = 0;
    private int libraryFnRenameSelectionPos = 0;
    private long lastLibraryFunctionClickAtMs = 0;
    private UUID lastLibraryFunctionClickId = null;

    /** Naming overlay after "+ New function" (computer editor only). */
    private boolean newFunctionNamingOpen;
    private String newFunctionNameBuffer = "";
    private boolean exportDialogOpen;
    private String exportDialogText = "";
    private boolean importDialogOpen;
    private String importDialogText = "";
    private String importDialogStatus = "";
    private boolean importDialogStatusError;

    private record FunctionEditFrame(WGraph parentGraph, FunctionCardNode openedHost) {}

    private final ArrayDeque<FunctionEditFrame> functionEditStack = new ArrayDeque<>();

    /** Graph snapshots for undo/redo (node positions, wiring, node settings). */
    private static final int MAX_UNDO = 80;
    private final ArrayDeque<CompoundTag> undoStack = new ArrayDeque<>();
    private final ArrayDeque<CompoundTag> redoStack = new ArrayDeque<>();
    private boolean historySuspended = false;
    
    // Viewport panning and zoom
    private double panX = 0;
    private double panY = 0;
    private boolean cameraFocusActive = false;
    private double cameraFocusStartPanX = 0;
    private double cameraFocusStartPanY = 0;
    private double cameraFocusTargetPanX = 0;
    private double cameraFocusTargetPanY = 0;
    private double cameraFocusElapsedSec = 0;
    private double cameraFocusDurationSec = 0.35;
    private boolean isPanning = false;
    private float zoom = 1.0f;

    // Interaction state
    private WNode selectedNode = null;
    private WNode draggingNode = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private WGraph.WSection draggingSection = null;
    private int sectionDragOffsetX = 0;
    private int sectionDragOffsetY = 0;
    /** Section position when header drag began; member nodes use original graph pos + (new - start). */
    private int sectionDragStartSectionX = 0;
    private int sectionDragStartSectionY = 0;
    /** Last total delta applied to waypoints during this section drag (see {@link #sectionDragPrevTotalDy}). */
    private int sectionDragPrevTotalDx = 0;
    private int sectionDragPrevTotalDy = 0;
    private final List<UUID> sectionDragMemberNodes = new ArrayList<>();
    private final Map<UUID, int[]> sectionDragOriginalNodePos = new HashMap<>();
    /** Nested sections fully inside the dragged band; move with the parent on drag. */
    private final List<WGraph.WSection> sectionDragChildSections = new ArrayList<>();
    private final Map<UUID, int[]> sectionDragOriginalNestedSectionPos = new HashMap<>();

    private enum SectionResizeHandle {
        NONE, E, S, W, SE, SW
    }

    private static final int MIN_SECTION_W = 28;
    private static final int MIN_SECTION_H = 24;
    private WGraph.WSection resizingSection = null;
    private SectionResizeHandle sectionResizeHandle = SectionResizeHandle.NONE;
    private int sectionResizeStartX;
    private int sectionResizeStartY;
    private int sectionResizeStartW;
    private int sectionResizeStartH;
    private int sectionResizeGrabNx;
    private int sectionResizeGrabNy;

    // Connection state
    private WNode linkingNode = null;
    private int linkingPin = -1;
    /** After dropping an output wire on empty space: connect this output to the next menu-spawned node's first input. */
    private WNode pendingWireFromNode = null;
    private int pendingWireFromOutputPin = -1;
    /** While the add-node menu is open from a wire drop, freeze the preview end at the drop point. */
    private boolean pendingWireDragFrozen = false;
    private int pendingWireFrozenTx;
    private int pendingWireFrozenTy;
    private int mouseX, mouseY;

    /** Editor spline: hover and drag state (graph space). */
    private enum WireHoverKind {
        NONE,
        WAYPOINT,
        /** Near curve with a valid midpoint for adding a waypoint (ghost shown). */
        INSERT_GHOST,
        /** Near curve (e.g. Alt+delete wire) but not a valid add point. */
        CURVE_ONLY
    }

    private static final int[] WIRE_ARGB_PALETTE = {
        0xAA00FF88, 0xAA6B9CFF, 0xFFFFB84D, 0xFFFF6B9A, 0xAA3DFFDA, 0xFFFF9B6B
    };
    private static final int WIRE_INSERT_GHOST_ARGB = 0xEE66EEFF;
    private WireHoverKind wireHoverKind = WireHoverKind.NONE;
    private int wireHoverConnIdx = -1;
    private int wireHoverWaypointIdx;
    private int wireHoverInsertSeg;
    private int wireHoverInsertGx;
    private int wireHoverInsertGy;
    private int draggingWireConnIdx = -1;
    private int draggingWireWaypointIdx = -1;
    
    // Selection state
    private boolean isSelecting = false;
    private double selStartX, selStartY, selEndX, selEndY;
    private boolean isCreatingSection = false;
    private int sectionCreateStartX, sectionCreateStartY, sectionCreateEndX, sectionCreateEndY;
    private int sectionOrdinalCounter = 1;
    private UUID selectedSectionId = null;
    private UUID renamingSectionId = null;
    private String sectionRenameBuffer = "";
    /** Like {@link dev.devce.websnodelib.api.elements.WTextField}: selection is active when this differs from cursor. */
    private int sectionRenameCursor = 0;
    private int sectionRenameSelectionPos = 0;
    private boolean showSectionsSidebar = false;
    private static final long SECTION_DOUBLE_CLICK_MS = 280;
    /** Compact function library list inside schematic dropdown (similar rhythm to sections sidebar). */
    private static final int FUNCTION_LIB_PANEL_W = 154;
    private static final int FUNCTION_LIB_TITLE_H = 11;
    private static final int FUNCTION_LIB_NAME_ROW_H = 13;
    private static final int FUNCTION_LIB_VISIBLE_ROWS = 5;
    /** Inset vertical scrollbar column width (function list, import flyout, item picker). */
    private static final int SCROLLER_TRACK_W = 9;
    /** Native size of {@link #ICON_SCROLLER_MULTICOLOR} / {@link #ICON_SCROLLER_DISABLED} (see assets). */
    private static final int SCROLLER_TEX_W = 6;
    private static final int SCROLLER_TEX_H = 15;
    /** Footer hint icons (scaled up from {@link #ICON_SIZE} atlas cells). */
    private static final int LIBRARY_HINT_ICON = 20;
    /** Two stacked hint rows + gap (see {@link #drawFunctionLibraryFooterHints}). */
    private static final int LIBRARY_HINT_BLOCK_H = LIBRARY_HINT_ICON * 2 + 8;
    private long lastSectionHeaderClickAtMs = 0;
    private UUID lastSectionHeaderClickId = null;
    private long lastSidebarSectionClickAtMs = 0;
    private UUID lastSidebarSectionClickId = null;

    /** Right-click section → floating RGBA picker (same chrome as Add node). */
    private static final int SECTION_COLOR_PICKER_W = 220;
    private static final int SECTION_COLOR_PICKER_H = 204;
    /** Screen-space anchor (top-left after clamp), like {@link #menuX}/{@link #menuY}. */
    private int sectionColorPickerX;
    private int sectionColorPickerY;
    private UUID sectionColorPickerSectionId = null;
    private int sectionPickR = 0x1F;
    private int sectionPickG = 0x2A;
    private int sectionPickB = 0x40;
    private int sectionPickA = 0x22;
    /** 0–3 = R,G,B,A slider drag; -1 = none. */
    private int sectionPickDragChannel = -1;

    /** {@link dev.devce.websnodelib.api.elements.WItemPickSlot} uses this host to open the picker. */
    private static WNodeScreen activeItemPickHost;

    private boolean itemPickerOpen;
    private String itemPickerQuery = "";
    private int itemPickerScroll;
    private java.util.function.Consumer<ItemStack> itemPickerCallback;
    private static final int ITEM_PICK_PANEL_W = 228;
    private static final int ITEM_PICK_ROW_H = 20;
    private static final int ITEM_PICK_VISIBLE_ROWS = 9;
    private final List<ItemStack> itemPickCandidates = new ArrayList<>();

    // Animation and Effects
    private float screenAnimation = 0.0f;
    private long lastFrameTimeNs = 0;
    /** False after the first {@link #init()} so window resize does not reset undo / replay open animation. */
    private boolean editorFirstInit = true;
    /** Wall-clock scroll for wire pulses; smooth at any tick rate (not tied to {@link WGraph#getSimulationStepCounter()}). */
    private float wirePulseScroll;
    /** Reusable scratch buffers for {@link #drawConnection} to avoid per-frame {@code int[]} allocation per wire. */
    private int[] wireChainXs = new int[4];
    private int[] wireChainYs = new int[4];
    
    /**
     * Particle system for the background.
     */
    private static class NodeParticle {
        double x, y, vx, vy;
        int color;
        int life, maxLife;
    }
    private final java.util.List<NodeParticle> editorParticles = new java.util.ArrayList<>();
    
    /** Add-node menu (right-click / Shift+A): hover flyouts + search. */
    private static final int MENU_MAX_VISIBLE = 22;
    private static final int MENU_GAP = 2;

    /** Bottom-center action dock when one or more nodes are selected. */
    private static final int DOCK_BTN = 32;
    private static final int DOCK_GAP = 8;
    private static final int DOCK_PAD = 10;
    private static final int DOCK_BAR_H = 40;

    private record NodeDockLayout(
            int barX, int barY, int barW, int barH, int btn, int dupX, int delX, int disX, int btnY) {}

    private NodeDockLayout computeNodeDockLayout() {
        int py2 = height - viewInset();
        int barH = DOCK_BAR_H;
        int barY = py2 - barH - 8;
        int btn = DOCK_BTN;
        int gap = DOCK_GAP;
        int pad = DOCK_PAD;
        int barW = pad * 2 + btn * 3 + gap * 2;
        int barX = width / 2 - barW / 2;
        int btnY = barY + (barH - btn) / 2;
        int dupX = barX + pad;
        int delX = dupX + btn + gap;
        int disX = delX + btn + gap;
        return new NodeDockLayout(barX, barY, barW, barH, btn, dupX, delX, disX, btnY);
    }

    private boolean anyNodeSelectedForDock() {
        if (isSearching) {
            return false;
        }
        for (WNode n : graph.getNodes()) {
            if (n.isSelected()) {
                return true;
            }
        }
        return false;
    }

    /** Safe inset for menus (scales down with small windows / high GUI scale). */
    private int menuEdgeMargin() {
        return Math.max(4, Math.min(viewInset(), Math.min(width, height) / 18));
    }

    private int menuEdgeLeft() {
        return menuEdgeMargin();
    }

    private int menuEdgeRight() {
        return width - menuEdgeMargin();
    }

    private int menuEdgeTop() {
        return menuEdgeMargin();
    }

    private int menuEdgeBottom() {
        return height - menuEdgeMargin();
    }

    private int menuRowHeight() {
        return Math.max(9, font.lineHeight + 2);
    }

    /** Two text lines + padding (title + search line). */
    private int menuHeaderHeight() {
        return menuRowHeight() * 2 + 4;
    }

    private int menuMinColWidth() {
        return Math.max(48, width / 12);
    }

    private boolean isSearching = false;
    private String searchQuery = "";
    private int menuX, menuY;
    /** Graph-space anchor where new nodes are placed (keyboard confirm / consistent spawn). */
    private int menuAnchorNx, menuAnchorNy;
    /** Open submenu chain from root (browse mode only); each entry is a category id. */
    private final java.util.List<net.minecraft.resources.ResourceLocation> menuFlyoutPath = new java.util.ArrayList<>();
    /**
     * Locked root category for browse mode: only this tree's flyouts are shown until the pointer leaves all
     * add-node menu chrome or the menu closes.
     */
    private net.minecraft.resources.ResourceLocation stickyBrowseRootId = null;
    /** Flat list when search query non-empty. */
    private final java.util.List<BrowseNodeRow> searchHitRows = new java.util.ArrayList<>();

    private sealed interface BrowseRow permits BrowseCategoryRow, BrowseNodeRow {}

    private record BrowseCategoryRow(net.minecraft.resources.ResourceLocation id, Component label) implements BrowseRow {}

    private record BrowseNodeRow(net.minecraft.resources.ResourceLocation nodeType, Component label) implements BrowseRow {}

    private record MenuRect(int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public WNodeScreen(WGraph graph) {
        this(graph, null, null);
    }

    public WNodeScreen(WGraph graph, FunctionDefinitionStore functionStore) {
        this(graph, functionStore, null);
    }

    public WNodeScreen(
            WGraph graph, FunctionDefinitionStore functionStore, Predicate<ResourceLocation> editorPeripheralLocked) {
        super(Component.literal("Web's Node Editor"));
        this.graph = graph;
        this.functionStore = functionStore;
        this.editorPeripheralLocked = editorPeripheralLocked;
    }

    /** True when the computer editor should show hardware-missing treatment for this node type. */
    protected boolean isEditorPeripheralLocked(ResourceLocation nodeTypeId) {
        return editorPeripheralLocked != null && editorPeripheralLocked.test(nodeTypeId);
    }

    /**
     * When true, the function library row is dimmed and cannot be placed (graph body references a peripheral
     * not installed on the computer).
     */
    protected boolean isFunctionLibraryDefinitionHardwareLocked(FunctionDefinitionStore.Definition def) {
        return false;
    }

    /**
     * Shown above the function library when the physical computer has in-world linked peripherals (Computed only).
     */
    protected List<Component> placedPeripheralHudLines() {
        return List.of();
    }

    private int functionPickerPlacedSectionHeight() {
        List<Component> lines = placedPeripheralHudLines();
        if (lines.isEmpty()) {
            return 0;
        }
        int lh = font != null ? font.lineHeight : 9;
        return FUNCTION_LIB_TITLE_H + lines.size() * lh + 4;
    }

    private boolean innerGraphHasLockedPeripheral(WGraph g) {
        for (WNode n : g.getNodes()) {
            if (isEditorPeripheralLocked(n.getTypeId())) {
                return true;
            }
            if (n instanceof FunctionCardNode fc && innerGraphHasLockedPeripheral(fc.getInnerGraph())) {
                return true;
            }
        }
        return false;
    }

    private void drawEditorPeripheralLockOverlay(GuiGraphics graphics, WNode node) {
        int x = node.getX();
        int y = node.getY();
        int w = node.getWidth();
        int h = node.getHeight();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 5);
        graphics.fill(x, y, x + w, y + h, 0xD0220808);
        graphics.renderOutline(x, y, w, h, 0xFFFF5555);
        graphics.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x88FF3333);
        String msg = Component.translatable("gui.computed.peripheral_not_available").getString();
        int tw = font.width(msg);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - font.lineHeight) / 2;
        graphics.drawString(font, msg, tx + 1, ty + 1, 0xFF000000, false);
        graphics.drawString(font, msg, tx, ty, 0xFFFFE0E0, false);
        graphics.pose().popPose();
    }

    private void enterFunctionGraphEdit(FunctionCardNode host) {
        nestedFunctionTestPlaying = false;
        persistEditorViewport(editorViewportContextKey());
        functionEditStack.push(new FunctionEditFrame(graph, host));
        graph = host.getInnerGraph();
        graph.updateTopology();
        undoStack.clear();
        redoStack.clear();
        selectedNode = null;
        selectedSectionId = null;
        isSearching = false;
        clearStickyBrowseRoot();
        clearPendingWireSpawn();
        if (!loadEditorViewport(editorViewportContextKey())) {
            applyDefaultViewportForContext(editorViewportContextKey());
        }
        playUiClick(1.06f);
    }

    /** Centers the viewport on Start + End nodes inside the current (inner) graph. */
    private void focusPanOnFunctionBoundaryNodes() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (WNode n : graph.getNodes()) {
            if (n instanceof FunctionStartNode || n instanceof FunctionEndNode) {
                any = true;
                minX = Math.min(minX, n.getX());
                minY = Math.min(minY, n.getY());
                maxX = Math.max(maxX, n.getX() + n.getWidth());
                maxY = Math.max(maxY, n.getY() + n.getHeight());
            }
        }
        if (!any) {
            return;
        }
        int cx = (minX + maxX) / 2;
        int cy = (minY + maxY) / 2;
        panX = width / 2.0 - cx;
        panY = height / 2.0 - cy;
    }

    private void confirmNewFunctionAfterNaming() {
        if (functionStore == null) {
            return;
        }
        String name = newFunctionNameBuffer.trim();
        if (name.isEmpty()) {
            name = "Function " + (functionStore.size() + 1);
        }
        UUID id = functionStore.addNew(name, FunctionCardNode.newInnerTemplateTag());
        newFunctionNamingOpen = false;
        newFunctionNameBuffer = "";
        int gx = screenToGraphX(width / 2.0);
        int gy = screenToGraphY(height / 2.0);
        recordCheckpointBeforeEdit();
        FunctionCardNode card = FunctionCardNode.createPlaced(gx, gy, id, functionStore);
        graph.addNode(card);
        enterFunctionGraphEdit(card);
    }

    private void cancelNewFunctionNaming() {
        newFunctionNamingOpen = false;
        newFunctionNameBuffer = "";
    }

    private void exitFunctionGraphEdit() {
        if (functionEditStack.isEmpty()) {
            return;
        }
        nestedFunctionTestPlaying = false;
        persistEditorViewport(editorViewportContextKey());
        if (functionStore != null) {
            syncCurrentNestedFunctionToStore();
        }
        FunctionEditFrame f = functionEditStack.pop();
        graph = f.parentGraph();
        if (functionStore != null) {
            f.openedHost().syncPinsFromInner(functionStore);
        } else {
            f.openedHost().syncPinsFromInner();
        }
        graph.updateTopology();
        undoStack.clear();
        redoStack.clear();
        selectedNode = null;
        if (!loadEditorViewport(editorViewportContextKey())) {
            applyDefaultViewportForContext(editorViewportContextKey());
        }
        playUiClick(1.02f);
    }

    private boolean isEditingNestedFunction() {
        return !functionEditStack.isEmpty();
    }

    /**
     * Identifies which pan/zoom snapshot applies to the graph currently being edited (root vs a specific
     * function body).
     */
    protected String editorViewportContextKey() {
        if (functionEditStack.isEmpty()) {
            return EDITOR_VIEWPORT_ROOT;
        }
        return functionEditStack.peek().openedHost().getFunctionId().toString();
    }

    /** Optional hook for client-side viewport persistence (computer editor: disk; default: no-op). */
    protected void persistEditorViewport(String contextKey) {}

    /**
     * Optional hook to restore pan/zoom for {@code contextKey}.
     *
     * @return true if viewport was applied (skips default framing)
     */
    protected boolean loadEditorViewport(String contextKey) {
        return false;
    }

    /**
     * Client folder for {@code .nbt} function exports (e.g. {@code config/computed/functions}). When {@code
     * null}, save / folder / import controls stay disabled.
     */
    protected Path clientNestedFunctionsDirectory() {
        return null;
    }

    /** Open an exported-functions folder in the OS file manager (optional override). */
    protected void clientRevealNestedFunctionsFolder(Path directory) {}

    private void applyDefaultViewportForContext(String contextKey) {
        if (EDITOR_VIEWPORT_ROOT.equals(contextKey)) {
            restoreEditorViewport(0, 0, 1);
        } else {
            focusPanOnFunctionBoundaryNodes();
        }
    }

    /** Restore pan/zoom from client persistence (e.g. per-computer {@link dev.propulsionteam.computed.client.ComputerEditorScreen}). */
    protected final void restoreEditorViewport(double panX, double panY, float zoom) {
        this.panX = panX;
        this.panY = panY;
        this.zoom = Mth.clamp(zoom, 0.1f, 3.0f);
    }

    protected final double editorPanX() {
        return panX;
    }

    protected final double editorPanY() {
        return panY;
    }

    protected final float editorZoom() {
        return zoom;
    }

    @Override
    public void tick() {
        double editorStep = 1.0 / (double) WGraph.MAX_TICK_RATE;
        if (nestedFunctionTestPlaying && functionStore != null && isEditingNestedFunction()) {
            graph.advanceSimulationInWorld(editorStep);
        } else {
            graph.advanceSimulation(editorStep);
        }
        super.tick();
    }

    /**
     * Effective scale for graph content (matches pose stack: open animation eases from 90% to 100% of {@link #zoom}).
     * Screen ↔ graph conversions must use this, not raw {@code zoom}, or picking drifts from drawing.
     */
    private float editorContentScale() {
        float ease = easeOutCubic(Math.min(1.0f, screenAnimation));
        return (0.90f + 0.10f * ease) * zoom;
    }

    private int screenToGraphX(double screenX) {
        float s = editorContentScale();
        return (int) ((screenX - width / 2.0) / s + width / 2.0 - panX);
    }

    private int screenToGraphY(double screenY) {
        float s = editorContentScale();
        return (int) ((screenY - height / 2.0) / s + height / 2.0 - panY);
    }

    private int viewInset() {
        return editorFullscreen ? 0 : VIEW_INSET_NORMAL;
    }

    private int fullscreenBtnX() {
        return width - viewInset() - FULLSCREEN_BTN - FULLSCREEN_BTN_PAD;
    }

    private int fullscreenBtnY() {
        return viewInset() + FULLSCREEN_BTN_PAD;
    }

    private boolean fullscreenBtnContains(double mx, double my) {
        int x = fullscreenBtnX();
        int y = fullscreenBtnY();
        return mx >= x && mx < x + FULLSCREEN_BTN && my >= y && my < y + FULLSCREEN_BTN;
    }

    private int centerViewBtnX() {
        return width / 2 - CENTER_BTN_W / 2;
    }

    private int centerViewBtnY() {
        return viewInset() + FULLSCREEN_BTN_PAD;
    }

    private boolean centerViewBtnContains(double mx, double my) {
        int x = centerViewBtnX();
        int y = centerViewBtnY();
        return mx >= x && mx < x + CENTER_BTN_W && my >= y && my < y + CENTER_BTN_H;
    }

    private void requestCameraCenterOnNodes() {
        if (graph.getNodes().isEmpty()) {
            playUiClick(0.82f);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (WNode n : graph.getNodes()) {
            // In nested function graphs, boundary helper nodes are often not useful anchors.
            if (n instanceof FunctionStartNode || n instanceof FunctionEndNode) {
                continue;
            }
            any = true;
            minX = Math.min(minX, n.getX());
            minY = Math.min(minY, n.getY());
            maxX = Math.max(maxX, n.getX() + n.getWidth());
            maxY = Math.max(maxY, n.getY() + n.getHeight());
        }
        if (!any) {
            for (WNode n : graph.getNodes()) {
                minX = Math.min(minX, n.getX());
                minY = Math.min(minY, n.getY());
                maxX = Math.max(maxX, n.getX() + n.getWidth());
                maxY = Math.max(maxY, n.getY() + n.getHeight());
            }
        }
        int cx = (minX + maxX) / 2;
        int cy = (minY + maxY) / 2;
        cameraFocusStartPanX = panX;
        cameraFocusStartPanY = panY;
        cameraFocusTargetPanX = width / 2.0 - cx;
        cameraFocusTargetPanY = height / 2.0 - cy;
        double dist = Math.hypot(cameraFocusTargetPanX - cameraFocusStartPanX, cameraFocusTargetPanY - cameraFocusStartPanY);
        cameraFocusDurationSec = Mth.clamp(dist / 1200.0, 0.22, 0.60);
        cameraFocusElapsedSec = 0.0;
        cameraFocusActive = true;
        playUiClick(1.02f);
    }

    private int sectionsSidebarW() {
        return 180;
    }

    private int sectionsSidebarX() {
        return width - viewInset() - sectionsSidebarW() - 8;
    }

    private int sectionsSidebarY() {
        return viewInset() + FULLSCREEN_BTN + FULLSCREEN_BTN_PAD * 2;
    }

    private int sectionsSidebarH() {
        return Math.max(80, height - viewInset() * 2 - FULLSCREEN_BTN - FULLSCREEN_BTN_PAD * 3);
    }

    private boolean sectionsSidebarContains(double mx, double my) {
        if (!showSectionsSidebar) {
            return false;
        }
        int x = sectionsSidebarX();
        int y = sectionsSidebarY();
        return mx >= x && mx < x + sectionsSidebarW() && my >= y && my < y + sectionsSidebarH();
    }

    /** Start/End boundaries draw beneath overlapping logic nodes; selection draws last (on top). */
    private static int nodeDrawLayer(WNode n) {
        if (n.isSelected()) {
            return 2;
        }
        if (n instanceof FunctionStartNode || n instanceof FunctionEndNode) {
            return 0;
        }
        return 1;
    }

    private static void sortNodesForDrawOrder(List<WNode> nodes) {
        nodes.sort(
                Comparator.comparingInt(WNodeScreen::nodeDrawLayer)
                        .thenComparingInt(WNode::getY)
                        .thenComparingInt(WNode::getX)
                        .thenComparing(WNode::getId));
    }

    private int sectionsToggleX() {
        return width - viewInset() - FULLSCREEN_BTN_PAD - FULLSCREEN_BTN;
    }

    private int sectionsToggleY() {
        return fullscreenBtnY();
    }

    private boolean sectionsToggleContains(double mx, double my) {
        int x = sectionsToggleX();
        int y = sectionsToggleY();
        return mx >= x && mx < x + FULLSCREEN_BTN && my >= y && my < y + FULLSCREEN_BTN;
    }

    private void beginSectionCreate(int nx, int ny) {
        isCreatingSection = true;
        sectionCreateStartX = nx;
        sectionCreateStartY = ny;
        sectionCreateEndX = nx;
        sectionCreateEndY = ny;
        selectedSectionId = null;
    }

    /** Section title bar only (same 16px band as the painted header); not the body below. */
    private WGraph.WSection findSectionAt(int nx, int ny) {
        WGraph.WSection best = null;
        int bestLayer = Integer.MIN_VALUE;
        int bestArea = Integer.MAX_VALUE;
        for (WGraph.WSection s : graph.getSections()) {
            if (nx < s.getX() || nx > s.getX() + s.getWidth() || ny < s.getY() || ny > s.getY() + 16) {
                continue;
            }
            int layer = s.getLayer();
            int area = s.getWidth() * s.getHeight();
            if (layer > bestLayer || (layer == bestLayer && area < bestArea)) {
                best = s;
                bestLayer = layer;
                bestArea = area;
            }
        }
        return best;
    }

    private static List<WGraph.WSection> sectionsSortedByLayer(List<WGraph.WSection> src) {
        List<WGraph.WSection> list = new ArrayList<>(src);
        list.sort(
                Comparator.comparingInt(WGraph.WSection::getLayer)
                        .thenComparing(s -> s.getName(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /**
     * {@code inner}'s rectangle is fully inside {@code outer}'s (different ids). Used for nested “layer”
     * sections: copy, drag, and delete move the subtree together.
     */
    private static boolean sectionFullyContainedIn(WGraph.WSection inner, WGraph.WSection outer) {
        if (inner.getId().equals(outer.getId())) {
            return false;
        }
        return inner.getX() >= outer.getX()
                && inner.getY() >= outer.getY()
                && inner.getX() + inner.getWidth() <= outer.getX() + outer.getWidth()
                && inner.getY() + inner.getHeight() <= outer.getY() + outer.getHeight();
    }

    private int sectionHeaderArgb(WGraph.WSection s, boolean renaming, boolean selected) {
        if (renaming) {
            return 0xEE102018;
        }
        int b = s.getBodyColorArgb();
        int r = (b >> 16) & 0xFF;
        int g = (b >> 8) & 0xFF;
        int bl = b & 0xFF;
        int boost = selected ? 35 : 18;
        r = Mth.clamp(r + boost, 0, 255);
        g = Mth.clamp(g + boost, 0, 255);
        bl = Mth.clamp(bl + boost, 0, 255);
        int alpha = selected ? 0xAA : 0x88;
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    private void clampSectionColorPickerOnScreen() {
        int el = menuEdgeLeft();
        int et = menuEdgeTop();
        int maxX = menuEdgeRight() - SECTION_COLOR_PICKER_W;
        int maxY = menuEdgeBottom() - SECTION_COLOR_PICKER_H;
        if (maxX < el) {
            maxX = el;
        }
        if (maxY < et) {
            maxY = et;
        }
        sectionColorPickerX = Mth.clamp(sectionColorPickerX, el, maxX);
        sectionColorPickerY = Mth.clamp(sectionColorPickerY, et, maxY);
    }

    private void sectionColorPickerPanelOrigin(int[] outXY) {
        clampSectionColorPickerOnScreen();
        outXY[0] = sectionColorPickerX;
        outXY[1] = sectionColorPickerY;
    }

    /** Content area below the green title bar (preview + sliders). */
    private int sectionPickerBodyTop(int py) {
        return py + 4 + menuRowHeight() + 6;
    }

    private int sectionColorPickerPackArgb() {
        return (sectionPickA << 24) | (sectionPickR << 16) | (sectionPickG << 8) | sectionPickB;
    }

    private void openSectionColorPicker(WGraph.WSection s, int anchorScreenX, int anchorScreenY) {
        sectionColorPickerSectionId = s.getId();
        selectedSectionId = s.getId();
        sectionColorPickerX = anchorScreenX;
        sectionColorPickerY = anchorScreenY;
        int col = s.getBodyColorArgb();
        sectionPickA = (col >>> 24) & 0xFF;
        sectionPickR = (col >> 16) & 0xFF;
        sectionPickG = (col >> 8) & 0xFF;
        sectionPickB = col & 0xFF;
        sectionPickDragChannel = -1;
        clampSectionColorPickerOnScreen();
    }

    private void closeSectionColorPicker() {
        sectionColorPickerSectionId = null;
        sectionPickDragChannel = -1;
    }

    private void applySectionColorPicker() {
        if (sectionColorPickerSectionId == null) {
            return;
        }
        recordCheckpointBeforeEdit();
        int argb = sectionColorPickerPackArgb();
        for (WGraph.WSection s : graph.getSections()) {
            if (s.getId().equals(sectionColorPickerSectionId)) {
                s.setBodyColorArgb(argb);
                break;
            }
        }
        closeSectionColorPicker();
        playUiClick(1.03f);
    }

    private boolean sectionColorPickerPanelContains(double mx, double my) {
        if (sectionColorPickerSectionId == null) {
            return false;
        }
        int[] o = new int[2];
        sectionColorPickerPanelOrigin(o);
        return mx >= o[0] && mx < o[0] + SECTION_COLOR_PICKER_W && my >= o[1] && my < o[1] + SECTION_COLOR_PICKER_H;
    }

    private void sectionPickerSetChannelFromMouseX(int channel, double mouseX) {
        int[] o = new int[2];
        sectionColorPickerPanelOrigin(o);
        int slx = o[0] + 12;
        int slw = SECTION_COLOR_PICKER_W - 24;
        int v = (int) Mth.clamp((mouseX - slx) / slw * 255.0, 0, 255);
        switch (channel) {
            case 0 -> sectionPickR = v;
            case 1 -> sectionPickG = v;
            case 2 -> sectionPickB = v;
            case 3 -> sectionPickA = v;
            default -> {
            }
        }
    }

    /** @return true if event consumed. */
    private boolean handleSectionColorPickerMouseClick(double mouseX, double mouseY, int button) {
        if (sectionColorPickerSectionId == null) {
            return false;
        }
        clampSectionColorPickerOnScreen();
        int px = sectionColorPickerX;
        int py = sectionColorPickerY;
        if (button != 0) {
            if (!sectionColorPickerPanelContains(mouseX, mouseY)) {
                closeSectionColorPicker();
            }
            return true;
        }
        if (!sectionColorPickerPanelContains(mouseX, mouseY)) {
            closeSectionColorPicker();
            return true;
        }
        int bodyTop = sectionPickerBodyTop(py);
        int slx = px + 12;
        int slw = SECTION_COLOR_PICKER_W - 24;
        for (int c = 0; c < 4; c++) {
            int sy = bodyTop + 44 + c * 22;
            int barTop = sy + 10;
            if (mouseX >= slx && mouseX < slx + slw && mouseY >= barTop && mouseY < barTop + 8) {
                sectionPickDragChannel = c;
                sectionPickerSetChannelFromMouseX(c, mouseX);
                playUiClick(0.98f);
                return true;
            }
        }
        int by = py + SECTION_COLOR_PICKER_H - 30;
        if (mouseX >= px + 10 && mouseX < px + 102 && mouseY >= by && mouseY < by + 14) {
            applySectionColorPicker();
            return true;
        }
        if (mouseX >= px + 112 && mouseX < px + 208 && mouseY >= by && mouseY < by + 14) {
            closeSectionColorPicker();
            playUiClick(0.92f);
            return true;
        }
        int ry = py + SECTION_COLOR_PICKER_H - 12;
        if (mouseX >= px + 12 && mouseX < px + 120 && mouseY >= ry && mouseY < ry + 10) {
            sectionPickR = (WGraph.WSection.DEFAULT_BODY_COLOR_ARGB >> 16) & 0xFF;
            sectionPickG = (WGraph.WSection.DEFAULT_BODY_COLOR_ARGB >> 8) & 0xFF;
            sectionPickB = WGraph.WSection.DEFAULT_BODY_COLOR_ARGB & 0xFF;
            sectionPickA = (WGraph.WSection.DEFAULT_BODY_COLOR_ARGB >>> 24) & 0xFF;
            playUiClick(1.0f);
            return true;
        }
        return true;
    }

    private void renderSectionColorPickerOverlay(GuiGraphics graphics) {
        if (sectionColorPickerSectionId == null) {
            return;
        }
        clampSectionColorPickerOnScreen();
        int px = sectionColorPickerX;
        int py = sectionColorPickerY;
        float zAboveGraph =
                4000f + Math.min(12000f, (float) graph.getNodes().size() * 12f);
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, zAboveGraph);
        drawMenuPanel(graphics, px, py, SECTION_COLOR_PICKER_W, SECTION_COLOR_PICKER_H);
        graphics.drawString(font, "Section color", px + 4, py + 4, 0xFF669966, false);
        int bodyTop = sectionPickerBodyTop(py);
        int preview = sectionColorPickerPackArgb();
        graphics.fill(px + 12, bodyTop, px + 50, bodyTop + 38, preview);
        graphics.renderOutline(px + 12, bodyTop, 38, 38, 0xFFAAAAAA);
        String hex = String.format("#%02X%02X%02X  A %02X", sectionPickR, sectionPickG, sectionPickB, sectionPickA);
        graphics.drawString(font, hex, px + 56, bodyTop + 12, 0xFFB0B8D0, false);

        int slx = px + 12;
        int slw = SECTION_COLOR_PICKER_W - 24;
        String[] labs = {"Red", "Green", "Blue", "Alpha"};
        int[] vals = {sectionPickR, sectionPickG, sectionPickB, sectionPickA};
        for (int c = 0; c < 4; c++) {
            int sy = bodyTop + 44 + c * 22;
            graphics.drawString(font, labs[c], px + 12, sy, 0xFFCCD4EE, false);
            graphics.fill(slx, sy + 10, slx + slw, sy + 18, 0xFF1A2030);
            int fw = (int) (slw * (vals[c] / 255.0));
            int fillCol = switch (c) {
                case 0 -> 0xFFFF5555;
                case 1 -> 0xFF55FF55;
                case 2 -> 0xFF5555FF;
                default -> (sectionPickA << 24) | (sectionPickR << 16) | (sectionPickG << 8) | sectionPickB;
            };
            if (fw > 0) {
                graphics.fill(slx, sy + 10, slx + fw, sy + 18, fillCol);
            }
            graphics.renderOutline(slx, sy + 10, slw, 8, 0xFF5A5A5A);
        }

        int by = py + SECTION_COLOR_PICKER_H - 30;
        graphics.fill(px + 10, by, px + 102, by + 14, 0xFF2A4060);
        graphics.drawString(font, "OK", px + 44, by + 3, 0xFFE0E8FF, false);
        graphics.fill(px + 112, by, px + 208, by + 14, 0xFF402830);
        graphics.drawString(font, "Cancel", px + 138, by + 3, 0xFFE0E0E8, false);
        graphics.drawString(font, "Reset theme default", px + 12, py + SECTION_COLOR_PICKER_H - 11, 0xFF8A9AB8, false);
        graphics.pose().popPose();
    }

    /** Called from {@link dev.devce.websnodelib.api.elements.WItemPickSlot} when a node editor is open. */
    public static void requestItemPick(Consumer<ItemStack> onChosen) {
        if (activeItemPickHost == null || onChosen == null) {
            return;
        }
        activeItemPickHost.openItemPicker(onChosen);
    }

    private void openItemPicker(Consumer<ItemStack> onChosen) {
        itemPickerOpen = true;
        itemPickerQuery = "";
        itemPickerScroll = 0;
        itemPickerCallback = onChosen;
        rebuildItemPickCandidates();
        playUiClick(1.01f);
    }

    private void closeItemPicker() {
        itemPickerOpen = false;
        itemPickerCallback = null;
        itemPickCandidates.clear();
    }

    private void rebuildItemPickCandidates() {
        itemPickCandidates.clear();
        String q = itemPickerQuery.trim().toLowerCase();
        for (Item it : BuiltInRegistries.ITEM) {
            ItemStack st = it.getDefaultInstance();
            if (st.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(it);
            String ids = id.toString().toLowerCase();
            if (!q.isEmpty() && !ids.contains(q)) {
                continue;
            }
            itemPickCandidates.add(st);
            if (itemPickCandidates.size() >= 400) {
                break;
            }
        }
    }

    private int itemPickerPanelH() {
        return menuHeaderHeight() + 16 + ITEM_PICK_VISIBLE_ROWS * ITEM_PICK_ROW_H + menuRowHeight() + 6;
    }

    private int itemPickerPanelX() {
        return Mth.clamp(width / 2 - ITEM_PICK_PANEL_W / 2, menuEdgeLeft(), menuEdgeRight() - ITEM_PICK_PANEL_W);
    }

    private int itemPickerPanelY() {
        return Mth.clamp(height / 5, menuEdgeTop(), menuEdgeBottom() - itemPickerPanelH());
    }

    private boolean itemPickerContains(double mx, double my) {
        if (!itemPickerOpen) {
            return false;
        }
        int px = itemPickerPanelX();
        int py = itemPickerPanelY();
        return mx >= px && mx < px + ITEM_PICK_PANEL_W && my >= py && my < py + itemPickerPanelH();
    }

    private void renderItemPickerOverlay(GuiGraphics graphics) {
        if (!itemPickerOpen) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 5200f);
        graphics.fill(0, 0, width, height, 0x88000000);
        int px = itemPickerPanelX();
        int py = itemPickerPanelY();
        int ph = itemPickerPanelH();
        drawMenuPanel(graphics, px, py, ITEM_PICK_PANEL_W, ph);
        graphics.drawString(font, "Pick item (frequency)", px + 6, py + 5, 0xFF669966, false);
        int lineY = py + menuHeaderHeight();
        graphics.drawString(font, "> " + itemPickerQuery + "_", px + 6, lineY, 0xFF00FF88, false);
        int listTop = lineY + 14;
        int listH = ITEM_PICK_VISIBLE_ROWS * ITEM_PICK_ROW_H;
        int itemListRight = px + ITEM_PICK_PANEL_W - 2 - SCROLLER_TRACK_W;
        int vis = Math.min(ITEM_PICK_VISIBLE_ROWS, Math.max(0, itemPickCandidates.size() - itemPickerScroll));
        graphics.enableScissor(px + 2, listTop, itemListRight, listTop + listH);
        for (int row = 0; row < vis; row++) {
            int idx = itemPickerScroll + row;
            if (idx >= itemPickCandidates.size()) {
                break;
            }
            ItemStack st = itemPickCandidates.get(idx);
            int ry = listTop + row * ITEM_PICK_ROW_H;
            boolean hr =
                    mouseX >= px
                            && mouseX < itemListRight
                            && mouseY >= ry
                            && mouseY < ry + ITEM_PICK_ROW_H;
            if (hr) {
                graphics.fill(px + 2, ry, itemListRight, ry + ITEM_PICK_ROW_H - 1, 0x4400FF88);
            }
            graphics.renderItem(st, px + 6, ry + 2);
            String nm = st.getHoverName().getString();
            int maxNmPx = Math.max(font.width("…"), itemListRight - (px + 28) - 4);
            if (font.width(nm) > maxNmPx) {
                String ell = "…";
                while (nm.length() > 1 && font.width(nm.substring(0, nm.length() - 1) + ell) > maxNmPx) {
                    nm = nm.substring(0, nm.length() - 1);
                }
                nm = nm + ell;
            }
            graphics.drawString(font, nm, px + 28, ry + 6, 0xFFD0D8EE, false);
        }
        graphics.disableScissor();
        drawInsetVerticalScroller(
                graphics,
                px + ITEM_PICK_PANEL_W - 2 - SCROLLER_TRACK_W,
                listTop,
                listH,
                itemPickerScroll,
                itemPickCandidates.size(),
                ITEM_PICK_VISIBLE_ROWS);
        int footY = listTop + ITEM_PICK_VISIBLE_ROWS * ITEM_PICK_ROW_H + 2;
        graphics.drawString(font, "Esc: cancel   Enter: top match", px + 6, footY, 0xFF888888, false);
        graphics.pose().popPose();
    }

    private boolean handleItemPickerClick(double mouseX, double mouseY, int button) {
        if (!itemPickerOpen) {
            return false;
        }
        if (itemPickerContains(mouseX, mouseY)) {
            if (button == 0) {
                int listTop = itemPickerPanelY() + menuHeaderHeight() + 14;
                int row = (int) ((mouseY - listTop) / ITEM_PICK_ROW_H);
                if (row >= 0 && row < ITEM_PICK_VISIBLE_ROWS) {
                    int idx = itemPickerScroll + row;
                    if (idx >= 0 && idx < itemPickCandidates.size()) {
                        ItemStack picked = itemPickCandidates.get(idx).copyWithCount(1);
                        if (itemPickerCallback != null) {
                            itemPickerCallback.accept(picked);
                        }
                        closeItemPicker();
                        playUiClick(1.04f);
                        return true;
                    }
                }
            }
            return true;
        }
        closeItemPicker();
        playUiClick(0.92f);
        return true;
    }

    private int sectionResizeHitSlop() {
        return Math.max(5, Mth.ceil(8.0f / editorContentScale()));
    }

    /** Hit-test resize handles for the selected section (graph coordinates). */
    private SectionResizeHandle hitSectionResizeHandle(WGraph.WSection s, int nx, int ny) {
        if (selectedSectionId == null || !s.getId().equals(selectedSectionId)) {
            return SectionResizeHandle.NONE;
        }
        int d = sectionResizeHitSlop();
        int x = s.getX();
        int y = s.getY();
        int w = s.getWidth();
        int h = s.getHeight();
        int right = x + w;
        int bottom = y + h;
        boolean onRight = nx >= right - d && nx <= right + d;
        boolean onLeft = nx >= x - d && nx <= x + d;
        boolean onBottom = ny >= bottom - d && ny <= bottom + d;
        if (onBottom && onRight) {
            return SectionResizeHandle.SE;
        }
        if (onBottom && onLeft) {
            return SectionResizeHandle.SW;
        }
        if (onBottom && nx > x + d && nx < right - d) {
            return SectionResizeHandle.S;
        }
        if (onRight && ny > y + d && ny < bottom - d) {
            return SectionResizeHandle.E;
        }
        if (onLeft && ny > y + d && ny < bottom - d) {
            return SectionResizeHandle.W;
        }
        return SectionResizeHandle.NONE;
    }

    private void drawSectionResizeHandles(GuiGraphics graphics, WGraph.WSection s) {
        int x = s.getX();
        int y = s.getY();
        int w = s.getWidth();
        int h = s.getHeight();
        int midY = y + h / 2;
        int midX = x + w / 2;
        int bot = y + h;
        int right = x + w;
        int half = Math.max(2, Mth.floor(3.5f * editorContentScale()));
        drawResizeHandleSquare(graphics, right, bot, half);
        drawResizeHandleSquare(graphics, x, bot, half);
        drawResizeHandleSquare(graphics, midX, bot, half);
        drawResizeHandleSquare(graphics, right, midY, half);
        drawResizeHandleSquare(graphics, x, midY, half);
    }

    private void drawResizeHandleSquare(GuiGraphics graphics, int cx, int cy, int half) {
        graphics.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, 0xFFE8ECFF);
        graphics.renderOutline(cx - half, cy - half, half * 2 + 1, half * 2 + 1, 0xFF6C8DFF);
    }

    private static final long SECTION_RENAME_CARET_BLINK_MS = 520L;

    private boolean sectionRenameCaretLit() {
        return (net.minecraft.Util.getMillis() / SECTION_RENAME_CARET_BLINK_MS) % 2L == 0L;
    }

    /**
     * Blinking insertion caret after inline rename text. {@code textY} must match the y passed to
     * {@link GuiGraphics#drawString(net.minecraft.client.gui.Font, String, int, int, int, boolean)} for that label.
     */
    private void drawSectionRenameCaret(
            GuiGraphics graphics, int textLeftX, int textY, int textMaxRightX, String textBeforeCaret) {
        if (!sectionRenameCaretLit()) {
            return;
        }
        int cx = textLeftX + font.width(textBeforeCaret);
        if (cx > textMaxRightX - 1) {
            return;
        }
        int h = Math.max(8, font.lineHeight);
        graphics.fill(cx, textY, cx + 1, textY + h, 0xFFFFFFFF);
    }

    private void startSectionRename(UUID sectionId, String name) {
        endLibraryFunctionRenameEditing();
        renamingSectionId = sectionId;
        sectionRenameBuffer = name == null ? "" : name;
        sectionRenameCursor = sectionRenameBuffer.length();
        sectionRenameSelectionPos = sectionRenameCursor;
    }

    private void startLibraryFunctionRename(UUID functionId, String name) {
        endSectionRenameEditing();
        renamingLibraryFunctionId = functionId;
        libraryFnRenameBuffer = name == null ? "" : name;
        libraryFnRenameCursor = libraryFnRenameBuffer.length();
        libraryFnRenameSelectionPos = libraryFnRenameCursor;
    }

    private void endLibraryFunctionRenameEditing() {
        renamingLibraryFunctionId = null;
        libraryFnRenameBuffer = "";
        libraryFnRenameCursor = 0;
        libraryFnRenameSelectionPos = 0;
    }

    private void commitLibraryFunctionRename() {
        if (renamingLibraryFunctionId == null || functionStore == null) {
            endLibraryFunctionRenameEditing();
            return;
        }
        FunctionDefinitionStore.Definition def = functionStore.get(renamingLibraryFunctionId);
        if (def == null) {
            endLibraryFunctionRenameEditing();
            return;
        }
        String nm = libraryFnRenameBuffer.trim();
        if (nm.isEmpty()) {
            nm = def.name();
        }
        recordCheckpointBeforeEdit();
        functionStore.put(renamingLibraryFunctionId, nm, def.body());
        refreshFunctionCardTitlesFromLibrary();
        endLibraryFunctionRenameEditing();
    }

    /** Updates {@link FunctionCardNode} titles on the root graph after a library rename. */
    private void refreshFunctionCardTitlesFromLibrary() {
        if (functionStore == null) {
            return;
        }
        WGraph root = rootGraphForLibraryCards();
        for (WNode n : root.getNodes()) {
            if (n instanceof FunctionCardNode c) {
                c.syncPinsFromInner(functionStore);
            }
        }
    }

    private WGraph rootGraphForLibraryCards() {
        if (functionEditStack.isEmpty()) {
            return graph;
        }
        return functionEditStack.peekLast().parentGraph();
    }

    private boolean libraryFnRenameHasSelection() {
        return libraryFnRenameCursor != libraryFnRenameSelectionPos;
    }

    private void libraryFnRenameDeleteSelection() {
        int start = Math.min(libraryFnRenameCursor, libraryFnRenameSelectionPos);
        int end = Math.max(libraryFnRenameCursor, libraryFnRenameSelectionPos);
        libraryFnRenameBuffer = libraryFnRenameBuffer.substring(0, start) + libraryFnRenameBuffer.substring(end);
        libraryFnRenameCursor = start;
        libraryFnRenameSelectionPos = start;
    }

    private void libraryFnRenameReplaceSelection(String text) {
        if (text == null) {
            text = "";
        }
        libraryFnRenameDeleteSelection();
        libraryFnRenameBuffer =
                libraryFnRenameBuffer.substring(0, libraryFnRenameCursor)
                        + text
                        + libraryFnRenameBuffer.substring(libraryFnRenameCursor);
        libraryFnRenameCursor += text.length();
        libraryFnRenameSelectionPos = libraryFnRenameCursor;
    }

    private void libraryFnRenameMoveCursor(int nextPos, boolean keepSelection) {
        libraryFnRenameCursor = Mth.clamp(nextPos, 0, libraryFnRenameBuffer.length());
        if (!keepSelection) {
            libraryFnRenameSelectionPos = libraryFnRenameCursor;
        }
    }

    private int libraryFnRenamePreviousWordBoundary(int from) {
        int i = Mth.clamp(from, 0, libraryFnRenameBuffer.length());
        while (i > 0 && Character.isWhitespace(libraryFnRenameBuffer.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(libraryFnRenameBuffer.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int libraryFnRenameNextWordBoundary(int from) {
        int len = libraryFnRenameBuffer.length();
        int i = Mth.clamp(from, 0, len);
        while (i < len && Character.isWhitespace(libraryFnRenameBuffer.charAt(i))) {
            i++;
        }
        while (i < len && !Character.isWhitespace(libraryFnRenameBuffer.charAt(i))) {
            i++;
        }
        return i;
    }

    private boolean handleLibraryFunctionRenameKey(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = hasControlDown();
        boolean shift = Screen.hasShiftDown();
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
                || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT
                || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER
                || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER) {
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            libraryFnRenameSelectionPos = 0;
            libraryFnRenameCursor = libraryFnRenameBuffer.length();
            playUiClick(0.97f);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (libraryFnRenameHasSelection()) {
                int a = Math.min(libraryFnRenameCursor, libraryFnRenameSelectionPos);
                int b = Math.max(libraryFnRenameCursor, libraryFnRenameSelectionPos);
                minecraft.keyboardHandler.setClipboard(libraryFnRenameBuffer.substring(a, b));
            } else {
                minecraft.keyboardHandler.setClipboard(libraryFnRenameBuffer);
            }
            playUiClick(1.02f);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            if (libraryFnRenameHasSelection()) {
                int a = Math.min(libraryFnRenameCursor, libraryFnRenameSelectionPos);
                int b = Math.max(libraryFnRenameCursor, libraryFnRenameSelectionPos);
                minecraft.keyboardHandler.setClipboard(libraryFnRenameBuffer.substring(a, b));
                libraryFnRenameDeleteSelection();
                playUiClick(0.9f);
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                libraryFnRenameReplaceSelection(sectionRenameSanitizePaste(clip));
                playUiClick(1.04f);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitLibraryFunctionRename();
            playUiClick(1.0f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            endLibraryFunctionRenameEditing();
            playUiClick(0.94f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (libraryFnRenameHasSelection()) {
                libraryFnRenameDeleteSelection();
            } else if (libraryFnRenameCursor > 0) {
                int start = ctrl ? libraryFnRenamePreviousWordBoundary(libraryFnRenameCursor) : libraryFnRenameCursor - 1;
                libraryFnRenameBuffer =
                        libraryFnRenameBuffer.substring(0, start) + libraryFnRenameBuffer.substring(libraryFnRenameCursor);
                libraryFnRenameCursor = start;
                libraryFnRenameSelectionPos = libraryFnRenameCursor;
            }
            playUiClick(0.9f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (libraryFnRenameHasSelection()) {
                libraryFnRenameDeleteSelection();
            } else if (libraryFnRenameCursor < libraryFnRenameBuffer.length()) {
                int end = ctrl ? libraryFnRenameNextWordBoundary(libraryFnRenameCursor) : libraryFnRenameCursor + 1;
                libraryFnRenameBuffer =
                        libraryFnRenameBuffer.substring(0, libraryFnRenameCursor) + libraryFnRenameBuffer.substring(end);
            }
            playUiClick(0.9f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            int next =
                    ctrl ? libraryFnRenamePreviousWordBoundary(libraryFnRenameCursor) : Math.max(0, libraryFnRenameCursor - 1);
            libraryFnRenameMoveCursor(next, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            int next =
                    ctrl
                            ? libraryFnRenameNextWordBoundary(libraryFnRenameCursor)
                            : Math.min(libraryFnRenameBuffer.length(), libraryFnRenameCursor + 1);
            libraryFnRenameMoveCursor(next, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            libraryFnRenameMoveCursor(0, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            libraryFnRenameMoveCursor(libraryFnRenameBuffer.length(), shift);
            return true;
        }
        return true;
    }

    private void endSectionRenameEditing() {
        renamingSectionId = null;
        sectionRenameBuffer = "";
        sectionRenameCursor = 0;
        sectionRenameSelectionPos = 0;
    }

    private boolean sectionRenameHasSelection() {
        return sectionRenameCursor != sectionRenameSelectionPos;
    }

    private void sectionRenameDeleteSelection() {
        int start = Math.min(sectionRenameCursor, sectionRenameSelectionPos);
        int end = Math.max(sectionRenameCursor, sectionRenameSelectionPos);
        sectionRenameBuffer = sectionRenameBuffer.substring(0, start) + sectionRenameBuffer.substring(end);
        sectionRenameCursor = start;
        sectionRenameSelectionPos = start;
    }

    private void sectionRenameReplaceSelection(String text) {
        if (text == null) {
            text = "";
        }
        sectionRenameDeleteSelection();
        sectionRenameBuffer =
                sectionRenameBuffer.substring(0, sectionRenameCursor) + text + sectionRenameBuffer.substring(sectionRenameCursor);
        sectionRenameCursor += text.length();
        sectionRenameSelectionPos = sectionRenameCursor;
    }

    private void sectionRenameMoveCursor(int nextPos, boolean keepSelection) {
        sectionRenameCursor = Mth.clamp(nextPos, 0, sectionRenameBuffer.length());
        if (!keepSelection) {
            sectionRenameSelectionPos = sectionRenameCursor;
        }
    }

    private int sectionRenamePreviousWordBoundary(int from) {
        int i = Mth.clamp(from, 0, sectionRenameBuffer.length());
        while (i > 0 && Character.isWhitespace(sectionRenameBuffer.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(sectionRenameBuffer.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int sectionRenameNextWordBoundary(int from) {
        int len = sectionRenameBuffer.length();
        int i = Mth.clamp(from, 0, len);
        while (i < len && Character.isWhitespace(sectionRenameBuffer.charAt(i))) {
            i++;
        }
        while (i < len && !Character.isWhitespace(sectionRenameBuffer.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String sectionRenameSanitizePaste(String clip) {
        if (clip == null || clip.isEmpty()) {
            return "";
        }
        int n = clip.indexOf('\n');
        int r = clip.indexOf('\r');
        int cut = clip.length();
        if (n >= 0) {
            cut = Math.min(cut, n);
        }
        if (r >= 0) {
            cut = Math.min(cut, r);
        }
        return clip.substring(0, cut);
    }

    private void drawSectionRenameTextWithSelectionAndCaret(GuiGraphics graphics, int lx, int ly, int maxTextRight) {
        int selStart = Math.min(sectionRenameCursor, sectionRenameSelectionPos);
        int selEnd = Math.max(sectionRenameCursor, sectionRenameSelectionPos);
        if (selStart != selEnd) {
            int left = lx + font.width(sectionRenameBuffer.substring(0, selStart));
            int right = lx + font.width(sectionRenameBuffer.substring(0, selEnd));
            left = Math.min(left, maxTextRight);
            right = Math.min(right, maxTextRight);
            if (left < right) {
                graphics.fill(left, ly, right, ly + Math.max(8, font.lineHeight), 0x664A90FF);
            }
        }
        graphics.drawString(font, sectionRenameBuffer, lx, ly, 0xFFCCEEDD, false);
        drawSectionRenameCaret(graphics, lx, ly, maxTextRight, sectionRenameBuffer.substring(0, sectionRenameCursor));
    }

    private void finalizeSectionCreate() {
        int x1 = Math.min(sectionCreateStartX, sectionCreateEndX);
        int y1 = Math.min(sectionCreateStartY, sectionCreateEndY);
        int x2 = Math.max(sectionCreateStartX, sectionCreateEndX);
        int y2 = Math.max(sectionCreateStartY, sectionCreateEndY);
        int w = x2 - x1;
        int h = y2 - y1;
        isCreatingSection = false;
        if (w < MIN_SECTION_W || h < MIN_SECTION_H) {
            return;
        }
        recordCheckpointBeforeEdit();
        WGraph.WSection s = new WGraph.WSection("Section " + sectionOrdinalCounter++, x1, y1, w, h);
        graph.getSections().add(s);
        int parentMaxLayer = -1;
        for (WGraph.WSection p : graph.getSections()) {
            if (p.getId().equals(s.getId())) {
                continue;
            }
            if (sectionFullyContainedIn(s, p)) {
                parentMaxLayer = Math.max(parentMaxLayer, p.getLayer());
            }
        }
        s.setLayer(parentMaxLayer < 0 ? 0 : parentMaxLayer + 1);
        selectedSectionId = s.getId();
        startSectionRename(s.getId(), s.getName());
        showSectionsSidebar = true;
        playUiClick(1.02f);
    }

    private void playUiClick(float pitch) {
        if (minecraft == null) {
            return;
        }
        minecraft.getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
    }

    @Override
    protected void init() {
        super.init();
        activeItemPickHost = this;
        if (editorFirstInit) {
            editorFirstInit = false;
            screenAnimation = 0;
            undoStack.clear();
            redoStack.clear();
        }
        graph.updateTopology();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        int prevW = this.width;
        int prevH = this.height;
        super.resize(minecraft, width, height);
        if (prevW > 0 && prevH > 0) {
            panX += (width - prevW) * 0.5;
            panY += (height - prevH) * 0.5;
        }
    }

    /** Call immediately before a user edit that should be reversible. */
    private void recordCheckpointBeforeEdit() {
        if (historySuspended) {
            return;
        }
        undoStack.addLast(graph.save().copy());
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeFirst();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        historySuspended = true;
        try {
            redoStack.addLast(graph.save().copy());
            graph.load(undoStack.removeLast());
            selectedNode = null;
            draggingNode = null;
            linkingNode = null;
            linkingPin = -1;
            clearPendingWireSpawn();
            isSearching = false;
            searchQuery = "";
            menuFlyoutPath.clear();
            clearStickyBrowseRoot();
            closeItemPicker();
        } finally {
            historySuspended = false;
        }
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        historySuspended = true;
        try {
            undoStack.addLast(graph.save().copy());
            graph.load(redoStack.removeLast());
            selectedNode = null;
            draggingNode = null;
            linkingNode = null;
            linkingPin = -1;
            clearPendingWireSpawn();
            isSearching = false;
            searchQuery = "";
            menuFlyoutPath.clear();
            clearStickyBrowseRoot();
            closeItemPicker();
        } finally {
            historySuspended = false;
        }
    }

    /**
     * Undo/redo using the key's layout label ({@link GLFW#glfwGetKeyName}) so e.g. QWERTZ Ctrl+Z / Ctrl+Y match
     * the printed letters; falls back to US QWERTY key positions if the name is unavailable.
     */
    private boolean tryHandleUndoRedo(int keyCode, int scanCode) {
        if (!hasControlDown()) {
            return false;
        }
        boolean shift = hasShiftDown();
        if (scanCode != 0) {
            String keyName = GLFW.glfwGetKeyName(GLFW.GLFW_KEY_UNKNOWN, scanCode);
            if (keyName != null && !keyName.isEmpty()) {
                int cp = keyName.codePointAt(0);
                if (!Character.isLetter(cp)) {
                    return false;
                }
                int lower = Character.toLowerCase(cp);
                if (lower == 'z') {
                    if (shift) {
                        redo();
                    } else {
                        undo();
                    }
                    return true;
                }
                if (lower == 'y' && !shift) {
                    redo();
                    return true;
                }
                return false;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Y && !shift) {
            redo();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        clearPendingWireSpawn();
        if (activeItemPickHost == this) {
            activeItemPickHost = null;
        }
        closeItemPicker();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isInsideEditorPanel(double mouseX, double mouseY) {
        int inset = viewInset();
        return mouseX >= inset && mouseX < width - inset && mouseY >= inset && mouseY < height - inset;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // drawManaged disables GuiGraphics's per-fill `bufferSource.endBatch()`, so the hundreds
        // of fills/quads/strings emitted below batch into one OpenGL submission per render type
        // instead of one submission per call. This is the dominant FPS win on busy editor screens.
        graphics.drawManaged(() -> renderInner(graphics, mouseX, mouseY, partialTick));
    }

    private void renderInner(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long nowNs = System.nanoTime();
        float deltaTime = (lastFrameTimeNs == 0) ? 0.016f : (float) ((nowNs - lastFrameTimeNs) / 1_000_000_000.0);
        lastFrameTimeNs = nowNs;
        if (cameraFocusActive) {
            cameraFocusElapsedSec += deltaTime;
            double t = cameraFocusDurationSec <= 0.0 ? 1.0 : Mth.clamp(cameraFocusElapsedSec / cameraFocusDurationSec, 0.0, 1.0);
            // Time-parametric easing avoids frame-quantized feel.
            double e = 1.0 - Math.pow(1.0 - t, 3.0);
            panX = Mth.lerp(e, cameraFocusStartPanX, cameraFocusTargetPanX);
            panY = Mth.lerp(e, cameraFocusStartPanY, cameraFocusTargetPanY);
            if (t >= 1.0) {
                cameraFocusActive = false;
            }
        }
        wirePulseScroll += deltaTime * PULSE_SCROLL_SPEED;
        if (wirePulseScroll > 8192f) {
            wirePulseScroll -= 8192f;
        }

        screenAnimation = Math.min(1.0f, screenAnimation + deltaTime / OPEN_DURATION_SEC);
        float ease = easeOutCubic(screenAnimation);
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        int dimAlpha = (int) (160 * ease);
        graphics.fill(0, 0, width, height, (dimAlpha << 24));

        int inset = viewInset();
        int px1 = inset;
        int py1 = inset;
        int px2 = width - inset;
        int py2 = height - inset;
        int panelBg = ((int) (230 * ease) << 24) | 0x121212;
        graphics.fill(px1, py1, px2, py2, panelBg);
        graphics.renderOutline(px1, py1, px2 - px1, py2 - py1, 0xFF555555);
        if (isEditingNestedFunction()) {
            String fnTitle = "Function";
            if (functionStore != null && !functionEditStack.isEmpty()) {
                FunctionCardNode host = functionEditStack.peek().openedHost();
                FunctionDefinitionStore.Definition def = functionStore.get(host.getFunctionId());
                if (def != null && def.name() != null && !def.name().isBlank()) {
                    fnTitle = def.name();
                }
            }
            String lead = fnTitle + " - ";
            String tail = "Go back";
            int keyDraw = 11;
            int gap = 4;
            int rowTop = py1 + 4;
            int textY = rowTop + (keyDraw - font.lineHeight) / 2 + 1;
            int totalW = font.width(lead) + keyDraw + gap + font.width(tail);
            int startX = px1 + (px2 - px1 - totalW) / 2;
            graphics.drawString(font, lead, startX, textY, 0xFF66CCAA, false);
            int ix = startX + font.width(lead);
            blitScaledHintTile(graphics, KEY_CAP_ESC, ix, rowTop, keyDraw);
            graphics.drawString(font, tail, ix + keyDraw + gap, textY, 0xFF66CCAA, false);
        }

        for (int i = py1; i < py2; i += 2) {
            graphics.fill(px1, i, px2, i + 1, 0x0A000000);
        }

        graphics.enableScissor(px1, py1, px2, py2);

        graphics.pose().pushPose();
        float sOut = editorContentScale();
        graphics.pose().translate(width / 2f, height / 2f, 0);
        graphics.pose().scale(sOut, sOut, 1.0f);
        graphics.pose().translate(-width / 2f, -height / 2f, 0);

        drawGrid(graphics);

        graphics.pose().translate(panX, panY, 0);

        for (WGraph.WSection s : sectionsSortedByLayer(graph.getSections())) {
            boolean renaming = s.getId().equals(renamingSectionId);
            boolean secSel = s.getId().equals(selectedSectionId);
            int bg = s.getBodyColorArgb();
            graphics.fill(s.getX(), s.getY(), s.getX() + s.getWidth(), s.getY() + s.getHeight(), bg);
            int head = sectionHeaderArgb(s, renaming, secSel);
            graphics.fill(s.getX(), s.getY(), s.getX() + s.getWidth(), s.getY() + 16, head);
            int outline = secSel ? 0xFF66CCAA : 0xFF448866;
            graphics.renderOutline(s.getX(), s.getY(), s.getWidth(), s.getHeight(), outline);
            int lx = s.getX() + 4;
            int ly = s.getY() + 4;
            if (renaming) {
                drawSectionRenameTextWithSelectionAndCaret(graphics, lx, ly, s.getX() + s.getWidth() - 4);
            } else {
                graphics.drawString(font, s.getName(), lx, ly, 0xFFCCEEDD, false);
            }
            if (s.getId().equals(selectedSectionId)) {
                drawSectionResizeHandles(graphics, s);
            }
        }

        int gmx = screenToGraphX(mouseX);
        int gmy = screenToGraphY(mouseY);
        if (!isSearching
                && linkingNode == null
                && draggingWireConnIdx < 0
                && !isCreatingSection
                && isInsideEditorPanel(mouseX, mouseY)) {
            updateWireInteractionHover(gmx, gmy);
        } else {
            clearWireHover();
            invalidateHoverCache();
        }
        int wi = 0;
        for (WConnection conn : graph.getConnections()) {
            drawConnection(graphics, conn, wi++);
        }

        if (linkingNode != null) {
            int sx = linkingNode.getX() + linkingNode.getWidth();
            int sy = linkingNode.getY() + 18 + linkingPin * 12;
            int tx = screenToGraphX(mouseX);
            int ty = screenToGraphY(mouseY);
            drawSmoothCurve(graphics, sx, sy, tx, ty, 0xAAFFFFFF, 1.5f);
        } else if (pendingWireFromNode != null && isSearching && pendingWireDragFrozen) {
            int sx = pendingWireFromNode.getX() + pendingWireFromNode.getWidth();
            int sy = pendingWireFromNode.getY() + 18 + pendingWireFromOutputPin * 12;
            drawSmoothCurve(graphics, sx, sy, pendingWireFrozenTx, pendingWireFrozenTy, 0xAAFFFFFF, 1.5f);
        }

        List<WNode> drawNodes = new ArrayList<>(graph.getNodes());
        sortNodesForDrawOrder(drawNodes);
        int z = 0;
        for (WNode node : drawNodes) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, z++ * 10);
            node.render(graphics, screenToGraphX(mouseX), screenToGraphY(mouseY), partialTick);
            if (node instanceof FunctionCardNode fc) {
                if (innerGraphHasLockedPeripheral(fc.getInnerGraph())) {
                    drawEditorPeripheralLockOverlay(graphics, node);
                }
            } else if (isEditorPeripheralLocked(node.getTypeId())) {
                drawEditorPeripheralLockOverlay(graphics, node);
            }
            graphics.pose().popPose();
        }

        if (isSelecting) {
            float x1 = (float) Math.min(selStartX, selEndX);
            float y1 = (float) Math.min(selStartY, selEndY);
            float x2 = (float) Math.max(selStartX, selEndX);
            float y2 = (float) Math.max(selStartY, selEndY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 2000);
            graphics.fill((int) x1, (int) y1, (int) x2, (int) y2, 0x3300FF88);
            graphics.renderOutline((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1), 0xFF00FF88);
            graphics.pose().popPose();
        }
        if (isCreatingSection) {
            int sx = Math.min(sectionCreateStartX, sectionCreateEndX);
            int sy = Math.min(sectionCreateStartY, sectionCreateEndY);
            int ex = Math.max(sectionCreateStartX, sectionCreateEndX);
            int ey = Math.max(sectionCreateStartY, sectionCreateEndY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 2000);
            graphics.fill(sx, sy, ex, ey, 0x332D66FF);
            graphics.renderOutline(sx, sy, ex - sx, ey - sy, 0xFF74A0FF);
            graphics.pose().popPose();
        }

        renderParticles(graphics, deltaTime);

        graphics.pose().popPose();

        graphics.disableScissor();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2500);

        renderNodeActionDock(graphics, mouseX, mouseY, ease);

        renderCenterViewButton(graphics, mouseX, mouseY, ease);
        renderSectionsSidebar(graphics, mouseX, mouseY, ease);
        renderSchematicToolbar(graphics, mouseX, mouseY, ease);
        renderShareToolbar(graphics, mouseX, mouseY, ease);

        if (isSearching) {
            rebuildSearchHitRows();
            if (searchQuery.trim().isEmpty()) {
                layoutBrowseMenuForPointer(mouseX, mouseY);
            } else {
                menuFlyoutPath.clear();
                stickyBrowseRootId = null;
                clampSearchMenuOnScreen();
            }
            renderSearchMenu(graphics);
        }

        renderSectionColorPickerOverlay(graphics);

        graphics.pose().popPose();

        renderItemPickerOverlay(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 9000);
        renderExportDialog(graphics);
        renderImportFromStringDialog(graphics);
        graphics.pose().popPose();

        if (newFunctionNamingOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 5200);
            renderNewFunctionNamingOverlay(graphics);
            graphics.pose().popPose();
        }
    }

    private void renderNewFunctionNamingOverlay(GuiGraphics graphics) {
        if (!newFunctionNamingOpen || functionStore == null) {
            return;
        }
        graphics.fill(0, 0, width, height, 0x88000000);
        int boxW = 300;
        int boxH = 76;
        int bx = width / 2 - boxW / 2;
        int by = height / 3;
        drawMenuPanel(graphics, bx, by, boxW, boxH);
        graphics.drawString(font, "Name new function", bx + 8, by + 8, 0xFF669966, false);
        String show = newFunctionNameBuffer.isEmpty() ? "_" : newFunctionNameBuffer + "_";
        graphics.drawString(font, show, bx + 8, by + 30, 0xFFEAF0FF, false);
        graphics.drawString(font, "Enter: create   Esc: cancel", bx + 8, by + boxH - 18, 0xFF888888, false);
    }

    private void renderSectionsSidebar(GuiGraphics graphics, int mx, int my, float ease) {
        int tx = sectionsToggleX();
        int ty = sectionsToggleY();
        graphics.fill(tx, ty, tx + FULLSCREEN_BTN, ty + FULLSCREEN_BTN, ((int) (200 * ease) << 24) | 0x2a2a2a);
        graphics.renderOutline(tx, ty, FULLSCREEN_BTN, FULLSCREEN_BTN, 0xFF777777);
        ResourceLocation sidebarIcon = showSectionsSidebar ? ICON_SIDEBAR_OPEN : ICON_SIDEBAR_CLOSED;
        int six = tx + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        int siy = ty + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        graphics.blit(sidebarIcon, six, siy, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        if (!showSectionsSidebar) {
            return;
        }
        int x = sectionsSidebarX();
        int y = sectionsSidebarY();
        int w = sectionsSidebarW();
        int h = sectionsSidebarH();
        graphics.fill(x, y, x + w, y + h, ((int) (220 * ease) << 24) | 0x121814);
        graphics.renderOutline(x, y, w, h, 0xFF3D8B6A);
        graphics.drawString(font, "Sections", x + 6, y + 4, 0xFFAAE8C8, false);
        int ry = y + 18;
        for (WGraph.WSection s : sectionsSortedByLayer(graph.getSections())) {
            if (ry + 14 > y + h - 4) {
                break;
            }
            boolean renaming = s.getId().equals(renamingSectionId);
            boolean selected = s.getId().equals(selectedSectionId);
            if (renaming) {
                graphics.fill(x + 2, ry - 1, x + w - 2, ry + 12, 0xEE081A12);
            } else if (selected) {
                graphics.fill(x + 2, ry - 1, x + w - 2, ry + 12, 0x552A6644);
            }
            int lx = x + 6;
            if (renaming) {
                drawSectionRenameTextWithSelectionAndCaret(graphics, lx, ry, x + w - 6);
            } else {
                int textColor = selected ? 0xFFE8FFF4 : 0xFF9AB8A8;
                graphics.drawString(font, s.getName(), lx, ry, textColor, false);
            }
            ry += 13;
        }
    }

    private void renderCenterViewButton(GuiGraphics graphics, int mx, int my, float ease) {
        int x = centerViewBtnX();
        int y = centerViewBtnY();
        boolean hov = centerViewBtnContains(mx, my);
        int fill = hov ? 0x3A3A3A : 0x2A2A2A;
        graphics.fill(x, y, x + CENTER_BTN_W, y + CENTER_BTN_H, ((int) (200 * ease) << 24) | fill);
        graphics.renderOutline(x, y, CENTER_BTN_W, CENTER_BTN_H, ((int) (255 * ease) << 24) | 0x777777);
        String label = "Center View";
        int tx = x + (CENTER_BTN_W - font.width(label)) / 2;
        int ty = y + (CENTER_BTN_H - font.lineHeight) / 2 + 1;
        graphics.drawString(font, label, tx, ty, 0xFFE8F0FF, false);
    }

    private int schematicBtnX() {
        return viewInset() + FULLSCREEN_BTN_PAD;
    }

    private int schematicBtnY() {
        return viewInset() + FULLSCREEN_BTN_PAD;
    }

    private boolean schematicBtnContains(double mx, double my) {
        if (functionStore == null) {
            return false;
        }
        int x = schematicBtnX();
        int y = schematicBtnY();
        return mx >= x && mx < x + FULLSCREEN_BTN && my >= y && my < y + FULLSCREEN_BTN;
    }

    private int shareExportBtnX() {
        return schematicBtnX() + FULLSCREEN_BTN + 4;
    }

    private int shareExportBtnY() {
        return schematicBtnY();
    }

    private int shareImportBtnX() {
        return shareExportBtnX() + FULLSCREEN_BTN + 4;
    }

    private int shareImportBtnY() {
        return schematicBtnY();
    }

    private boolean shareExportBtnContains(double mx, double my) {
        int x = shareExportBtnX();
        int y = shareExportBtnY();
        return mx >= x && mx < x + FULLSCREEN_BTN && my >= y && my < y + FULLSCREEN_BTN;
    }

    private boolean shareImportBtnContains(double mx, double my) {
        int x = shareImportBtnX();
        int y = shareImportBtnY();
        return mx >= x && mx < x + FULLSCREEN_BTN && my >= y && my < y + FULLSCREEN_BTN;
    }

    private void postShareStatus(boolean error, String key, Object... args) {
        importDialogStatusError = error;
        importDialogStatus = Component.translatable(key, args).getString();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    private void openExportDialog() {
        try {
            if (functionStore != null) {
                functionStore.syncBodiesFromGraph(graph);
            }
            String out = ComputedGraphShareCodec.encode(graph, functionStore);
            exportDialogText = out;
            exportDialogOpen = true;
            importDialogOpen = false;
            postShareStatus(false, "gui.computed.share.export_ready", out.length());
            playUiClick(1.05f);
        } catch (Exception e) {
            postShareStatus(true, "gui.computed.share.export_failed");
            playUiClick(0.82f);
        }
    }

    private void openImportFromStringDialog() {
        importDialogOpen = true;
        exportDialogOpen = false;
        importDialogText = "";
        importDialogStatus = "";
        importDialogStatusError = false;
    }

    private void closeImportFromStringDialog() {
        importDialogOpen = false;
    }

    private void closeExportDialog() {
        exportDialogOpen = false;
    }

    private void importGraphFromShareString(String input) {
        try {
            ComputedGraphShareCodec.Decoded decoded = ComputedGraphShareCodec.decode(input);
            recordCheckpointBeforeEdit();
            graph.load(decoded.graph());
            if (functionStore != null) {
                functionStore.load(decoded.functions());
                FunctionCardNode.applyLibraryToInnerGraphs(graph, functionStore);
            }
            postShareStatus(
                    false,
                    decoded.legacy()
                            ? "gui.computed.share.import_success_legacy"
                            : "gui.computed.share.import_success",
                    decoded.embeddedCustomNodeCount());
            closeImportFromStringDialog();
            playUiClick(1.07f);
        } catch (Exception e) {
            postShareStatus(true, "gui.computed.share.import_failed");
            playUiClick(0.82f);
        }
    }

    private int schematicPickerW() {
        return Math.max(Math.max(menuMinColWidth(), 140), FUNCTION_LIB_PANEL_W);
    }

    /**
     * Padding + title + “New” + fixed-height definitions viewport + open-folder + import row (matches
     * {@link #renderSchematicToolbar}).
     */
    private int schematicPickerH() {
        if (functionStore == null) {
            return 0;
        }
        int rh = menuRowHeight();
        return 12
                + functionPickerPlacedSectionHeight()
                + FUNCTION_LIB_TITLE_H
                + rh
                + FUNCTION_LIB_VISIBLE_ROWS * FUNCTION_LIB_NAME_ROW_H
                + rh
                + rh
                + LIBRARY_HINT_BLOCK_H;
    }

    private int functionPickerDefsStartY(int panelTop) {
        return panelTop + 6 + functionPickerPlacedSectionHeight() + FUNCTION_LIB_TITLE_H + menuRowHeight();
    }

    private int functionPickerFolderRowY(int panelTop) {
        return functionPickerDefsStartY(panelTop) + FUNCTION_LIB_VISIBLE_ROWS * FUNCTION_LIB_NAME_ROW_H;
    }

    private int functionPickerDefsViewportHeight() {
        return FUNCTION_LIB_VISIBLE_ROWS * FUNCTION_LIB_NAME_ROW_H;
    }

    private boolean functionPickerDefsViewportContains(double mx, double my) {
        if (!functionPickerOpen || functionStore == null) {
            return false;
        }
        int px = schematicPickerX();
        int py = schematicPickerY();
        int pw = schematicPickerW();
        int defsTop = functionPickerDefsStartY(py);
        int vh = functionPickerDefsViewportHeight();
        return mx >= px && mx < px + pw && my >= defsTop && my < defsTop + vh;
    }

    /** Computes flyout bounds (must match {@link #renderFunctionImportFlyout}). */
    private void layoutFunctionImportFlyout(int[] outXYWH) {
        int px = schematicPickerX();
        int py = schematicPickerY();
        int pw = schematicPickerW();
        int impY = functionPickerImportRowY(py);
        int nf = functionDiscImportFiles.size();
        int rowH = FUNCTION_LIB_NAME_ROW_H;
        int vis = FUNCTION_LIB_VISIBLE_ROWS;
        int fh;
        if (nf == 0) {
            fh = 6 + menuRowHeight() + 6;
        } else {
            fh = 6 + vis * rowH + 6 + (nf > vis ? 12 : 0);
        }
        int fw = Math.max(pw, 172);
        int fx = px + pw + 3;
        int fy = impY;
        int er = menuEdgeRight();
        if (fx + fw > er) {
            fx = px - fw - 3;
        }
        int eb = menuEdgeBottom();
        if (fy + fh > eb) {
            fy = eb - fh;
        }
        int et = menuEdgeTop();
        if (fy < et) {
            fy = et;
        }
        outXYWH[0] = fx;
        outXYWH[1] = fy;
        outXYWH[2] = fw;
        outXYWH[3] = fh;
    }

    private boolean functionImportFlyoutContains(double mx, double my) {
        if (!functionImportSubmenuOpen || clientNestedFunctionsDirectory() == null) {
            return false;
        }
        int[] b = new int[4];
        layoutFunctionImportFlyout(b);
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    private int functionPickerImportRowY(int panelTop) {
        return functionPickerFolderRowY(panelTop) + menuRowHeight();
    }

    private int schematicPickerX() {
        int px = schematicBtnX();
        int pw = schematicPickerW();
        int el = menuEdgeLeft();
        int er = menuEdgeRight();
        if (px + pw > er) {
            px = er - pw;
        }
        return Math.max(px, el);
    }

    private int schematicPickerY() {
        int py = schematicBtnY() + FULLSCREEN_BTN + 4;
        int ph = schematicPickerH();
        int et = menuEdgeTop();
        int eb = menuEdgeBottom();
        if (py + ph > eb) {
            py = eb - ph;
        }
        return Math.max(py, et);
    }

    private boolean functionPickerPanelContains(double mx, double my) {
        if (!functionPickerOpen || functionStore == null) {
            return false;
        }
        int px = schematicPickerX();
        int py = schematicPickerY();
        int pw = schematicPickerW();
        int ph = schematicPickerH();
        return mx >= px && mx < px + pw && my >= py && my < py + ph;
    }

    private void drawLibraryFnRenameTextWithSelectionAndCaret(
            GuiGraphics graphics, int lx, int ly, int maxTextRight) {
        int selStart = Math.min(libraryFnRenameCursor, libraryFnRenameSelectionPos);
        int selEnd = Math.max(libraryFnRenameCursor, libraryFnRenameSelectionPos);
        if (selStart != selEnd) {
            int left = lx + font.width(libraryFnRenameBuffer.substring(0, selStart));
            int right = lx + font.width(libraryFnRenameBuffer.substring(0, selEnd));
            left = Math.min(left, maxTextRight);
            right = Math.min(right, maxTextRight);
            if (left < right) {
                graphics.fill(left, ly, right, ly + Math.max(8, font.lineHeight), 0x664A90FF);
            }
        }
        graphics.drawString(font, libraryFnRenameBuffer, lx, ly, 0xFFEAF0FF, false);
        drawSectionRenameCaret(
                graphics,
                lx,
                ly,
                maxTextRight,
                libraryFnRenameBuffer.substring(0, libraryFnRenameCursor));
    }

    private void refreshFunctionDiscFileList() {
        functionDiscImportFiles.clear();
        Path root = clientNestedFunctionsDirectory();
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(functionDiscImportFiles::add);
        } catch (IOException ignored) {
        }
    }

    /** Adds a new entry to {@link #functionStore} from the {@code .nbt} at {@code fileIndex} (full inner graph tag). */
    private void importDiscFileAtIndex(int fileIndex) {
        Path root = clientNestedFunctionsDirectory();
        if (root == null || functionDiscImportFiles.isEmpty()) {
            playUiClick(0.9f);
            return;
        }
        int n = functionDiscImportFiles.size();
        int idx = Mth.clamp(fileIndex, 0, n - 1);
        Path file = functionDiscImportFiles.get(idx);
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (!tag.contains("nodes", Tag.TAG_LIST)) {
                playUiClick(0.85f);
                return;
            }
            String base = file.getFileName().toString();
            if (base.endsWith(".nbt")) {
                base = base.substring(0, base.length() - 4);
            }
            if (base.isEmpty()) {
                base = "imported";
            }
            recordCheckpointBeforeEdit();
            functionStore.addNew(base, tag.copy());
            playUiClick(1.08f);
        } catch (IOException e) {
            playUiClick(0.85f);
        }
    }

    private void renderFunctionImportFlyout(GuiGraphics graphics, int mx, int my) {
        if (!functionImportSubmenuOpen || clientNestedFunctionsDirectory() == null) {
            return;
        }
        int nf = functionDiscImportFiles.size();
        functionDiscImportListScroll =
                Mth.clamp(functionDiscImportListScroll, 0, Math.max(0, nf - FUNCTION_LIB_VISIBLE_ROWS));
        int[] b = new int[4];
        layoutFunctionImportFlyout(b);
        int fx = b[0];
        int fy = b[1];
        int fw = b[2];
        int fh = b[3];
        drawMenuPanel(graphics, fx, fy, fw, fh);
        int rowH = FUNCTION_LIB_NAME_ROW_H;
        int vis = FUNCTION_LIB_VISIBLE_ROWS;
        int contentTop = fy + 6;
        if (nf == 0) {
            graphics.drawString(font, "(no .nbt)", fx + 6, contentTop + 2, 0xFF666666, false);
            return;
        }
        int footerGap = nf > vis ? 12 : 0;
        int listClipBottom = contentTop + vis * rowH;
        int flyListRight = fx + fw - 2 - SCROLLER_TRACK_W;
        graphics.enableScissor(fx + 1, contentTop, flyListRight, listClipBottom);
        int show = Math.min(vis, Math.max(0, nf - functionDiscImportListScroll));
        for (int j = 0; j < show; j++) {
            Path fp = functionDiscImportFiles.get(functionDiscImportListScroll + j);
            int rowY = contentTop + j * rowH;
            boolean hr = mx >= fx && mx < fx + fw && my >= rowY && my < rowY + rowH;
            if (hr) {
                graphics.fill(fx + 1, rowY, flyListRight, rowY + rowH, 0x4400FF88);
            }
            String name = fp.getFileName().toString();
            int mw = flyListRight - fx - 10;
            if (font.width(name) > mw) {
                while (name.length() > 2 && font.width(name + "…") > mw) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            graphics.drawString(font, name, fx + 6, rowY + 1, 0xFFEAF0FF, false);
        }
        graphics.disableScissor();
        drawInsetVerticalScroller(
                graphics,
                fx + fw - 2 - SCROLLER_TRACK_W,
                contentTop,
                vis * rowH,
                functionDiscImportListScroll,
                nf,
                vis);
        if (footerGap > 0) {
            int from = functionDiscImportListScroll + 1;
            int to = functionDiscImportListScroll + show;
            String footer = from + "–" + to + " / " + nf;
            graphics.drawString(font, footer, fx + 6, fy + fh - 11, 0xFF778899, false);
        }
    }

    private void renderSchematicToolbar(GuiGraphics graphics, int mx, int my, float ease) {
        if (functionStore == null) {
            return;
        }
        int tx = schematicBtnX();
        int ty = schematicBtnY();
        boolean hov = schematicBtnContains(mx, my);
        int alphaBg = (int) (200 * ease);
        graphics.fill(tx, ty, tx + FULLSCREEN_BTN, ty + FULLSCREEN_BTN, (alphaBg << 24) | (hov ? 0x3a3a3a : 0x2a2a2a));
        graphics.renderOutline(tx, ty, FULLSCREEN_BTN, FULLSCREEN_BTN, ((int) (255 * ease) << 24) | 0x777777);
        int six = tx + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        int siy = ty + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        graphics.blit(ICON_SCHEMATIC, six, siy, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        if (functionPickerOpen) {
            int px = schematicPickerX();
            int py = schematicPickerY();
            int pw = schematicPickerW();
            int ph = schematicPickerH();
            drawMenuPanel(graphics, px, py, pw, ph);
            int rh = menuRowHeight();
            int placedH = functionPickerPlacedSectionHeight();
            graphics.fill(px + 2, py + 2, px + pw - 2, py + 6 + placedH + FUNCTION_LIB_TITLE_H, 0x33101822);
            int contentY = py + 6;
            List<Component> placedLines = placedPeripheralHudLines();
            if (!placedLines.isEmpty()) {
                graphics.drawString(
                        font,
                        Component.translatable("gui.computed.placed_hardware_title"),
                        px + 6,
                        contentY,
                        0xFF88DDAA,
                        false);
                contentY += FUNCTION_LIB_TITLE_H;
                for (Component line : placedLines) {
                    graphics.drawString(font, line, px + 8, contentY, 0xFFC0D8C0, false);
                    contentY += font.lineHeight;
                }
                contentY += 4;
            }
            graphics.drawString(font, "Functions", px + 6, contentY, 0xFFAACCEE, false);
            int ry = contentY + FUNCTION_LIB_TITLE_H;
            boolean hNew = mx >= px && mx < px + pw && my >= ry && my < ry + rh;
            int newColor = hNew ? 0xFFFFFFFF : 0xFF888888;
            if (hNew) {
                graphics.fill(px + 1, ry, px + pw - 1, ry + rh, 0x4400FF88);
            }
            graphics.drawString(font, "+ New function", px + 6, ry + 2, newColor, false);
            ry += rh;
            List<FunctionDefinitionStore.Definition> defs =
                    new ArrayList<>(functionStore.definitionsInOrder());
            int defsTop = ry;
            int defsViewportH = functionPickerDefsViewportHeight();
            int nDefs = defs.size();
            functionLibraryListScroll =
                    Mth.clamp(functionLibraryListScroll, 0, Math.max(0, nDefs - FUNCTION_LIB_VISIBLE_ROWS));
            int defsListRight = px + pw - 2 - SCROLLER_TRACK_W;
            graphics.enableScissor(px + 1, defsTop, defsListRight, defsTop + defsViewportH);
            int visibleDefRows = Math.min(FUNCTION_LIB_VISIBLE_ROWS, Math.max(0, nDefs - functionLibraryListScroll));
            int defsTextMaxRight = defsListRight - 4;
            for (int j = 0; j < visibleDefRows; j++) {
                FunctionDefinitionStore.Definition def = defs.get(functionLibraryListScroll + j);
                int rowY = defsTop + j * FUNCTION_LIB_NAME_ROW_H;
                boolean renaming = def.id().equals(renamingLibraryFunctionId);
                boolean selected = def.id().equals(selectedLibraryFunctionId);
                boolean hwLocked = isFunctionLibraryDefinitionHardwareLocked(def);
                boolean hr =
                        mx >= px && mx < px + pw && my >= rowY && my < rowY + FUNCTION_LIB_NAME_ROW_H;
                if (renaming) {
                    graphics.fill(px + 2, rowY - 1, defsListRight, rowY + FUNCTION_LIB_NAME_ROW_H - 2, 0xEE080E14);
                } else if (selected) {
                    graphics.fill(px + 2, rowY - 1, defsListRight, rowY + FUNCTION_LIB_NAME_ROW_H - 2, 0x445C74CC);
                } else if (hr && !hwLocked) {
                    graphics.fill(px + 2, rowY - 1, defsListRight, rowY + FUNCTION_LIB_NAME_ROW_H - 2, 0x3300AA66);
                } else if (hwLocked) {
                    graphics.fill(px + 2, rowY - 1, defsListRight, rowY + FUNCTION_LIB_NAME_ROW_H - 2, 0x88201818);
                }
                int lx = px + 6;
                if (renaming) {
                    drawLibraryFnRenameTextWithSelectionAndCaret(graphics, lx, rowY, defsTextMaxRight);
                } else {
                    int tColor =
                            hwLocked ? 0xFF886666 : (selected ? 0xFFFFFFFF : 0xFFAFB7D0);
                    graphics.drawString(font, def.name(), lx, rowY, tColor, false);
                    if (hwLocked) {
                        String hint = Component.translatable("gui.computed.function_needs_peripheral").getString();
                        int hx = defsTextMaxRight - font.width(hint);
                        if (hx > lx + font.width(def.name()) + 4) {
                            graphics.drawString(font, hint, hx, rowY, 0xFFFF6666, false);
                        }
                    }
                }
            }
            graphics.disableScissor();
            drawInsetVerticalScroller(
                    graphics,
                    px + pw - 2 - SCROLLER_TRACK_W,
                    defsTop,
                    defsViewportH,
                    functionLibraryListScroll,
                    nDefs,
                    FUNCTION_LIB_VISIBLE_ROWS);
            ry = defsTop + defsViewportH;
            int folderY = functionPickerFolderRowY(py);
            boolean hFolder =
                    mx >= px && mx < px + pw && my >= folderY && my < folderY + rh && clientNestedFunctionsDirectory() != null;
            if (hFolder) {
                graphics.fill(px + 1, folderY, px + pw - 1, folderY + rh, 0x4400AAFF);
            }
            int ic = px + 8;
            int iy = folderY + (rh - ICON_SIZE) / 2;
            float ft = clientNestedFunctionsDirectory() != null ? 1f : 0.35f;
            graphics.setColor(ft, ft, ft, ease);
            graphics.blit(ICON_FOLDER, ic, iy, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            graphics.setColor(1f, 1f, 1f, 1f);
            graphics.drawString(
                    font,
                    "Open folder",
                    px + 8 + ICON_SIZE + 4,
                    folderY + (rh - font.lineHeight) / 2 + 1,
                    clientNestedFunctionsDirectory() != null ? 0xFFCCCCCC : 0xFF555555,
                    false);

            int impY = functionPickerImportRowY(py);
            boolean hImp = mx >= px && mx < px + pw && my >= impY && my < impY + rh;
            int nf = functionDiscImportFiles.size();
            boolean diskOk = clientNestedFunctionsDirectory() != null;
            if ((hImp || functionImportSubmenuOpen) && diskOk) {
                graphics.fill(px + 1, impY, px + pw - 1, impY + rh, 0x4400FF88);
            }
            graphics.drawString(font, "Import…", px + 6, impY + 2, diskOk ? 0xFF669966 : 0xFF445544, false);
            String subHint =
                    !diskOk ? "(save path N/A)" : nf == 0 ? "(no .nbt)" : nf + " file" + (nf == 1 ? "" : "s");
            int subColor = diskOk ? 0xFF888888 : 0xFF555555;
            graphics.drawString(font, subHint, px + 56, impY + 2, subColor, false);
            String chevron = (!diskOk || nf == 0) ? "" : "›";
            if (!chevron.isEmpty()) {
                graphics.drawString(font, chevron, px + pw - font.width(chevron) - 6, impY + 2, 0xFFAACCEE, false);
            }
            drawFunctionLibraryFooterHints(graphics, px, py + ph - LIBRARY_HINT_BLOCK_H);
            renderFunctionImportFlyout(graphics, mx, my);
        }

        if (nestedFunctionDiskToolbarVisible()) {
            renderNestedFunctionDiskToolbar(graphics, mx, my, ease);
        }
    }

    private void renderShareToolbar(GuiGraphics graphics, int mx, int my, float ease) {
        int alphaBg = (int) (200 * ease);
        int ex = shareExportBtnX();
        int ey = shareExportBtnY();
        int ix = shareImportBtnX();
        int iy = shareImportBtnY();
        boolean hExport = shareExportBtnContains(mx, my);
        boolean hImport = shareImportBtnContains(mx, my);
        graphics.fill(ex, ey, ex + FULLSCREEN_BTN, ey + FULLSCREEN_BTN, (alphaBg << 24) | (hExport ? 0x3a3a3a : 0x2a2a2a));
        graphics.renderOutline(ex, ey, FULLSCREEN_BTN, FULLSCREEN_BTN, ((int) (255 * ease) << 24) | 0x777777);
        graphics.fill(ix, iy, ix + FULLSCREEN_BTN, iy + FULLSCREEN_BTN, (alphaBg << 24) | (hImport ? 0x3a3a3a : 0x2a2a2a));
        graphics.renderOutline(ix, iy, FULLSCREEN_BTN, FULLSCREEN_BTN, ((int) (255 * ease) << 24) | 0x777777);
        int exi = ex + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        int eyi = ey + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        int ixi = ix + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        int iyi = iy + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        graphics.blit(ICON_EXPORT, exi, eyi, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        graphics.blit(ICON_UPLOAD, ixi, iyi, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    private void renderImportFromStringDialog(GuiGraphics graphics) {
        if (!importDialogOpen) {
            return;
        }
        graphics.fill(0, 0, width, height, 0x88000000);
        int boxW = Math.min(540, width - 40);
        int boxH = Math.min(220, height - 40);
        int bx = width / 2 - boxW / 2;
        int by = height / 2 - boxH / 2;
        drawMenuPanel(graphics, bx, by, boxW, boxH);
        graphics.drawString(font, Component.translatable("gui.computed.share.import_title"), bx + 8, by + 8, 0xFFEAF0FF, false);
        int tx1 = bx + 8;
        int ty1 = by + 24;
        int tx2 = bx + boxW - 8;
        int ty2 = by + boxH - 44;
        graphics.fill(tx1, ty1, tx2, ty2, 0xFF000000);
        graphics.renderOutline(tx1, ty1, tx2 - tx1, ty2 - ty1, 0xFF5A6A5A);
        String display = importDialogText.isEmpty() ? Component.translatable("gui.computed.share.import_placeholder").getString() : importDialogText;
        int color = importDialogText.isEmpty() ? 0xFF667766 : 0xFFE8F0E8;
        graphics.enableScissor(tx1 + 2, ty1 + 2, tx2 - 2, ty2 - 2);
        int maxW = tx2 - tx1 - 8;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(display), maxW);
        int y = ty1 + 4;
        int start = Math.max(0, lines.size() - Math.max(1, (ty2 - ty1 - 8) / font.lineHeight));
        for (int i = start; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), tx1 + 4, y, color, false);
            y += font.lineHeight;
            if (y > ty2 - font.lineHeight) {
                break;
            }
        }
        graphics.disableScissor();

        int btnW = 88;
        int btnH = 20;
        int importX = bx + boxW - btnW * 2 - 16;
        int cancelX = bx + boxW - btnW - 8;
        int btnY = by + boxH - 30;
        graphics.fill(importX, btnY, importX + btnW, btnY + btnH, 0xFF2A4A3A);
        graphics.renderOutline(importX, btnY, btnW, btnH, 0xFF77AA88);
        graphics.drawString(font, Component.translatable("gui.computed.share.import_button"), importX + 16, btnY + 6, 0xFFFFFFFF, false);
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, 0xFF3A2A2A);
        graphics.renderOutline(cancelX, btnY, btnW, btnH, 0xFFAA7777);
        graphics.drawString(font, Component.translatable("gui.computed.share.cancel_button"), cancelX + 18, btnY + 6, 0xFFFFFFFF, false);

        if (!importDialogStatus.isEmpty()) {
            graphics.drawString(
                    font,
                    importDialogStatus,
                    bx + 8,
                    by + boxH - 28,
                    importDialogStatusError ? 0xFFFF8888 : 0xFF88CC88,
                    false);
        }
    }

    private void renderExportDialog(GuiGraphics graphics) {
        if (!exportDialogOpen) {
            return;
        }
        graphics.fill(0, 0, width, height, 0x88000000);
        int boxW = Math.min(540, width - 40);
        int boxH = Math.min(220, height - 40);
        int bx = width / 2 - boxW / 2;
        int by = height / 2 - boxH / 2;
        drawMenuPanel(graphics, bx, by, boxW, boxH);
        graphics.drawString(font, Component.translatable("gui.computed.share.export_title"), bx + 8, by + 8, 0xFFEAF0FF, false);
        int tx1 = bx + 8;
        int ty1 = by + 24;
        int tx2 = bx + boxW - 8;
        int ty2 = by + boxH - 44;
        graphics.fill(tx1, ty1, tx2, ty2, 0xFF000000);
        graphics.renderOutline(tx1, ty1, tx2 - tx1, ty2 - ty1, 0xFF5A6A5A);
        graphics.enableScissor(tx1 + 2, ty1 + 2, tx2 - 2, ty2 - 2);
        int maxW = tx2 - tx1 - 8;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(exportDialogText), maxW);
        int y = ty1 + 4;
        int start = Math.max(0, lines.size() - Math.max(1, (ty2 - ty1 - 8) / font.lineHeight));
        for (int i = start; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), tx1 + 4, y, 0xFFE8F0E8, false);
            y += font.lineHeight;
            if (y > ty2 - font.lineHeight) {
                break;
            }
        }
        graphics.disableScissor();

        int btnW = 88;
        int btnH = 20;
        int copyX = bx + boxW - btnW * 2 - 16;
        int closeX = bx + boxW - btnW - 8;
        int btnY = by + boxH - 30;
        graphics.fill(copyX, btnY, copyX + btnW, btnY + btnH, 0xFF2A4A3A);
        graphics.renderOutline(copyX, btnY, btnW, btnH, 0xFF77AA88);
        graphics.drawString(font, Component.translatable("gui.computed.share.copy_button"), copyX + 24, btnY + 6, 0xFFFFFFFF, false);
        graphics.fill(closeX, btnY, closeX + btnW, btnY + btnH, 0xFF3A2A2A);
        graphics.renderOutline(closeX, btnY, btnW, btnH, 0xFFAA7777);
        graphics.drawString(font, Component.translatable("gui.computed.share.close_button"), closeX + 22, btnY + 6, 0xFFFFFFFF, false);

        if (!importDialogStatus.isEmpty()) {
            graphics.drawString(
                    font,
                    importDialogStatus,
                    bx + 8,
                    by + boxH - 28,
                    importDialogStatusError ? 0xFFFF8888 : 0xFF88CC88,
                    false);
        }
    }

    /** Scales a 16×16 UI tile to {@code drawPx} for key caps and small icons. */
    private void blitScaledHintTile(GuiGraphics graphics, ResourceLocation icon, int x, int y, int drawPx) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float s = drawPx / (float) ICON_SIZE;
        graphics.pose().scale(s, s, 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        graphics.pose().popPose();
    }

    /**
     * Inset vertical scrollbar track with a thumb from {@link #ICON_SCROLLER_MULTICOLOR} /
     * {@link #ICON_SCROLLER_DISABLED}. The thumb uses uniform scale (aspect preserved), is horizontally
     * centered in the pit, and is top-aligned in the proportional scroll slot (including when disabled).
     * {@code scroll} and {@code totalItems} match list semantics (e.g. {@link #functionLibraryListScroll}).
     */
    private void drawInsetVerticalScroller(
            GuiGraphics graphics, int trackLeft, int trackTop, int trackH, int scroll, int totalItems, int visibleRows) {
        if (trackH < 10) {
            return;
        }
        int trackW = SCROLLER_TRACK_W;
        int trackRight = trackLeft + trackW;
        int trackBottom = trackTop + trackH;
        int edgeGray = 0xFF4a5260;
        int pitBase = 0xFF0c0e14;
        int bevelDark = 0xFF2a3038;
        int bevelLight = 0xFF5a6270;
        // 1px chrome on all sides so top/left read against the panel like bottom/right.
        graphics.fill(trackLeft, trackTop, trackRight, trackTop + 1, edgeGray);
        graphics.fill(trackLeft, trackBottom - 1, trackRight, trackBottom, edgeGray);
        graphics.fill(trackLeft, trackTop + 1, trackLeft + 1, trackBottom - 1, edgeGray);
        graphics.fill(trackRight - 1, trackTop + 1, trackRight, trackBottom - 1, edgeGray);
        graphics.fill(trackLeft + 1, trackTop + 1, trackRight - 1, trackBottom - 1, pitBase);
        // Inset pit: dark along top/left, light along bottom/right.
        graphics.fill(trackLeft + 1, trackTop + 1, trackRight - 1, trackTop + 2, bevelDark);
        graphics.fill(trackLeft + 1, trackTop + 2, trackLeft + 2, trackBottom - 1, bevelDark);
        graphics.fill(trackLeft + 1, trackBottom - 2, trackRight - 1, trackBottom - 1, bevelLight);
        graphics.fill(trackRight - 2, trackTop + 1, trackRight - 1, trackBottom - 1, bevelLight);
        int pitX = trackLeft + 2;
        int pitY = trackTop + 2;
        int pitW = trackW - 4;
        int pitH = trackH - 4;
        if (pitW < 2 || pitH < 6) {
            return;
        }
        graphics.fill(pitX, pitY, pitX + pitW, pitY + pitH, 0xFF080a10);
        graphics.fill(pitX, pitY, pitX + pitW, pitY + pitH, 0xFF080a10);
        boolean active = totalItems > visibleRows;
        int maxScroll = Math.max(0, totalItems - visibleRows);
        ResourceLocation tex = active ? ICON_SCROLLER_MULTICOLOR : ICON_SCROLLER_DISABLED;
        float fracVisible = visibleRows / (float) Math.max(1, totalItems);
        int slotH = active ? Mth.clamp(Mth.ceil(fracVisible * pitH), 8, pitH) : pitH;
        int thumbTravel = pitH - slotH;
        int slotY = pitY + (maxScroll <= 0 ? 0 : (int) Math.round(scroll * (thumbTravel / (float) maxScroll)));
        float s = Math.min(pitW / (float) SCROLLER_TEX_W, slotH / (float) SCROLLER_TEX_H);
        float drawW = SCROLLER_TEX_W * s;
        float drawH = SCROLLER_TEX_H * s;
        float ox = pitX + (pitW - drawW) / 2f;
        // Top-align thumb in slot (including disabled full-track) so it never floats mid-track.
        float oy = slotY;
        graphics.pose().pushPose();
        graphics.pose().translate(ox, oy, 0);
        graphics.pose().scale(s, s, 1f);
        graphics.blit(tex, 0, 0, 0, 0, SCROLLER_TEX_W, SCROLLER_TEX_H, SCROLLER_TEX_W, SCROLLER_TEX_H);
        graphics.pose().popPose();
    }

    private void drawFunctionLibraryFooterHints(GuiGraphics graphics, int x, int y) {
        int hintColor = 0xFF556677;
        int rowStride = LIBRARY_HINT_ICON + 4;
        int tilePx = ICON_SIZE;
        int keyGap = 2;
        int iconStripW = tilePx + keyGap + tilePx;
        int hintTextX = x + iconStripW + 6;
        int iconY = y + (LIBRARY_HINT_ICON - tilePx) / 2;
        int ty1 = y + (LIBRARY_HINT_ICON - font.lineHeight) / 2 + 1;
        blitScaledHintTile(graphics, KEY_CAP_ALT, x, iconY, tilePx);
        blitScaledHintTile(graphics, ICON_UI_CLICK, x + tilePx + keyGap, iconY, tilePx);
        graphics.drawString(font, "Place card", hintTextX, ty1, hintColor, false);
        int y2 = y + rowStride;
        int iconY2 = y2 + (LIBRARY_HINT_ICON - tilePx) / 2;
        int ty2 = y2 + (LIBRARY_HINT_ICON - font.lineHeight) / 2 + 1;
        int doubleClickX = x + (iconStripW - tilePx) / 2;
        blitScaledHintTile(graphics, ICON_UI_DOUBLE_CLICK, doubleClickX, iconY2, tilePx);
        graphics.drawString(font, "Rename", hintTextX, ty2, hintColor, false);
    }

    private boolean nestedFunctionDiskToolbarVisible() {
        return functionStore != null && isEditingNestedFunction();
    }

    private int nestedDiskToolbarY() {
        return schematicBtnY() + FULLSCREEN_BTN + 6;
    }

    private int nestedDiskToolbarBtnX(int index) {
        return schematicBtnX() + index * (FULLSCREEN_BTN + 4);
    }

    private boolean nestedDiskToolbarBtnContains(double mx, double my, int index) {
        int x = nestedDiskToolbarBtnX(index);
        int y = nestedDiskToolbarY();
        return mx >= x && mx < x + FULLSCREEN_BTN && my >= y && my < y + FULLSCREEN_BTN;
    }

    private boolean nestedDiskOpEnabled(int index) {
        if (index == 1) {
            return clientNestedFunctionsDirectory() != null;
        }
        return true;
    }

    /** Single play/pause toggle + save when editing a function body. */
    private void renderNestedFunctionDiskToolbar(GuiGraphics graphics, int mx, int my, float ease) {
        int alphaBg = (int) (200 * ease);
        for (int i = 0; i < 2; i++) {
            int bx = nestedDiskToolbarBtnX(i);
            int by = nestedDiskToolbarY();
            boolean hov = nestedDiskToolbarBtnContains(mx, my, i);
            boolean enabled = nestedDiskOpEnabled(i);
            ResourceLocation icon = i == 0 ? (nestedFunctionTestPlaying ? ICON_PAUSE : ICON_PLAY) : ICON_SAVE_DISK;
            int fillRgb =
                    !enabled
                            ? 0x1a1a1a
                            : (nestedFunctionTestPlaying && i == 0)
                                    ? 0x2a4a3a
                                    : (hov ? 0x3a3a3a : 0x2a2a2a);
            graphics.fill(bx, by, bx + FULLSCREEN_BTN, by + FULLSCREEN_BTN, (alphaBg << 24) | fillRgb);
            graphics.renderOutline(
                    bx,
                    by,
                    FULLSCREEN_BTN,
                    FULLSCREEN_BTN,
                    ((int) (255 * ease) << 24) | (enabled ? 0x777777 : 0x444444));
            int ix = bx + (FULLSCREEN_BTN - ICON_SIZE) / 2;
            int iy = by + (FULLSCREEN_BTN - ICON_SIZE) / 2;
            float tint = enabled ? 1.0f : 0.35f;
            graphics.setColor(tint, tint, tint, ease);
            graphics.blit(icon, ix, iy, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            graphics.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void syncCurrentNestedFunctionToStore() {
        if (functionStore == null || !isEditingNestedFunction()) {
            return;
        }
        FunctionCardNode host = functionEditStack.peek().openedHost();
        FunctionDefinitionStore.Definition def = functionStore.get(host.getFunctionId());
        String name = def != null ? def.name() : "Function";
        functionStore.put(host.getFunctionId(), name, graph.save());
    }

    private void saveNestedFunctionToClientFile() {
        Path root = clientNestedFunctionsDirectory();
        if (root == null || !isEditingNestedFunction()) {
            playUiClick(0.85f);
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            playUiClick(0.85f);
            return;
        }
        FunctionCardNode host = functionEditStack.peek().openedHost();
        FunctionDefinitionStore.Definition def = functionStore.get(host.getFunctionId());
        String name = def != null ? def.name() : "Function";
        String base = safeFunctionFileBase(name);
        Path file = root.resolve(base + ".nbt");
        try {
            NbtIo.writeCompressed(graph.save(), file);
            playUiClick(1.06f);
        } catch (IOException e) {
            playUiClick(0.85f);
        }
    }

    private static String safeFunctionFileBase(String displayName) {
        String s = displayName == null ? "" : displayName.trim();
        if (s.isEmpty()) {
            return "function";
        }
        return s.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    }

    private void openNestedFunctionsFolder() {
        Path root = clientNestedFunctionsDirectory();
        if (root == null) {
            playUiClick(0.85f);
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            playUiClick(0.85f);
            return;
        }
        clientRevealNestedFunctionsFolder(root);
        playUiClick(1.02f);
    }

    private boolean handleNestedDiskToolbarClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !nestedFunctionDiskToolbarVisible()) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            if (!nestedDiskToolbarBtnContains(mouseX, mouseY, i)) {
                continue;
            }
            if (!nestedDiskOpEnabled(i)) {
                playUiClick(0.85f);
                return true;
            }
            if (i == 0) {
                nestedFunctionTestPlaying = !nestedFunctionTestPlaying;
                playUiClick(nestedFunctionTestPlaying ? 1.04f : 0.98f);
            } else {
                saveNestedFunctionToClientFile();
            }
            return true;
        }
        return false;
    }

    private void handleFunctionImportFlyoutClick(double mouseX, double mouseY) {
        int nf = functionDiscImportFiles.size();
        if (nf == 0) {
            return;
        }
        int[] b = new int[4];
        layoutFunctionImportFlyout(b);
        int fx = b[0];
        int fy = b[1];
        int fw = b[2];
        int rowH = FUNCTION_LIB_NAME_ROW_H;
        int vis = FUNCTION_LIB_VISIBLE_ROWS;
        int contentTop = fy + 6;
        int rowAreaBottom = contentTop + vis * rowH;
        if (mouseX < fx || mouseX >= fx + fw || mouseY < contentTop || mouseY >= rowAreaBottom) {
            return;
        }
        int row = (int) ((mouseY - contentTop) / rowH);
        if (row < 0 || row >= vis) {
            return;
        }
        int fileIdx = functionDiscImportListScroll + row;
        if (fileIdx >= nf) {
            return;
        }
        commitLibraryFunctionRename();
        importDiscFileAtIndex(fileIdx);
        functionImportSubmenuOpen = false;
    }

    private void handleFunctionPickerClick(double mouseX, double mouseY) {
        int px = schematicPickerX();
        int py = schematicPickerY();
        int pw = schematicPickerW();
        int rh = menuRowHeight();
        if (mouseX < px || mouseX >= px + pw || mouseY < py + 6 || mouseY >= py + schematicPickerH()) {
            return;
        }
        int impY = functionPickerImportRowY(py);
        boolean onImportRow = mouseY >= impY && mouseY < impY + rh;
        if (functionImportSubmenuOpen && !onImportRow) {
            functionImportSubmenuOpen = false;
        }
        int placedH = functionPickerPlacedSectionHeight();
        int titleEnd = py + 6 + placedH + FUNCTION_LIB_TITLE_H;
        int newTop = titleEnd;
        if (mouseY >= newTop && mouseY < newTop + rh) {
            commitLibraryFunctionRename();
            functionPickerOpen = false;
            newFunctionNamingOpen = true;
            newFunctionNameBuffer = "";
            playUiClick(1.02f);
            return;
        }
        int defsTop = newTop + rh;
        int defsViewportH = functionPickerDefsViewportHeight();
        if (mouseY >= defsTop && mouseY < defsTop + defsViewportH) {
            int row = (int) ((mouseY - defsTop) / FUNCTION_LIB_NAME_ROW_H);
            List<FunctionDefinitionStore.Definition> defs =
                    new ArrayList<>(functionStore.definitionsInOrder());
            int i = functionLibraryListScroll + row;
            if (row < 0 || row >= FUNCTION_LIB_VISIBLE_ROWS || i < 0 || i >= defs.size()) {
                return;
            }
            FunctionDefinitionStore.Definition def = defs.get(i);
            if (isFunctionLibraryDefinitionHardwareLocked(def)) {
                playUiClick(0.82f);
                return;
            }
            if (renamingLibraryFunctionId != null && !def.id().equals(renamingLibraryFunctionId)) {
                commitLibraryFunctionRename();
            }
            if (Screen.hasAltDown()) {
                placeFunctionCardFromLibrary(def.id(), mouseX, mouseY);
                return;
            }
            selectedLibraryFunctionId = def.id();
            long now = net.minecraft.Util.getMillis();
            if (def.id().equals(lastLibraryFunctionClickId)
                    && now - lastLibraryFunctionClickAtMs <= SECTION_DOUBLE_CLICK_MS) {
                startLibraryFunctionRename(def.id(), def.name());
            }
            lastLibraryFunctionClickId = def.id();
            lastLibraryFunctionClickAtMs = now;
            playUiClick(1.0f);
            return;
        }
        int folderY = functionPickerFolderRowY(py);
        if (mouseY >= folderY && mouseY < folderY + rh) {
            commitLibraryFunctionRename();
            openNestedFunctionsFolder();
            return;
        }
        if (onImportRow) {
            commitLibraryFunctionRename();
            if (clientNestedFunctionsDirectory() == null) {
                playUiClick(0.85f);
                return;
            }
            refreshFunctionDiscFileList();
            functionImportSubmenuOpen = !functionImportSubmenuOpen;
            if (functionImportSubmenuOpen) {
                functionDiscImportListScroll = 0;
            }
            playUiClick(functionImportSubmenuOpen ? 1.02f : 0.98f);
        }
    }

    private void placeFunctionCardFromLibrary(UUID functionId, double screenMx, double screenMy) {
        commitLibraryFunctionRename();
        if (functionStore != null) {
            FunctionDefinitionStore.Definition def = functionStore.get(functionId);
            if (def != null && isFunctionLibraryDefinitionHardwareLocked(def)) {
                playUiClick(0.82f);
                return;
            }
        }
        int nx = screenToGraphX(screenMx);
        int ny = screenToGraphY(screenMy);
        recordCheckpointBeforeEdit();
        graph.addNode(FunctionCardNode.createPlaced(nx, ny, functionId, functionStore));
        functionPickerOpen = false;
        playUiClick(1.06f);
    }

    private void renderFullscreenToggle(GuiGraphics graphics, int mx, int my, float ease) {
        int x = fullscreenBtnX();
        int y = fullscreenBtnY();
        int alphaBg = (int) (200 * ease);
        boolean hov = fullscreenBtnContains(mx, my);
        int fill = (alphaBg << 24) | (hov ? 0x3a3a3a : 0x2a2a2a);
        graphics.fill(x, y, x + FULLSCREEN_BTN, y + FULLSCREEN_BTN, fill);
        graphics.renderOutline(x, y, FULLSCREEN_BTN, FULLSCREEN_BTN, ((int) (255 * ease) << 24) | 0x777777);
        ResourceLocation icon = editorFullscreen ? ICON_MINIMIZE : ICON_MAXIMIZE;
        int ix = x + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        int iy = y + (FULLSCREEN_BTN - ICON_SIZE) / 2;
        graphics.blit(icon, ix, iy, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    private void clearStickyBrowseRoot() {
        stickyBrowseRootId = null;
    }

    /** Builds flyout path, then clamps menu position using that path's stack width. */
    private void layoutBrowseMenuForPointer(int mx, int my) {
        updateMenuFlyoutPath(mx, my);
        clampBrowseMenuOnScreen();
    }

    /** Left column (header + list); padded so clamp/layout jitter does not lose hover on the main panel. */
    private boolean menuMainColumnHitSloppy(int mx, int my, int pad) {
        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int mw = browsePanelWidth(mainRows, menuEdgeRight() - menuEdgeLeft());
        int h = menuHeaderHeight() + mainRows.size() * menuRowHeight();
        return mx >= menuX - pad
                && mx < menuX + mw + pad
                && my >= menuY - pad
                && my < menuY + h + pad;
    }

    private boolean isRootCategoryWithSubmenu(
            net.minecraft.resources.ResourceLocation id, java.util.List<BrowseRow> mainRows) {
        for (BrowseRow r : mainRows) {
            if (r instanceof BrowseCategoryRow b && b.id().equals(id)) {
                return submenuHasContent(id);
            }
        }
        return false;
    }

    private void rebuildSearchHitRows() {
        searchHitRows.clear();
        if (searchQuery.trim().isEmpty()) {
            return;
        }
        for (NodeMenuRegistry.MenuEntry e : NodeMenuRegistry.filterEntries(searchQuery)) {
            searchHitRows.add(new BrowseNodeRow(e.nodeType(), e.label()));
        }
    }

    private java.util.List<BrowseRow> browseRowsFor(net.minecraft.resources.ResourceLocation parentId) {
        java.util.ArrayList<BrowseRow> list = new java.util.ArrayList<>();
        for (NodeMenuRegistry.Category c : NodeMenuRegistry.getChildCategories(parentId)) {
            if (submenuHasContent(c.id())) {
                list.add(new BrowseCategoryRow(c.id(), c.title()));
            }
        }
        for (NodeMenuRegistry.MenuEntry e : NodeMenuRegistry.getEntriesIn(parentId)) {
            list.add(new BrowseNodeRow(e.nodeType(), e.label()));
        }
        return list;
    }

    private static boolean submenuHasContent(net.minecraft.resources.ResourceLocation catId) {
        return !NodeMenuRegistry.getChildCategories(catId).isEmpty()
                || !NodeMenuRegistry.getEntriesIn(catId).isEmpty();
    }

    private int browsePanelWidth(java.util.List<BrowseRow> rows, int maxWidth) {
        int w = menuMinColWidth();
        for (BrowseRow row : rows) {
            Component lab = row instanceof BrowseCategoryRow c ? c.label() : ((BrowseNodeRow) row).label();
            boolean sub = row instanceof BrowseCategoryRow c && submenuHasContent(c.id());
            w = Math.max(w, font.width(lab) + (sub ? 28 : 14));
        }
        int cap = Math.max(menuMinColWidth(), Math.min(maxWidth, menuEdgeRight() - menuEdgeLeft()));
        return Math.min(w, cap);
    }

    /** Total width of root + flyouts for {@code path} at current {@link #menuX}. */
    private int browseStackWidthPx(java.util.List<net.minecraft.resources.ResourceLocation> path) {
        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int maxStrip = menuEdgeRight() - menuEdgeLeft();
        int mainW = browsePanelWidth(mainRows, maxStrip);
        int left = menuX + mainW + MENU_GAP;
        int total = mainW;
        for (net.minecraft.resources.ResourceLocation catId : path) {
            java.util.List<BrowseRow> content = browseRowsFor(catId);
            int fw = browsePanelWidth(content, Math.max(menuMinColWidth(), menuEdgeRight() - left - MENU_GAP));
            total += MENU_GAP + fw;
            left += MENU_GAP + fw;
        }
        return total;
    }

    private void clampBrowseMenuOnScreen() {
        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int rh = menuRowHeight();
        int hh = menuHeaderHeight();
        int mainH = hh + mainRows.size() * rh;
        int stackW = browseStackWidthPx(menuFlyoutPath);

        int el = menuEdgeLeft();
        int er = menuEdgeRight();
        int et = menuEdgeTop();
        int eb = menuEdgeBottom();

        if (menuY + mainH > eb) {
            menuY = eb - mainH;
        }
        if (menuY < et) {
            menuY = et;
        }

        if (menuX + stackW > er) {
            menuX = Math.max(el, er - stackW);
        }
        if (menuX < el) {
            menuX = el;
        }
    }

    private int computeSearchMenuWidthPx() {
        int mw = menuMinColWidth();
        for (BrowseNodeRow r : searchHitRows) {
            mw = Math.max(mw, font.width(r.label()) + 20);
        }
        return Math.min(mw, menuEdgeRight() - menuEdgeLeft());
    }

    private int menuMaxSearchVisibleRows() {
        int rh = menuRowHeight();
        int hh = menuHeaderHeight();
        int avail = menuEdgeBottom() - menuY - hh - rh;
        return Math.max(3, Math.min(MENU_MAX_VISIBLE, avail / rh));
    }

    private void clampSearchMenuOnScreen() {
        int rh = menuRowHeight();
        int hh = menuHeaderHeight();
        int maxVis = menuMaxSearchVisibleRows();
        int visible = Math.min(maxVis, searchHitRows.size());
        int mh = hh + visible * rh;
        if (searchHitRows.size() > visible) {
            mh += rh - 2;
        }

        int el = menuEdgeLeft();
        int er = menuEdgeRight();
        int et = menuEdgeTop();
        int eb = menuEdgeBottom();

        int mw = computeSearchMenuWidthPx();

        if (menuY + mh > eb) {
            menuY = eb - mh;
        }
        if (menuY < et) {
            menuY = et;
        }
        if (menuX + mw > er) {
            menuX = er - mw;
        }
        if (menuX < el) {
            menuX = el;
        }
    }

    private void updateMenuFlyoutPath(int mx, int my) {
        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int maxStrip = menuEdgeRight() - menuEdgeLeft();
        int mainW = browsePanelWidth(mainRows, maxStrip);
        int mainListTop = menuY + menuHeaderHeight();
        int mainListH = mainRows.size() * menuRowHeight();
        MenuRect mainList = new MenuRect(menuX, mainListTop, mainW, mainListH);

        java.util.ArrayList<net.minecraft.resources.ResourceLocation> prevBrowsePath =
                new java.util.ArrayList<>(menuFlyoutPath);
        menuFlyoutPath.clear();

        BrowseCategoryRow hoveredRootFromMain = null;
        // List rows only (below header). Sloppy X so edge clicks register after clamp.
        boolean mainGeom =
                mainList.contains(mx, my) || (menuMainColumnHitSloppy(mx, my, 4) && my >= mainListTop);
        if (mainGeom && !browseMouseBlockedByDeeperPanel(mx, my, -1, prevBrowsePath)) {
            int idx = (my - mainListTop) / menuRowHeight();
            if (idx >= 0 && idx < mainRows.size() && mainRows.get(idx) instanceof BrowseCategoryRow bcr
                    && submenuHasContent(bcr.id())) {
                hoveredRootFromMain = bcr;
            }
        }

        net.minecraft.resources.ResourceLocation rootId = null;
        // Main-column opener wins over sticky so switching rows updates the flyout immediately.
        if (hoveredRootFromMain != null) {
            rootId = hoveredRootFromMain.id();
            stickyBrowseRootId = rootId;
        } else if (stickyBrowseRootId != null) {
            if (isRootCategoryWithSubmenu(stickyBrowseRootId, mainRows)) {
                rootId = stickyBrowseRootId;
            } else {
                clearStickyBrowseRoot();
            }
        }

        if (rootId == null) {
            return;
        }

        java.util.ArrayList<net.minecraft.resources.ResourceLocation> path = new java.util.ArrayList<>();
        path.add(rootId);

        /*
         * Pointer on a nested column is not inside the first flyout's union, so we must either extend
         * from a direct hit or jump into the child branch whose flyout already contains the pointer.
         * Root flyouts for other categories are not consulted (no ghost stacks).
         */
        while (path.size() < 24) {
            FlyoutHitPanel panel = flyoutHitPanelForPath(path, mainRows, mainW);
            if (panel == null) {
                break;
            }
            if (panel.unionContains(mx, my)) {
                BrowseRow hit = panel.rowAt(mx, my);
                if (!(hit instanceof BrowseCategoryRow bcr) || !submenuHasContent(bcr.id())) {
                    break;
                }
                path.add(bcr.id());
                continue;
            }
            BrowseCategoryRow jump = null;
            int bestScore = Integer.MIN_VALUE;
            int jumpIdx = -1;
            for (BrowseRow r : panel.rows) {
                if (!(r instanceof BrowseCategoryRow bcr) || !submenuHasContent(bcr.id())) {
                    continue;
                }
                java.util.ArrayList<net.minecraft.resources.ResourceLocation> longer =
                        new java.util.ArrayList<>(path);
                longer.add(bcr.id());
                FlyoutHitPanel deeper = flyoutHitPanelForPath(longer, mainRows, mainW);
                if (deeper == null || !deeper.unionContains(mx, my)) {
                    continue;
                }
                boolean inFly = deeper.flyout().contains(mx, my);
                boolean onParentRow = browsePointerOnCategoryRowInPanel(panel, bcr, mx, my);
                boolean stickyHere =
                        prevBrowsePath.size() > path.size()
                                && prevBrowsePath.get(path.size()).equals(bcr.id());
                /*
                 * Sibling submenus share X alignment; a tall flyout can overlap another sibling’s
                 * hypothetical rect — score so the parent row and sticky branch win, and flyout
                 * interior beats bridge-only.
                 */
                int score = inFly ? 100 : 10;
                if (onParentRow) {
                    score += 1000;
                }
                if (stickyHere) {
                    score += 50;
                }
                int idx = indexOfCategory(panel.rows, bcr.id());
                if (score > bestScore) {
                    bestScore = score;
                    jump = bcr;
                    jumpIdx = idx;
                } else if (score == bestScore && idx > jumpIdx) {
                    jump = bcr;
                    jumpIdx = idx;
                }
            }
            if (jump != null) {
                path.add(jump.id());
                continue;
            }
            break;
        }

        menuFlyoutPath.addAll(path);

        if (!menuBrowseStackBoundsContains(mx, my, mainRows, mainW)) {
            clearStickyBrowseRoot();
            menuFlyoutPath.clear();
        }
    }

    /**
     * True if the pointer is over the main column or the axis-aligned hull of the current flyout stack
     * (covers gaps between columns). Only uses {@link #menuFlyoutPath}; no other root's geometry.
     */
    private boolean menuBrowseStackBoundsContains(
            int mx, int my, java.util.List<BrowseRow> mainRows, int mainW) {
        if (menuMainColumnHitSloppy(mx, my, 6)) {
            return true;
        }
        if (menuFlyoutPath.isEmpty()) {
            return false;
        }
        int minX = menuX + mainW;
        int minY = menuY;
        int maxX = menuX + mainW;
        int maxY = menuY + menuHeaderHeight() + mainRows.size() * menuRowHeight();
        for (int d = 0; d < menuFlyoutPath.size(); d++) {
            java.util.ArrayList<net.minecraft.resources.ResourceLocation> prefix =
                    new java.util.ArrayList<>(menuFlyoutPath.subList(0, d + 1));
            FlyoutGeom g = computeFlyoutGeom(prefix, mainRows, mainW);
            if (g == null) {
                continue;
            }
            MenuRect r = g.flyout();
            minX = Math.min(minX, r.x);
            minY = Math.min(minY, r.y);
            maxX = Math.max(maxX, r.x + r.w);
            maxY = Math.max(maxY, r.y + r.h);
        }
        int pad = 4;
        return mx >= minX - pad && mx < maxX + pad && my >= minY - pad && my < maxY + pad;
    }

    private static boolean bridgeContains(int mx, int my, int bridgeLeft, int rowTop, int rowBottom, int flyLeft) {
        return mx >= bridgeLeft && mx < flyLeft && my >= rowTop && my < rowBottom;
    }

    private record FlyoutGeom(MenuRect flyout, int rowTopInParent, int rowBottomInParent) {}

    private record FlyoutHitPanel(
            MenuRect flyout,
            int flyListTop,
            java.util.List<BrowseRow> rows,
            int rowHeight,
            int parentRowTop,
            int parentRowBottom,
            int bridgeLeft) {
        boolean unionContains(int mx, int my) {
            if (flyout.contains(mx, my)) {
                return true;
            }
            return bridgeContains(mx, my, bridgeLeft, parentRowTop, parentRowBottom, flyout.x);
        }

        BrowseRow rowAt(int mx, int my) {
            if (!flyout.contains(mx, my)) {
                return null;
            }
            int idx = (my - flyListTop) / rowHeight;
            if (idx < 0 || idx >= rows.size()) {
                return null;
            }
            return rows.get(idx);
        }
    }

    private FlyoutHitPanel flyoutHitPanelForPath(
            java.util.List<net.minecraft.resources.ResourceLocation> path,
            java.util.List<BrowseRow> mainRows,
            int mainW) {
        if (path.isEmpty()) {
            return null;
        }
        FlyoutGeom g = computeFlyoutGeom(path, mainRows, mainW);
        if (g == null) {
            return null;
        }
        net.minecraft.resources.ResourceLocation leaf = path.get(path.size() - 1);
        java.util.List<BrowseRow> rows = browseRowsFor(leaf);
        int flyListTop = g.flyout().y;

        int parentListTop;
        int bridgeLeft;
        java.util.List<BrowseRow> parentRows;
        if (path.size() == 1) {
            parentListTop = menuY + menuHeaderHeight();
            bridgeLeft = menuX + mainW;
            parentRows = mainRows;
        } else {
            java.util.ArrayList<net.minecraft.resources.ResourceLocation> parentPath =
                    new java.util.ArrayList<>(path.subList(0, path.size() - 1));
            FlyoutGeom pg = computeFlyoutGeom(parentPath, mainRows, mainW);
            if (pg == null) {
                return null;
            }
            parentListTop = pg.flyout().y;
            bridgeLeft = pg.flyout().x + pg.flyout().w;
            parentRows = browseRowsFor(path.get(path.size() - 2));
        }
        int pIdx = indexOfCategory(parentRows, leaf);
        if (pIdx < 0) {
            return null;
        }
        int rh = menuRowHeight();
        int rowTop = parentListTop + pIdx * rh;
        int rowBottom = rowTop + rh;
        return new FlyoutHitPanel(g.flyout(), flyListTop, rows, rh, rowTop, rowBottom, bridgeLeft);
    }

    private FlyoutGeom computeFlyoutGeom(
            java.util.List<net.minecraft.resources.ResourceLocation> path,
            java.util.List<BrowseRow> mainRows,
            int mainW) {
        if (path.isEmpty()) {
            return null;
        }
        int mainListTop = menuY + menuHeaderHeight();
        int left = menuX + mainW + MENU_GAP;
        java.util.List<BrowseRow> parentRows = mainRows;
        int parentListTop = mainListTop;
        FlyoutGeom last = null;
        for (int i = 0; i < path.size(); i++) {
            net.minecraft.resources.ResourceLocation catId = path.get(i);
            int idx = indexOfCategory(parentRows, catId);
            if (idx < 0) {
                return null;
            }
            int anchorTop = parentListTop + idx * menuRowHeight();
            int anchorBottom = anchorTop + menuRowHeight();
            java.util.List<BrowseRow> content = browseRowsFor(catId);
            int fw = browsePanelWidth(
                    content, Math.max(menuMinColWidth(), menuEdgeRight() - left - MENU_GAP));
            int fh = content.size() * menuRowHeight();
            int flyTop = anchorTop;
            flyTop = Math.max(menuEdgeTop(), Math.min(flyTop, menuEdgeBottom() - fh));
            MenuRect fly = new MenuRect(left, flyTop, fw, fh);
            last = new FlyoutGeom(fly, anchorTop, anchorBottom);
            if (i < path.size() - 1) {
                left = left + fw + MENU_GAP;
                parentRows = content;
                parentListTop = flyTop;
            }
        }
        return last;
    }

    /**
     * Flyouts drawn later in the stack sit visually on top; pointer over a deeper panel must not activate
     * rows in shallower columns (otherwise hidden rows “light up” on hover).
     *
     * @param panelDepth {@code -1} for the root browse column, {@code 0} for the first flyout, etc.
     * @param activeStack path stack to test (usually {@link #menuFlyoutPath}; while rebuilding the flyout
     *     path, pass the previous frame’s path).
     */
    private boolean browseMouseBlockedByDeeperPanel(
            int mx,
            int my,
            int panelDepth,
            java.util.List<net.minecraft.resources.ResourceLocation> activeStack) {
        if (activeStack.isEmpty()) {
            return false;
        }
        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int maxStrip = menuEdgeRight() - menuEdgeLeft();
        int mainW = browsePanelWidth(mainRows, maxStrip);
        for (int d = panelDepth + 1; d < activeStack.size(); d++) {
            java.util.ArrayList<net.minecraft.resources.ResourceLocation> prefix =
                    new java.util.ArrayList<>(activeStack.subList(0, d + 1));
            FlyoutGeom g = computeFlyoutGeom(prefix, mainRows, mainW);
            if (g != null && g.flyout().contains(mx, my)) {
                return true;
            }
        }
        return false;
    }

    private boolean browseMouseBlockedByDeeperPanel(int mx, int my, int panelDepth) {
        return browseMouseBlockedByDeeperPanel(mx, my, panelDepth, menuFlyoutPath);
    }

    /** True if the pointer is on {@code bcr}’s row inside {@code panel}’s list (not a sibling’s). */
    private boolean browsePointerOnCategoryRowInPanel(
            FlyoutHitPanel panel, BrowseCategoryRow bcr, int mx, int my) {
        int ri = indexOfCategory(panel.rows(), bcr.id());
        if (ri < 0) {
            return false;
        }
        int y0 = panel.flyListTop() + ri * panel.rowHeight();
        int fx = panel.flyout().x;
        int fw = panel.flyout().w;
        return mx >= fx && mx < fx + fw && my >= y0 && my < y0 + panel.rowHeight();
    }

    private int indexOfCategory(java.util.List<BrowseRow> rows, net.minecraft.resources.ResourceLocation id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof BrowseCategoryRow bcr && bcr.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void renderSearchMenu(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 5000);

        if (!searchQuery.trim().isEmpty()) {
            renderSearchFlatMenu(graphics);
            graphics.pose().popPose();
            return;
        }

        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int maxStrip = menuEdgeRight() - menuEdgeLeft();
        int mw = browsePanelWidth(mainRows, maxStrip);
        int mainBodyH = mainRows.size() * menuRowHeight();
        int mh = menuHeaderHeight() + mainBodyH;

        drawMenuPanel(graphics, menuX, menuY, mw, mh);
        graphics.drawString(font, "Add node", menuX + 4, menuY + 4, 0xFF669966, false);
        graphics.drawString(
                font,
                "> " + searchQuery + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : " "),
                menuX + 4,
                menuY + 4 + menuRowHeight(),
                0xFF00FF88,
                false);
        drawBrowseRows(
                graphics,
                mainRows,
                menuX,
                menuY + menuHeaderHeight(),
                mw,
                mouseX,
                mouseY,
                true,
                -1);

        for (int depth = 0; depth < menuFlyoutPath.size(); depth++) {
            java.util.List<net.minecraft.resources.ResourceLocation> prefix =
                    new java.util.ArrayList<>(menuFlyoutPath.subList(0, depth + 1));
            FlyoutGeom g = computeFlyoutGeom(prefix, mainRows, mw);
            if (g == null) {
                continue;
            }
            net.minecraft.resources.ResourceLocation cat = prefix.get(prefix.size() - 1);
            java.util.List<BrowseRow> rows = browseRowsFor(cat);
            drawMenuPanel(graphics, g.flyout().x, g.flyout().y, g.flyout().w, g.flyout().h);
            drawBrowseRows(
                    graphics,
                    rows,
                    g.flyout().x,
                    g.flyout().y,
                    g.flyout().w,
                    mouseX,
                    mouseY,
                    true,
                    depth);
        }

        graphics.pose().popPose();
    }

    private void renderSearchFlatMenu(GuiGraphics graphics) {
        int mw = computeSearchMenuWidthPx();
        int visible = Math.min(menuMaxSearchVisibleRows(), searchHitRows.size());
        int mh = menuHeaderHeight() + visible * menuRowHeight();
        if (searchHitRows.size() > visible) {
            mh += menuRowHeight() - 2;
        }

        drawMenuPanel(graphics, menuX, menuY, mw, mh);
        graphics.drawString(font, "Filter (all categories)", menuX + 4, menuY + 4, 0xFF669966, false);
        graphics.drawString(
                font,
                "> " + searchQuery + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : " "),
                menuX + 4,
                menuY + 4 + menuRowHeight(),
                0xFF00FF88,
                false);
        for (int i = 0; i < visible; i++) {
            BrowseNodeRow row = searchHitRows.get(i);
            int ry = menuY + menuHeaderHeight() + i * menuRowHeight();
            boolean hovered = mouseY >= ry && mouseY < ry + menuRowHeight() && mouseX >= menuX && mouseX <= menuX + mw;
            int color = (i == 0 || hovered) ? 0xFFFFFFFF : 0xFF888888;
            if (i == 0 || hovered) {
                graphics.fill(menuX + 1, ry, menuX + mw - 1, ry + menuRowHeight(), 0x4400FF88);
            }
            boolean locked = isEditorPeripheralLocked(row.nodeType());
            int rowColor = locked ? (hovered ? 0xFFCC8888 : 0xFF886666) : color;
            graphics.drawString(font, row.label(), menuX + 6, ry + 1, rowColor, false);
        }
        if (searchHitRows.size() > visible) {
            graphics.drawString(
                    font,
                    "(" + searchHitRows.size() + " total — type to narrow)",
                    menuX + 4,
                    menuY + menuHeaderHeight() + visible * menuRowHeight() + 2,
                    0xFF666666,
                    false);
        }
    }

    private static void drawMenuPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
        graphics.fill(x, y, x + w, y + h, 0xEE1A1A1A);
        graphics.renderOutline(x, y, w, h, 0xFF00FF88);
    }

    private void drawBrowseRows(
            GuiGraphics graphics,
            java.util.List<BrowseRow> rows,
            int rx,
            int ry,
            int rw,
            int mx,
            int my,
            boolean showSubArrow,
            int occlusionPanelDepth) {
        int rh = menuRowHeight();
        for (int i = 0; i < rows.size(); i++) {
            BrowseRow row = rows.get(i);
            int y0 = ry + i * rh;
            boolean hovered = my >= y0 && my < y0 + rh && mx >= rx && mx < rx + rw;
            if (hovered && browseMouseBlockedByDeeperPanel(mx, my, occlusionPanelDepth)) {
                hovered = false;
            }
            int color = hovered ? 0xFFFFFFFF : 0xFF888888;
            if (hovered) {
                graphics.fill(rx + 1, y0, rx + rw - 1, y0 + rh, 0x4400FF88);
            }
            graphics.enableScissor(rx + 1, y0, rx + rw - 1, y0 + rh);
            if (row instanceof BrowseCategoryRow c) {
                boolean sub = submenuHasContent(c.id());
                Component text =
                        sub && showSubArrow ? Component.empty().append(c.label()).append(" ›") : c.label();
                graphics.drawString(font, text, rx + 6, y0 + 1, color, false);
            } else if (row instanceof BrowseNodeRow n) {
                boolean locked = isEditorPeripheralLocked(n.nodeType());
                int rowColor = locked ? (hovered ? 0xFFCC8888 : 0xFF886666) : color;
                graphics.drawString(font, n.label(), rx + 6, y0 + 1, rowColor, false);
            }
            graphics.disableScissor();
        }
    }

    private boolean menuBrowseContainsMouse(int mx, int my) {
        if (!searchQuery.trim().isEmpty()) {
            return false;
        }
        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int mw = browsePanelWidth(mainRows, menuEdgeRight() - menuEdgeLeft());
        return menuBrowseStackBoundsContains(mx, my, mainRows, mw);
    }

    private boolean menuSearchFlatContainsMouse(int mx, int my) {
        if (searchQuery.trim().isEmpty()) {
            return false;
        }
        int mw = computeSearchMenuWidthPx();
        int visible = Math.min(menuMaxSearchVisibleRows(), searchHitRows.size());
        int mh = menuHeaderHeight() + visible * menuRowHeight();
        if (searchHitRows.size() > visible) {
            mh += menuRowHeight() - 2;
        }
        return new MenuRect(menuX, menuY, mw, mh).contains(mx, my);
    }

    private net.minecraft.resources.ResourceLocation hitBrowseNodeTypeAt(int mx, int my) {
        if (!searchQuery.trim().isEmpty()) {
            int mw = computeSearchMenuWidthPx();
            int visible = Math.min(menuMaxSearchVisibleRows(), searchHitRows.size());
            int listTop = menuY + menuHeaderHeight();
            if (mx >= menuX && mx <= menuX + mw && my >= listTop && my < listTop + visible * menuRowHeight()) {
                int idx = (my - listTop) / menuRowHeight();
                if (idx >= 0 && idx < searchHitRows.size()) {
                    return searchHitRows.get(idx).nodeType();
                }
            }
            return null;
        }

        java.util.List<BrowseRow> mainRows = browseRowsFor(NodeMenuRegistry.ROOT);
        int mw = browsePanelWidth(mainRows, menuEdgeRight() - menuEdgeLeft());
        int mainListTop = menuY + menuHeaderHeight();
        if (mx >= menuX && mx < menuX + mw && my >= mainListTop && my < mainListTop + mainRows.size() * menuRowHeight()) {
            if (!browseMouseBlockedByDeeperPanel(mx, my, -1)) {
                int idx = (my - mainListTop) / menuRowHeight();
                if (idx >= 0 && idx < mainRows.size() && mainRows.get(idx) instanceof BrowseNodeRow n) {
                    return n.nodeType();
                }
            }
        }

        for (int d = menuFlyoutPath.size() - 1; d >= 0; d--) {
            java.util.ArrayList<net.minecraft.resources.ResourceLocation> prefix =
                    new java.util.ArrayList<>(menuFlyoutPath.subList(0, d + 1));
            FlyoutHitPanel panel = flyoutHitPanelForPath(prefix, mainRows, mw);
            if (panel == null) {
                continue;
            }
            if (browseMouseBlockedByDeeperPanel(mx, my, d)) {
                continue;
            }
            BrowseRow r = panel.rowAt(mx, my);
            if (r instanceof BrowseNodeRow n) {
                return n.nodeType();
            }
        }
        return null;
    }

    private void drawGrid(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(panX, panY, 0);
        float gScale = editorContentScale();
        int gridSize = Math.max(2, Math.round(GRID_SPACING_SCREEN_PX / gScale));
        int lineW = Math.max(1, Math.round(GRID_LINE_WIDTH_SCREEN_PX / gScale));
        lineW = Math.min(lineW, Math.max(1, gridSize - 1));
        int margin = gridSize + 4;
        int startX = (int)(-panX - (width / 2f) / gScale - margin);
        int startY = (int)(-panY - (height / 2f) / gScale - margin);
        int endX = (int)(-panX + (width / 2f) / gScale + width + margin);
        int endY = (int)(-panY + (height / 2f) / gScale + height + margin);
        startX = (startX / gridSize) * gridSize;
        startY = (startY / gridSize) * gridSize;
        for (int i = startX; i < endX; i += gridSize) {
            graphics.fill(i, startY, i + lineW, endY, 0x12FFFFFF);
        }
        for (int i = startY; i < endY; i += gridSize) {
            graphics.fill(startX, i, endX, i + lineW, 0x12FFFFFF);
        }
        graphics.pose().popPose();
    }

    private void clearWireHover() {
        wireHoverKind = WireHoverKind.NONE;
        wireHoverConnIdx = -1;
    }

    /** Invalidate the {@link #updateWireInteractionHover} cache; call when the panel is left so re-entry recomputes. */
    private void invalidateHoverCache() {
        hoverCacheGx = Integer.MIN_VALUE;
        hoverCacheGy = Integer.MIN_VALUE;
    }

    private static int wireArgbForConnection(WConnection c) {
        int h = Objects.hash(c.sourceNode(), c.sourcePin(), c.targetNode(), c.targetPin());
        return WIRE_ARGB_PALETTE[Math.floorMod(h, WIRE_ARGB_PALETTE.length)];
    }

    private static void fillWireChainEndpoints(WConnection conn, WNode src, WNode tgt, int[] xs, int[] ys) {
        int i = 0;
        xs[i] = src.getX() + src.getWidth();
        ys[i] = src.getY() + 18 + conn.sourcePin() * 12;
        for (int w = 0; w < conn.waypointXs().length; w++) {
            i++;
            xs[i] = conn.waypointXs()[w];
            ys[i] = conn.waypointYs()[w];
        }
        i++;
        xs[i] = tgt.getX();
        ys[i] = tgt.getY() + 18 + conn.targetPin() * 12;
    }

    private static int wireChainLen(WConnection c) {
        return 2 + c.waypointXs().length;
    }

    private static float distSq(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static boolean waypointInsertClearOfOthers(
            int ix, int iy, int[] xs, int[] ys, int seg, float minD) {
        float m2 = minD * minD;
        int a = seg;
        int b = seg + 1;
        if (distSq(ix, iy, xs[a], ys[a]) < m2 || distSq(ix, iy, xs[b], ys[b]) < m2) {
            return false;
        }
        for (int k = 0; k < xs.length; k++) {
            if (k == a || k == b) {
                continue;
            }
            if (distSq(ix, iy, xs[k], ys[k]) < m2) {
                return false;
            }
        }
        return true;
    }

    private int wirePickGhostRadiusGraph() {
        float scale = editorContentScale();
        return Math.max(5, Mth.ceil(9f / scale));
    }

    /** Throttle state for {@link #updateWireInteractionHover}: when mouse and topology are stable, skip the 24-sample-per-segment scan. */
    private int hoverCacheGx = Integer.MIN_VALUE;
    private int hoverCacheGy = Integer.MIN_VALUE;
    private int hoverCacheConnCount = -1;
    private int hoverCacheNodeCount = -1;
    private float hoverCacheScale = Float.NaN;

    private void updateWireInteractionHover(int gx, int gy) {
        int connCount = graph.getConnections().size();
        int nodeCount = graph.getNodes().size();
        float scaleNow = editorContentScale();
        if (gx == hoverCacheGx
                && gy == hoverCacheGy
                && connCount == hoverCacheConnCount
                && nodeCount == hoverCacheNodeCount
                && scaleNow == hoverCacheScale) {
            return;
        }
        hoverCacheGx = gx;
        hoverCacheGy = gy;
        hoverCacheConnCount = connCount;
        hoverCacheNodeCount = nodeCount;
        hoverCacheScale = scaleNow;
        clearWireHover();
        if (graphPointBlocksWireInteraction(gx, gy)) {
            return;
        }
        float scale = editorContentScale();
        int waypointPickR = Math.max(6, Mth.ceil(10f / scale));
        int curvePickR = Math.max(5, Mth.ceil(8f / scale));
        int insertMinSep = Math.max(12, Mth.ceil(14f / scale));
        float wpR2 = waypointPickR * (float) waypointPickR;
        float cvR2 = curvePickR * (float) curvePickR;
        int bestWpConn = -1;
        int bestWpIdx = -1;
        float bestWpD2 = wpR2;
        List<WConnection> conns = graph.getConnections();
        for (int ci = 0; ci < conns.size(); ci++) {
            WConnection c = conns.get(ci);
            for (int wi = 0; wi < c.waypointXs().length; wi++) {
                float d2 = distSq(gx, gy, c.waypointXs()[wi], c.waypointYs()[wi]);
                if (d2 <= bestWpD2) {
                    bestWpD2 = d2;
                    bestWpConn = ci;
                    bestWpIdx = wi;
                }
            }
        }
        if (bestWpConn >= 0) {
            wireHoverKind = WireHoverKind.WAYPOINT;
            wireHoverConnIdx = bestWpConn;
            wireHoverWaypointIdx = bestWpIdx;
            return;
        }
        int bestCi = -1;
        int bestSeg = 0;
        int bestIx = 0;
        int bestIy = 0;
        float bestD2 = cvR2;
        boolean insertOk = false;
        for (int ci = 0; ci < conns.size(); ci++) {
            WConnection c = conns.get(ci);
            WNode s = findNode(c.sourceNode());
            WNode t = findNode(c.targetNode());
            if (s == null || t == null) {
                continue;
            }
            int n = wireChainLen(c);
            int[] xs = new int[n];
            int[] ys = new int[n];
            fillWireChainEndpoints(c, s, t, xs, ys);
            for (int seg = 0; seg < n - 1; seg++) {
                int ax = xs[seg];
                int ay = ys[seg];
                int bx = xs[seg + 1];
                int by = ys[seg + 1];
                for (int step = 0; step <= 24; step++) {
                    float tf = step / 24f;
                    float px = wireCurveX(ax, bx, tf);
                    float py = wireCurveY(ay, by, tf);
                    float dx = gx - px;
                    float dy = gy - py;
                    float d2 = dx * dx + dy * dy;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        bestCi = ci;
                        bestSeg = seg;
                        bestIx = Math.round(px);
                        bestIy = Math.round(py);
                        insertOk =
                                waypointInsertClearOfOthers(bestIx, bestIy, xs, ys, seg, (float) insertMinSep);
                    }
                }
            }
        }
        if (bestCi < 0) {
            return;
        }
        wireHoverConnIdx = bestCi;
        if (insertOk) {
            wireHoverKind = WireHoverKind.INSERT_GHOST;
            wireHoverInsertSeg = bestSeg;
            wireHoverInsertGx = bestIx;
            wireHoverInsertGy = bestIy;
        } else {
            wireHoverKind = WireHoverKind.CURVE_ONLY;
        }
    }

    private boolean graphPointBlocksWireInteraction(int gx, int gy) {
        for (WNode n : graph.getNodes()) {
            if (gx >= n.getX()
                    && gx < n.getX() + n.getWidth()
                    && gy >= n.getY()
                    && gy < n.getY() + n.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private void insertWaypointOnConnection(int connIdx, int seg, int ix, int iy) {
        WConnection c = graph.getConnections().get(connIdx);
        int oldN = c.waypointXs().length;
        int[] nxs = new int[oldN + 1];
        int[] nys = new int[oldN + 1];
        for (int i = 0; i < seg; i++) {
            nxs[i] = c.waypointXs()[i];
            nys[i] = c.waypointYs()[i];
        }
        nxs[seg] = ix;
        nys[seg] = iy;
        for (int i = seg; i < oldN; i++) {
            nxs[i + 1] = c.waypointXs()[i];
            nys[i + 1] = c.waypointYs()[i];
        }
        graph.getConnections()
                .set(
                        connIdx,
                        new WConnection(
                                c.sourceNode(), c.sourcePin(), c.targetNode(), c.targetPin(), nxs, nys));
    }

    private void removeWaypointFromConnection(int connIdx, int wpIdx) {
        WConnection c = graph.getConnections().get(connIdx);
        int n = c.waypointXs().length;
        if (wpIdx < 0 || wpIdx >= n || n <= 0) {
            return;
        }
        if (n == 1) {
            graph.getConnections()
                    .set(
                            connIdx,
                            WConnection.withoutWaypoints(
                                    c.sourceNode(), c.sourcePin(), c.targetNode(), c.targetPin()));
            return;
        }
        int[] nxs = new int[n - 1];
        int[] nys = new int[n - 1];
        for (int i = 0, j = 0; i < n; i++) {
            if (i == wpIdx) {
                continue;
            }
            nxs[j] = c.waypointXs()[i];
            nys[j] = c.waypointYs()[i];
            j++;
        }
        graph.getConnections()
                .set(
                        connIdx,
                        new WConnection(
                                c.sourceNode(), c.sourcePin(), c.targetNode(), c.targetPin(), nxs, nys));
    }

    private void drawConnection(GuiGraphics graphics, WConnection conn, int connIdx) {
        WNode src = findNode(conn.sourceNode());
        WNode tgt = findNode(conn.targetNode());
        if (src == null || tgt == null) {
            return;
        }
        int n = wireChainLen(conn);
        if (wireChainXs.length < n) {
            wireChainXs = new int[n];
            wireChainYs = new int[n];
        }
        int[] xs = wireChainXs;
        int[] ys = wireChainYs;
        fillWireChainEndpoints(conn, src, tgt, xs, ys);
        int color = wireArgbForConnection(conn);
        for (int seg = 0; seg < n - 1; seg++) {
            drawSmoothCurve(graphics, xs[seg], ys[seg], xs[seg + 1], ys[seg + 1], color, 1.5f);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        drawConnectionPulseTravelChain(graphics, xs, ys, n, src.getTopoDepth(), color);
        graphics.pose().popPose();
        int rgb = color & 0xFFFFFF;
        int wh = Math.max(3, Mth.ceil(4f / editorContentScale()));
        for (int w = 0; w < conn.waypointXs().length; w++) {
            int wx = conn.waypointXs()[w];
            int wy = conn.waypointYs()[w];
            boolean hot =
                    wireHoverKind == WireHoverKind.WAYPOINT
                            && connIdx == wireHoverConnIdx
                            && w == wireHoverWaypointIdx;
            int ring = hot ? 0xFFFFFFFF : (0xCC000000 | rgb);
            int core = 0xFF000000 | rgb;
            int half = wh / 2;
            graphics.fill(wx - wh - 1, wy - wh - 1, wx + wh + 2, wy + wh + 2, ring);
            graphics.fill(wx - half, wy - half, wx + half + 1, wy + half + 1, core);
        }
        if (connIdx == wireHoverConnIdx && wireHoverKind == WireHoverKind.INSERT_GHOST) {
            int gr = Math.max(3, Mth.ceil(5f / editorContentScale()));
            int gx = wireHoverInsertGx;
            int gy = wireHoverInsertGy;
            graphics.fill(gx - gr - 2, gy - gr - 2, gx + gr + 3, gy + gr + 3, WIRE_INSERT_GHOST_ARGB);
            graphics.fill(gx - gr, gy - gr, gx + gr + 1, gy + gr + 1, 0xFF222244);
        }
    }

    /** Loops per second along each wire (phase 0–1); independent of sim pulses so motion stays smooth. */
    private static final float PULSE_SCROLL_SPEED = 0.11f;
    private static final float PULSE_STAGGER_PER_DEPTH = 0.034f;
    /** Fewer sprites than before; motion is interpolated along the curve each frame. */
    private static final int PULSE_DOT_COUNT = 2;
    private static final float PULSE_DOT_SPACING = 0.11f;

    private void drawConnectionPulseTravelChain(
            GuiGraphics graphics, int[] xs, int[] ys, int pointCount, int topoDepth, int wireArgb) {
        int segCount = pointCount - 1;
        if (segCount <= 0) {
            return;
        }
        int rgb = wireArgb & 0xFFFFFF;
        float base = wirePulseScroll - Mth.floor(wirePulseScroll);
        float phase = base - topoDepth * PULSE_STAGGER_PER_DEPTH;
        phase -= Mth.floor(phase);
        for (int k = 0; k < PULSE_DOT_COUNT; k++) {
            float u = phase - k * PULSE_DOT_SPACING;
            u = u - Mth.floor(u);
            if (u < 0) {
                u += 1f;
            }
            float smooth = u * u * (3.0f - 2.0f * u);
            float g = smooth * segCount;
            int seg = Math.min(segCount - 1, (int) g);
            float localT = g - seg;
            localT = Mth.clamp(localT, 0f, 1f);
            float px = wireCurveX(xs[seg], xs[seg + 1], localT);
            float py = wireCurveY(ys[seg], ys[seg + 1], localT);
            int alpha = 235 - k * 70;
            int core = (alpha << 24) | rgb;
            int glow = ((alpha / 4) << 24) | rgb;
            graphics.fill((int) px - 3, (int) py - 3, (int) px + 4, (int) py + 4, glow);
            graphics.fill((int) px - 1, (int) py - 1, (int) px + 2, (int) py + 2, core);
        }
    }

    /** Matches {@link #drawSmoothCurve}: P1 = (midX, y1), P2 = (midX, y2). */
    private static float wireCurveX(int x1, int x2, float t) {
        float mid = x1 + (x2 - x1) * 0.5f;
        float mt = 1.0f - t;
        return mt * mt * mt * x1 + 3 * mt * mt * t * mid + 3 * mt * t * t * mid + t * t * t * x2;
    }

    private static float wireCurveY(int y1, int y2, float t) {
        float mt = 1.0f - t;
        return mt * mt * mt * y1 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y2;
    }

    private void drawSmoothCurve(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, float thickness) {
        int steps = 24; int lastX = x1; int lastY = y1;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float cx1 = x1 + (x2 - x1) * 0.5f; float cy1 = y1;
            float cx2 = x1 + (x2 - x1) * 0.5f; float cy2 = y2;
            float mt = 1.0f - t;
            float x = mt * mt * mt * x1 + 3 * mt * mt * t * cx1 + 3 * mt * t * t * cx2 + t * t * t * x2;
            float y = mt * mt * mt * y1 + 3 * mt * mt * t * cy1 + 3 * mt * t * t * cy2 + t * t * t * y2;
            drawLine(graphics, lastX, lastY, (int)x, (int)y, color, thickness);
            lastX = (int)x; lastY = (int)y;
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, float thickness) {
        int dx = x2 - x1; int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy)); if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int px = x1 + (int)(dx * t); int py = y1 + (int)(dy * t);
            graphics.fill(px, py, px + (int)Math.max(1, thickness), py + (int)Math.max(1, thickness), color);
        }
    }

    private void renderParticles(GuiGraphics graphics, float deltaTime) {
        for (int i = editorParticles.size() - 1; i >= 0; i--) {
            NodeParticle p = editorParticles.get(i);
            p.x += p.vx * deltaTime * 60.0; p.y += p.vy * deltaTime * 60.0; p.life -= deltaTime * 60.0;
            if (p.life <= 0) { editorParticles.remove(i); continue; }
            float alpha = (float) p.life / p.maxLife;
            int rColor = (p.color & 0xFFFFFF) | ((int)(alpha * 255) << 24);
            graphics.fill((int)p.x, (int)p.y, (int)p.x + 2, (int)p.y + 2, rColor);
        }
    }

    private WNode findNode(UUID id) {
        return graph.getNode(id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (exportDialogOpen) {
            if (button != 0) {
                return true;
            }
            int boxW = Math.min(540, width - 40);
            int boxH = Math.min(220, height - 40);
            int bx = width / 2 - boxW / 2;
            int by = height / 2 - boxH / 2;
            int btnW = 88;
            int btnH = 20;
            int copyX = bx + boxW - btnW * 2 - 16;
            int closeX = bx + boxW - btnW - 8;
            int btnY = by + boxH - 30;
            if (mouseX >= copyX && mouseX < copyX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                minecraft.keyboardHandler.setClipboard(exportDialogText);
                postShareStatus(false, "gui.computed.share.export_success", exportDialogText.length());
                playUiClick(1.03f);
                return true;
            }
            if (mouseX >= closeX && mouseX < closeX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                closeExportDialog();
                playUiClick(0.94f);
                return true;
            }
            closeExportDialog();
            playUiClick(0.94f);
            return true;
        }
        if (importDialogOpen) {
            if (button != 0) {
                return true;
            }
            int boxW = Math.min(540, width - 40);
            int boxH = Math.min(220, height - 40);
            int bx = width / 2 - boxW / 2;
            int by = height / 2 - boxH / 2;
            int tx1 = bx + 8;
            int ty1 = by + 24;
            int tx2 = bx + boxW - 8;
            int ty2 = by + boxH - 44;
            int btnW = 88;
            int btnH = 20;
            int importX = bx + boxW - btnW * 2 - 16;
            int cancelX = bx + boxW - btnW - 8;
            int btnY = by + boxH - 30;
            if (mouseX >= importX && mouseX < importX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                importGraphFromShareString(importDialogText);
                return true;
            }
            if (mouseX >= cancelX && mouseX < cancelX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                closeImportFromStringDialog();
                playUiClick(0.94f);
                return true;
            }
            if (mouseX >= tx1 && mouseX < tx2 && mouseY >= ty1 && mouseY < ty2) {
                return true;
            }
            closeImportFromStringDialog();
            playUiClick(0.94f);
            return true;
        }
        if (newFunctionNamingOpen) {
            return true;
        }
        if (itemPickerOpen) {
            return handleItemPickerClick(mouseX, mouseY, button);
        }
        if (handleNestedDiskToolbarClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && centerViewBtnContains(mouseX, mouseY)) {
            requestCameraCenterOnNodes();
            return true;
        }
        if (button == 0 && shareExportBtnContains(mouseX, mouseY)) {
            openExportDialog();
            return true;
        }
        if (button == 0 && shareImportBtnContains(mouseX, mouseY)) {
            openImportFromStringDialog();
            playUiClick(1.01f);
            return true;
        }
        if (functionStore != null && button == 0 && schematicBtnContains(mouseX, mouseY)) {
            if (functionPickerOpen) {
                commitLibraryFunctionRename();
                functionPickerOpen = false;
                functionImportSubmenuOpen = false;
            } else {
                refreshFunctionDiscFileList();
                functionLibraryListScroll = 0;
                functionDiscImportListScroll = 0;
                functionImportSubmenuOpen = false;
                functionPickerOpen = true;
            }
            playUiClick(1.02f);
            return true;
        }
        if (functionStore != null && functionPickerOpen && button == 0) {
            if (functionImportSubmenuOpen && functionImportFlyoutContains(mouseX, mouseY)) {
                handleFunctionImportFlyoutClick(mouseX, mouseY);
                return true;
            }
            if (functionPickerPanelContains(mouseX, mouseY)) {
                handleFunctionPickerClick(mouseX, mouseY);
                return true;
            }
            commitLibraryFunctionRename();
            functionPickerOpen = false;
            functionImportSubmenuOpen = false;
            playUiClick(0.98f);
        }
        if (button == 0 && sectionsToggleContains(mouseX, mouseY)) {
            showSectionsSidebar = !showSectionsSidebar;
            playUiClick(showSectionsSidebar ? 1.01f : 0.94f);
            return true;
        }
        if (button == 0 && sectionsSidebarContains(mouseX, mouseY)) {
            int y = sectionsSidebarY() + 18;
            int row = ((int) mouseY - y) / 13;
            List<WGraph.WSection> sidebarSecs = sectionsSortedByLayer(graph.getSections());
            if (row >= 0 && row < sidebarSecs.size()) {
                UUID id = sidebarSecs.get(row).getId();
                selectedSectionId = id;
                long now = net.minecraft.Util.getMillis();
                if (id.equals(lastSidebarSectionClickId) && now - lastSidebarSectionClickAtMs <= SECTION_DOUBLE_CLICK_MS) {
                    for (WGraph.WSection s : graph.getSections()) {
                        if (s.getId().equals(id)) {
                            startSectionRename(id, s.getName());
                            break;
                        }
                    }
                }
                lastSidebarSectionClickId = id;
                lastSidebarSectionClickAtMs = now;
                playUiClick(1.0f);
                return true;
            }
        }
        if (!isSearching && !isInsideEditorPanel(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (!isSearching && isInsideEditorPanel(mouseX, mouseY) && tryHandleNodeDockClick(mouseX, mouseY, button)) {
            return true;
        }
        if (sectionColorPickerSectionId != null) {
            return handleSectionColorPickerMouseClick(mouseX, mouseY, button);
        }
        int nx = screenToGraphX(mouseX);
        int ny = screenToGraphY(mouseY);
        if (isCreatingSection) {
            if (button == 0) {
                sectionCreateEndX = nx;
                sectionCreateEndY = ny;
                return true;
            }
            if (button == 1 || button == 2) {
                isCreatingSection = false;
                return true;
            }
        }
        if (isSearching) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            rebuildSearchHitRows();
            if (searchQuery.trim().isEmpty()) {
                layoutBrowseMenuForPointer(mx, my);
            } else {
                menuFlyoutPath.clear();
                stickyBrowseRootId = null;
                clampSearchMenuOnScreen();
            }
            net.minecraft.resources.ResourceLocation pick = hitBrowseNodeTypeAt(mx, my);
            if (pick != null) {
                if (isEditorPeripheralLocked(pick)) {
                    playUiClick(0.82f);
                    return true;
                }
                WNode placed = addNodeAtReturning(pick, menuAnchorNx, menuAnchorNy);
                tryAutoConnectPendingOutput(placed);
                isSearching = false;
                clearStickyBrowseRoot();
                return true;
            }
            if (menuBrowseContainsMouse(mx, my) || menuSearchFlatContainsMouse(mx, my)) {
                return true;
            }
            isSearching = false;
            clearStickyBrowseRoot();
            clearPendingWireSpawn();
            return true;
        }
        if (button == 0) {
            WGraph.WSection selectedSec = null;
            if (selectedSectionId != null) {
                for (WGraph.WSection s : graph.getSections()) {
                    if (s.getId().equals(selectedSectionId)) {
                        selectedSec = s;
                        break;
                    }
                }
            }
            if (selectedSec != null) {
                SectionResizeHandle rh = hitSectionResizeHandle(selectedSec, nx, ny);
                if (rh != SectionResizeHandle.NONE) {
                    resizingSection = selectedSec;
                    sectionResizeHandle = rh;
                    sectionResizeStartX = selectedSec.getX();
                    sectionResizeStartY = selectedSec.getY();
                    sectionResizeStartW = selectedSec.getWidth();
                    sectionResizeStartH = selectedSec.getHeight();
                    sectionResizeGrabNx = nx;
                    sectionResizeGrabNy = ny;
                    recordCheckpointBeforeEdit();
                    playUiClick(0.98f);
                    return true;
                }
            }
            WGraph.WSection sec = findSectionAt(nx, ny);
            if (sec != null) {
                long now = net.minecraft.Util.getMillis();
                if (sec.getId().equals(lastSectionHeaderClickId)
                        && now - lastSectionHeaderClickAtMs <= SECTION_DOUBLE_CLICK_MS) {
                    selectedSectionId = sec.getId();
                    startSectionRename(sec.getId(), sec.getName());
                    playUiClick(1.02f);
                    return true;
                }
                lastSectionHeaderClickId = sec.getId();
                lastSectionHeaderClickAtMs = now;
                draggingSection = sec;
                sectionDragOffsetX = nx - sec.getX();
                sectionDragOffsetY = ny - sec.getY();
                sectionDragStartSectionX = sec.getX();
                sectionDragStartSectionY = sec.getY();
                selectedSectionId = sec.getId();
                sectionDragMemberNodes.clear();
                sectionDragOriginalNodePos.clear();
                for (WNode n : graph.getNodes()) {
                    int cx = n.getX() + n.getWidth() / 2;
                    int cy = n.getY() + n.getHeight() / 2;
                    if (cx >= sec.getX() && cx <= sec.getX() + sec.getWidth()
                            && cy >= sec.getY() && cy <= sec.getY() + sec.getHeight()) {
                        sectionDragMemberNodes.add(n.getId());
                        sectionDragOriginalNodePos.put(n.getId(), new int[] {n.getX(), n.getY()});
                    }
                }
                sectionDragChildSections.clear();
                sectionDragOriginalNestedSectionPos.clear();
                for (WGraph.WSection nested : graph.getSections()) {
                    if (nested.getId().equals(sec.getId())) {
                        continue;
                    }
                    if (sectionFullyContainedIn(nested, sec)) {
                        sectionDragChildSections.add(nested);
                        sectionDragOriginalNestedSectionPos.put(
                                nested.getId(), new int[] {nested.getX(), nested.getY()});
                    }
                }
                sectionDragPrevTotalDx = 0;
                sectionDragPrevTotalDy = 0;
                recordCheckpointBeforeEdit();
                return true;
            }
        }
        if (button == 0 && linkingNode == null && !isCreatingSection && isInsideEditorPanel(mouseX, mouseY)) {
            updateWireInteractionHover(nx, ny);
            if (Screen.hasAltDown()) {
                if (wireHoverKind == WireHoverKind.WAYPOINT) {
                    recordCheckpointBeforeEdit();
                    removeWaypointFromConnection(wireHoverConnIdx, wireHoverWaypointIdx);
                    clearWireHover();
                    playUiClick(0.78f);
                    return true;
                }
                if (wireHoverKind == WireHoverKind.INSERT_GHOST
                        || wireHoverKind == WireHoverKind.CURVE_ONLY) {
                    recordCheckpointBeforeEdit();
                    graph.getConnections().remove(wireHoverConnIdx);
                    graph.updateTopology();
                    clearWireHover();
                    playUiClick(0.76f);
                    return true;
                }
            } else {
                if (wireHoverKind == WireHoverKind.INSERT_GHOST) {
                    int gr = wirePickGhostRadiusGraph();
                    int ddx = nx - wireHoverInsertGx;
                    int ddy = ny - wireHoverInsertGy;
                    if (ddx * ddx + ddy * ddy <= gr * gr) {
                        recordCheckpointBeforeEdit();
                        insertWaypointOnConnection(
                                wireHoverConnIdx, wireHoverInsertSeg, wireHoverInsertGx, wireHoverInsertGy);
                        playUiClick(1.04f);
                        return true;
                    }
                }
                if (wireHoverKind == WireHoverKind.WAYPOINT) {
                    recordCheckpointBeforeEdit();
                    draggingWireConnIdx = wireHoverConnIdx;
                    draggingWireWaypointIdx = wireHoverWaypointIdx;
                    return true;
                }
            }
        }
        if (button == 1) {
            boolean hitAnything = false;
            for (WNode node : graph.getNodes()) {
                if (node.getPinAt(nx - node.getX(), ny - node.getY(), true) != -1 || node.getPinAt(nx - node.getX(), ny - node.getY(), false) != -1 || (nx >= node.getX() && nx <= node.getX() + node.getWidth() && ny >= node.getY() && ny <= node.getY() + node.getHeight())) {
                    hitAnything = true; break;
                }
            }
            if (!hitAnything && renamingSectionId == null && renamingLibraryFunctionId == null) {
                WGraph.WSection secTitle = findSectionAt(nx, ny);
                if (secTitle != null) {
                    openSectionColorPicker(secTitle, (int) mouseX, (int) mouseY);
                    playUiClick(1.0f);
                    return true;
                }
            }
            if (!hitAnything) {
                clearPendingWireSpawn();
                isSearching = true;
                searchQuery = "";
                menuFlyoutPath.clear();
                clearStickyBrowseRoot();
                menuAnchorNx = nx;
                menuAnchorNy = ny;
                menuX = (int) mouseX;
                menuY = (int) mouseY;
                return true;
            }
        }
        if (button == 1) {
            for (WNode node : graph.getNodes()) {
                int inPin = node.getPinAt(nx - node.getX(), ny - node.getY(), true);
                int outPin = node.getPinAt(nx - node.getX(), ny - node.getY(), false);
                if (inPin != -1) {
                    recordCheckpointBeforeEdit();
                    graph.getConnections().removeIf(c -> c.targetNode().equals(node.getId()) && c.targetPin() == inPin);
                    graph.updateTopology();
                    return true;
                }
                if (outPin != -1) {
                    recordCheckpointBeforeEdit();
                    graph.getConnections().removeIf(c -> c.sourceNode().equals(node.getId()) && c.sourcePin() == outPin);
                    graph.updateTopology();
                    return true;
                }
            }
        }
        for (int i = graph.getNodes().size() - 1; i >= 0; i--) {
            WNode node = graph.getNodes().get(i);
            int outPin = node.getPinAt(nx - node.getX(), ny - node.getY(), false);
            if (outPin != -1 && !isEditorPeripheralLocked(node.getTypeId())) {
                linkingNode = node;
                linkingPin = outPin;
                return true;
            }
            if (nx >= node.getX() && nx <= node.getX() + node.getWidth() && ny >= node.getY() && ny <= node.getY() + node.getHeight()) {
                if (!Screen.hasShiftDown() && !node.isSelected()) graph.getNodes().forEach(n -> n.setSelected(false));
                node.setSelected(true);
                selectedNode = node; // Set before element interaction!

                if (button == 0 && Screen.hasAltDown() && node instanceof FunctionCardNode fh) {
                    enterFunctionGraphEdit(fh);
                    return true;
                }

                if (!isEditorPeripheralLocked(node.getTypeId())
                        && node.mouseClicked(nx - node.getX(), ny - node.getY(), button)) {
                    return true;
                }

                recordCheckpointBeforeEdit();
                draggingNode = node;
                dragOffsetX = nx - node.getX();
                dragOffsetY = ny - node.getY();
                graph.getNodes().remove(i);
                graph.getNodes().add(node);
                return true;
            }
        }
        if (button == 0) {
            if (Screen.hasShiftDown()) {
                isSelecting = true; selStartX = nx; selStartY = ny; selEndX = nx; selEndY = ny;
            } else {
                isPanning = true;
                graph.getNodes().forEach(n -> n.setSelected(false));
            }
            return true;
        }
        selectedNode = null; if (!Screen.hasShiftDown()) graph.getNodes().forEach(n -> n.setSelected(false));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int nx = screenToGraphX(mouseX);
        int ny = screenToGraphY(mouseY);
        if (isCreatingSection && button == 0) {
            sectionCreateEndX = nx;
            sectionCreateEndY = ny;
            finalizeSectionCreate();
            return true;
        }
        if (isSelecting) {
            float x1 = (float)Math.min(selStartX, selEndX); float y1 = (float)Math.min(selStartY, selEndY);
            float x2 = (float)Math.max(selStartX, selEndX); float y2 = (float)Math.max(selStartY, selEndY);
            for (WNode node : graph.getNodes()) {
                if (node.getX() + node.getWidth() >= x1 && node.getX() <= x2 && node.getY() + node.getHeight() >= y1 && node.getY() <= y2) node.setSelected(true);
            }
            isSelecting = false; return true;
        }
        if (linkingNode != null) {
            boolean linked = false;
            for (WNode node : graph.getNodes()) {
                int inPin = node.getPinAt(nx - node.getX(), ny - node.getY(), true);
                if (inPin != -1) {
                    if (isEditorPeripheralLocked(linkingNode.getTypeId())
                            || isEditorPeripheralLocked(node.getTypeId())) {
                        playUiClick(0.82f);
                        continue;
                    }
                    dev.devce.websnodelib.api.WPin srcPin = linkingNode.getOutputs().get(linkingPin);
                    dev.devce.websnodelib.api.WPin tgtPin = node.getInputs().get(inPin);
                    if (srcPin.getDataType() != tgtPin.getDataType()
                            && !(srcPin.getDataType() == dev.devce.websnodelib.api.WPin.DataType.NUMBER
                                    && tgtPin.getDataType() == dev.devce.websnodelib.api.WPin.DataType.STRING)) {
                        playUiClick(0.82f);
                        continue;
                    }
                    recordCheckpointBeforeEdit();
                    graph.connect(linkingNode.getId(), linkingPin, node.getId(), inPin);
                    playUiClick(1.1f);
                    linked = true;
                    break;
                }
            }
            if (!linked && isInsideEditorPanel(mouseX, mouseY)) {
                pendingWireFromNode = linkingNode;
                pendingWireFromOutputPin = linkingPin;
                pendingWireDragFrozen = true;
                pendingWireFrozenTx = nx;
                pendingWireFrozenTy = ny;
                isSearching = true;
                searchQuery = "";
                menuFlyoutPath.clear();
                clearStickyBrowseRoot();
                menuAnchorNx = nx;
                menuAnchorNy = ny;
                menuX = (int) mouseX;
                menuY = (int) mouseY;
            }
        }
        if (selectedNode != null && !isEditorPeripheralLocked(selectedNode.getTypeId())) {
            selectedNode.mouseReleased(nx, ny, button);
        }
        isPanning = false;
        draggingNode = null;
        draggingSection = null;
        resizingSection = null;
        sectionResizeHandle = SectionResizeHandle.NONE;
        linkingNode = null;
        linkingPin = -1;
        if (button == 0) {
            draggingWireConnIdx = -1;
            draggingWireWaypointIdx = -1;
        }
        sectionDragMemberNodes.clear();
        sectionDragOriginalNodePos.clear();
        sectionDragChildSections.clear();
        sectionDragOriginalNestedSectionPos.clear();
        sectionPickDragChannel = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (sectionColorPickerSectionId != null && sectionPickDragChannel >= 0 && button == 0) {
            sectionPickerSetChannelFromMouseX(sectionPickDragChannel, mouseX);
            return true;
        }
        if (isCreatingSection) {
            int nx = screenToGraphX(mouseX);
            int ny = screenToGraphY(mouseY);
            sectionCreateEndX = nx;
            sectionCreateEndY = ny;
            return true;
        }
        if (isSelecting) {
            float mx = screenToGraphX(mouseX);
            float my = screenToGraphY(mouseY);
            selEndX = mx; selEndY = my; return true;
        }
        if (draggingWireConnIdx >= 0 && button == 0) {
            if (draggingWireConnIdx >= graph.getConnections().size()) {
                draggingWireConnIdx = -1;
                draggingWireWaypointIdx = -1;
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            int gnx = screenToGraphX(mouseX);
            int gny = screenToGraphY(mouseY);
            WConnection c = graph.getConnections().get(draggingWireConnIdx);
            int[] nxs = new int[c.waypointXs().length];
            int[] nys = new int[c.waypointYs().length];
            System.arraycopy(c.waypointXs(), 0, nxs, 0, nxs.length);
            System.arraycopy(c.waypointYs(), 0, nys, 0, nys.length);
            nxs[draggingWireWaypointIdx] = gnx;
            nys[draggingWireWaypointIdx] = gny;
            graph.getConnections().set(draggingWireConnIdx, c.withWaypoints(nxs, nys));
            return true;
        }
        if (isPanning) {
            cameraFocusActive = false;
            float s = editorContentScale();
            panX += dragX / s;
            panY += dragY / s;
            return true;
        }
        if (resizingSection != null && sectionResizeHandle != SectionResizeHandle.NONE) {
            int nx = screenToGraphX(mouseX);
            int ny = screenToGraphY(mouseY);
            int dnx = nx - sectionResizeGrabNx;
            int dny = ny - sectionResizeGrabNy;
            int x = sectionResizeStartX;
            int y = sectionResizeStartY;
            int w = sectionResizeStartW;
            int h = sectionResizeStartH;
            int newX = x;
            int newY = y;
            int newW = w;
            int newH = h;
            switch (sectionResizeHandle) {
                case E -> newW = Math.max(MIN_SECTION_W, w + dnx);
                case S -> newH = Math.max(MIN_SECTION_H, h + dny);
                case W -> {
                    newX = x + dnx;
                    newW = w - dnx;
                    if (newW < MIN_SECTION_W) {
                        newX = x + w - MIN_SECTION_W;
                        newW = MIN_SECTION_W;
                    }
                }
                case SE -> {
                    newW = Math.max(MIN_SECTION_W, w + dnx);
                    newH = Math.max(MIN_SECTION_H, h + dny);
                }
                case SW -> {
                    newH = Math.max(MIN_SECTION_H, h + dny);
                    newX = x + dnx;
                    newW = w - dnx;
                    if (newW < MIN_SECTION_W) {
                        newX = x + w - MIN_SECTION_W;
                        newW = MIN_SECTION_W;
                    }
                }
                default -> {
                }
            }
            resizingSection.setPos(newX, newY);
            resizingSection.setSize(newW, newH);
            return true;
        }
        if (draggingSection != null) {
            int nx = screenToGraphX(mouseX);
            int ny = screenToGraphY(mouseY);
            int newX = nx - sectionDragOffsetX;
            int newY = ny - sectionDragOffsetY;
            int totalDx = newX - sectionDragStartSectionX;
            int totalDy = newY - sectionDragStartSectionY;
            int ddx = totalDx - sectionDragPrevTotalDx;
            int ddy = totalDy - sectionDragPrevTotalDy;
            sectionDragPrevTotalDx = totalDx;
            sectionDragPrevTotalDy = totalDy;
            draggingSection.setPos(newX, newY);
            for (WGraph.WSection nested : sectionDragChildSections) {
                int[] sp = sectionDragOriginalNestedSectionPos.get(nested.getId());
                if (sp != null) {
                    nested.setPos(sp[0] + totalDx, sp[1] + totalDy);
                }
            }
            for (UUID id : sectionDragMemberNodes) {
                WNode n = findNode(id);
                int[] p = sectionDragOriginalNodePos.get(id);
                if (n != null && p != null) {
                    n.setPos(p[0] + totalDx, p[1] + totalDy);
                }
            }
            if ((ddx != 0 || ddy != 0) && !sectionDragMemberNodes.isEmpty()) {
                graph.shiftWaypointsForConnectionsTouching(sectionDragMemberNodes, ddx, ddy);
            }
            return true;
        }
        if (draggingNode != null) {
            int idx;
            int idy;
            if (Screen.hasShiftDown()) {
                float s = editorContentScale();
                idx = (int) (dragX / s);
                idy = (int) (dragY / s);
            } else {
                int targetX = screenToGraphX(mouseX) - (int) dragOffsetX;
                int targetY = screenToGraphY(mouseY) - (int) dragOffsetY;
                idx = targetX - draggingNode.getX();
                idy = targetY - draggingNode.getY();
            }
            if (idx != 0 || idy != 0) {
                List<UUID> moved = new ArrayList<>();
                for (WNode n : graph.getNodes()) {
                    if (n.isSelected()) {
                        moved.add(n.getId());
                    }
                }
                if (!moved.isEmpty()) {
                    graph.shiftWaypointsForConnectionsTouching(moved, idx, idy);
                }
                for (WNode n : graph.getNodes()) if (n.isSelected()) n.setPos(n.getX() + idx, n.getY() + idy);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (importDialogOpen) {
            return true;
        }
        if (!isInsideEditorPanel(mouseX, mouseY)) {
            return false;
        }
        if (itemPickerOpen && itemPickerContains(mouseX, mouseY)) {
            int maxScroll = Math.max(0, itemPickCandidates.size() - ITEM_PICK_VISIBLE_ROWS);
            itemPickerScroll =
                    Mth.clamp(itemPickerScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (scrollY != 0) {
            cameraFocusActive = false;
        }
        if (sectionColorPickerSectionId != null) {
            return true;
        }
        if (functionPickerOpen) {
            if (functionImportSubmenuOpen && functionImportFlyoutContains(mouseX, mouseY)) {
                int nf = functionDiscImportFiles.size();
                if (nf > FUNCTION_LIB_VISIBLE_ROWS) {
                    functionDiscImportListScroll =
                            Mth.clamp(
                                    functionDiscImportListScroll
                                            - (int) Math.signum(scrollY),
                                    0,
                                    nf - FUNCTION_LIB_VISIBLE_ROWS);
                }
                return true;
            }
            if (functionPickerDefsViewportContains(mouseX, mouseY) && functionStore != null) {
                int n = functionStore.size();
                if (n > FUNCTION_LIB_VISIBLE_ROWS) {
                    functionLibraryListScroll =
                            Mth.clamp(
                                    functionLibraryListScroll - (int) Math.signum(scrollY),
                                    0,
                                    n - FUNCTION_LIB_VISIBLE_ROWS);
                }
                return true;
            }
        }
        zoom = (float) Math.max(0.1, Math.min(3.0, zoom + scrollY * 0.1));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (exportDialogOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeExportDialog();
                playUiClick(0.94f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                minecraft.keyboardHandler.setClipboard(exportDialogText);
                postShareStatus(false, "gui.computed.share.export_success", exportDialogText.length());
                playUiClick(1.03f);
                return true;
            }
            if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
                minecraft.keyboardHandler.setClipboard(exportDialogText);
                postShareStatus(false, "gui.computed.share.export_success", exportDialogText.length());
                playUiClick(1.03f);
                return true;
            }
            return true;
        }
        if (importDialogOpen) {
            boolean ctrl = hasControlDown();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeImportFromStringDialog();
                playUiClick(0.94f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                importGraphFromShareString(importDialogText);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    importDialogText += clip;
                    playUiClick(1.03f);
                }
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                minecraft.keyboardHandler.setClipboard(importDialogText);
                playUiClick(1.01f);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                // Keep simple text editor behavior: Ctrl+A mirrors selecting all by copying full text context.
                minecraft.keyboardHandler.setClipboard(importDialogText);
                playUiClick(0.98f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !importDialogText.isEmpty()) {
                importDialogText = importDialogText.substring(0, importDialogText.length() - 1);
                playUiClick(0.9f);
                return true;
            }
            return true;
        }
        if (newFunctionNamingOpen && functionStore != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelNewFunctionNaming();
                playUiClick(0.94f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmNewFunctionAfterNaming();
                playUiClick(1.04f);
                return true;
            }
            boolean ctrl = hasControlDown();
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !newFunctionNameBuffer.isEmpty()) {
                if (ctrl) {
                    newFunctionNameBuffer = "";
                } else {
                    newFunctionNameBuffer = newFunctionNameBuffer.substring(0, newFunctionNameBuffer.length() - 1);
                }
                playUiClick(0.9f);
                return true;
            }
            return true;
        }
        if (itemPickerOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeItemPicker();
                playUiClick(0.92f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !itemPickerQuery.isEmpty()) {
                itemPickerQuery = itemPickerQuery.substring(0, itemPickerQuery.length() - 1);
                itemPickerScroll = 0;
                rebuildItemPickCandidates();
                playUiClick(0.9f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!itemPickCandidates.isEmpty() && itemPickerCallback != null) {
                    itemPickerCallback.accept(itemPickCandidates.get(0).copyWithCount(1));
                }
                closeItemPicker();
                playUiClick(1.03f);
                return true;
            }
            return true;
        }
        if (tryHandleUndoRedo(keyCode, scanCode)) {
            return true;
        }
        if (sectionColorPickerSectionId != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeSectionColorPicker();
            return true;
        }
        if (renamingLibraryFunctionId != null) {
            return handleLibraryFunctionRenameKey(keyCode, scanCode, modifiers);
        }
        if (!functionEditStack.isEmpty() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            exitFunctionGraphEdit();
            return true;
        }
        if (renamingSectionId != null) {
            boolean ctrl = hasControlDown();
            boolean shift = Screen.hasShiftDown();
            if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                    || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                    || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
                    || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                    || keyCode == GLFW.GLFW_KEY_LEFT_ALT
                    || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                    || keyCode == GLFW.GLFW_KEY_LEFT_SUPER
                    || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER) {
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                sectionRenameSelectionPos = 0;
                sectionRenameCursor = sectionRenameBuffer.length();
                playUiClick(0.97f);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                if (sectionRenameHasSelection()) {
                    int a = Math.min(sectionRenameCursor, sectionRenameSelectionPos);
                    int b = Math.max(sectionRenameCursor, sectionRenameSelectionPos);
                    minecraft.keyboardHandler.setClipboard(sectionRenameBuffer.substring(a, b));
                } else {
                    minecraft.keyboardHandler.setClipboard(sectionRenameBuffer);
                }
                playUiClick(1.02f);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
                if (sectionRenameHasSelection()) {
                    int a = Math.min(sectionRenameCursor, sectionRenameSelectionPos);
                    int b = Math.max(sectionRenameCursor, sectionRenameSelectionPos);
                    minecraft.keyboardHandler.setClipboard(sectionRenameBuffer.substring(a, b));
                    sectionRenameDeleteSelection();
                    playUiClick(0.9f);
                }
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    sectionRenameReplaceSelection(sectionRenameSanitizePaste(clip));
                    playUiClick(1.04f);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                recordCheckpointBeforeEdit();
                for (WGraph.WSection s : graph.getSections()) {
                    if (s.getId().equals(renamingSectionId)) {
                        s.setName(sectionRenameBuffer.trim().isEmpty() ? s.getName() : sectionRenameBuffer.trim());
                        break;
                    }
                }
                endSectionRenameEditing();
                playUiClick(1.0f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                endSectionRenameEditing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (sectionRenameHasSelection()) {
                    sectionRenameDeleteSelection();
                } else if (sectionRenameCursor > 0) {
                    int start = ctrl ? sectionRenamePreviousWordBoundary(sectionRenameCursor) : sectionRenameCursor - 1;
                    sectionRenameBuffer =
                            sectionRenameBuffer.substring(0, start) + sectionRenameBuffer.substring(sectionRenameCursor);
                    sectionRenameCursor = start;
                    sectionRenameSelectionPos = sectionRenameCursor;
                }
                playUiClick(0.9f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (sectionRenameHasSelection()) {
                    sectionRenameDeleteSelection();
                } else if (sectionRenameCursor < sectionRenameBuffer.length()) {
                    int end = ctrl ? sectionRenameNextWordBoundary(sectionRenameCursor) : sectionRenameCursor + 1;
                    sectionRenameBuffer =
                            sectionRenameBuffer.substring(0, sectionRenameCursor) + sectionRenameBuffer.substring(end);
                }
                playUiClick(0.9f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                int next = ctrl ? sectionRenamePreviousWordBoundary(sectionRenameCursor) : Math.max(0, sectionRenameCursor - 1);
                sectionRenameMoveCursor(next, shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                int next = ctrl
                        ? sectionRenameNextWordBoundary(sectionRenameCursor)
                        : Math.min(sectionRenameBuffer.length(), sectionRenameCursor + 1);
                sectionRenameMoveCursor(next, shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                sectionRenameMoveCursor(0, shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                sectionRenameMoveCursor(sectionRenameBuffer.length(), shift);
                return true;
            }
            return true;
        }
        if (isSearching) {
            if (keyCode == 256) {
                isSearching = false;
                clearStickyBrowseRoot();
                clearPendingWireSpawn();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                rebuildSearchHitRows();
                if (!searchQuery.trim().isEmpty() && !searchHitRows.isEmpty()) {
                    boolean placedAny = false;
                    for (BrowseNodeRow row : searchHitRows) {
                        if (!isEditorPeripheralLocked(row.nodeType())) {
                            WNode placed = addNodeAtReturning(row.nodeType(), menuAnchorNx, menuAnchorNy);
                            tryAutoConnectPendingOutput(placed);
                            isSearching = false;
                            clearStickyBrowseRoot();
                            placedAny = true;
                            break;
                        }
                    }
                    if (!placedAny) {
                        playUiClick(0.82f);
                    }
                }
                return true;
            }
            if (keyCode == 259) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                } else if (!menuFlyoutPath.isEmpty()) {
                    menuFlyoutPath.remove(menuFlyoutPath.size() - 1);
                }
                return true;
            }
            return true;
        }
        if (selectedNode != null
                && !isEditorPeripheralLocked(selectedNode.getTypeId())
                && selectedNode.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean nodeUiFocused =
                selectedNode != null
                        && !isEditorPeripheralLocked(selectedNode.getTypeId())
                        && selectedNode.hasFocusedElement();
        if (!nodeUiFocused && keyCode == GLFW.GLFW_KEY_S && hasControlDown()) {
            int nx = screenToGraphX(this.mouseX);
            int ny = screenToGraphY(this.mouseY);
            beginSectionCreate(nx, ny);
            playUiClick(1.02f);
            return true;
        }
        if (!nodeUiFocused && keyCode == GLFW.GLFW_KEY_F2 && selectedSectionId != null) {
            for (WGraph.WSection s : graph.getSections()) {
                if (s.getId().equals(selectedSectionId)) {
                    startSectionRename(s.getId(), s.getName());
                    return true;
                }
            }
        }
        if (!nodeUiFocused
                && keyCode == GLFW.GLFW_KEY_F2
                && selectedLibraryFunctionId != null
                && functionStore != null) {
            FunctionDefinitionStore.Definition lf = functionStore.get(selectedLibraryFunctionId);
            if (lf != null && !isFunctionLibraryDefinitionHardwareLocked(lf)) {
                startLibraryFunctionRename(lf.id(), lf.name());
                functionPickerOpen = true;
                return true;
            }
        }
        boolean plainDeleteNoMods =
                !hasControlDown() && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_X);
        if (plainDeleteNoMods && !nodeUiFocused && renamingSectionId == null && renamingLibraryFunctionId == null) {
            if (anyNodeSelectedForDock()) {
                deleteSelectedNodes();
                return true;
            }
            if (selectedSectionId != null) {
                deleteSectionAndMembers(selectedSectionId);
                return true;
            }
            if (selectedNode != null) {
                recordCheckpointBeforeEdit();
                graph.removeNode(selectedNode);
                selectedNode = null;
                return true;
            }
        }
        if (hasControlDown()
                && keyCode == GLFW.GLFW_KEY_X
                && !nodeUiFocused
                && renamingSectionId == null
                && renamingLibraryFunctionId == null) {
            if (anyNodeSelectedForDock()) {
                copySelectedNodesToClipboard();
                deleteSelectedNodes();
                return true;
            }
            if (selectedSectionId != null) {
                UUID sid = selectedSectionId;
                copySectionBundle(sid);
                deleteSectionAndMembers(sid);
                return true;
            }
        }
        if (keyCode == 73 && hasControlDown() && hasAltDown()) { spawnSecretNode(); return true; }
        if (keyCode == 65 && hasControlDown()) { graph.getNodes().forEach(n -> n.setSelected(true)); return true; }
        if (keyCode == 67 && hasControlDown()) { copySelected(); return true; }
        if (keyCode == 86 && hasControlDown()) { pasteFromClipboard(); return true; }
        if (keyCode == 65 && Screen.hasShiftDown()) {
            clearPendingWireSpawn();
            isSearching = true;
            searchQuery = "";
            menuFlyoutPath.clear();
            clearStickyBrowseRoot();
            menuAnchorNx = screenToGraphX(this.mouseX);
            menuAnchorNy = screenToGraphY(this.mouseY);
            menuX = this.mouseX;
            menuY = this.mouseY;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (importDialogOpen) {
            if (!Character.isISOControl(codePoint)) {
                importDialogText += codePoint;
            }
            return true;
        }
        if (newFunctionNamingOpen && functionStore != null) {
            if (!Character.isISOControl(codePoint) && newFunctionNameBuffer.length() < 48) {
                newFunctionNameBuffer += codePoint;
            }
            return true;
        }
        if (itemPickerOpen) {
            if (!Character.isISOControl(codePoint) && itemPickerQuery.length() < 64) {
                itemPickerQuery += codePoint;
                itemPickerScroll = 0;
                rebuildItemPickCandidates();
            }
            return true;
        }
        if (renamingLibraryFunctionId != null) {
            if (!Character.isISOControl(codePoint)) {
                libraryFnRenameReplaceSelection(String.valueOf(codePoint));
            }
            return true;
        }
        if (renamingSectionId != null) {
            if (!Character.isISOControl(codePoint)) {
                sectionRenameReplaceSelection(String.valueOf(codePoint));
            }
            return true;
        }
        if (isSearching) {
            if (searchQuery.isEmpty() && (codePoint == 'a' || codePoint == 'A' || codePoint == 'ф' || codePoint == 'Ф')) return true;
            searchQuery += codePoint;
            return true;
        }
        if (selectedNode != null
                && !isEditorPeripheralLocked(selectedNode.getTypeId())
                && selectedNode.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private WNode addNodeAtReturning(net.minecraft.resources.ResourceLocation type, int x, int y) {
        if (SECTION_TOOL_TYPE.equals(type)) {
            beginSectionCreate(x, y);
            playUiClick(1.02f);
            return null;
        }
        if (isEditorPeripheralLocked(type)) {
            playUiClick(0.82f);
            return null;
        }
        WNode node = NodeRegistry.createNode(type, x, y);
        if (node != null) {
            recordCheckpointBeforeEdit();
            graph.addNode(node);
            playUiClick(1.05f);
        }
        return node;
    }

    private void clearPendingWireSpawn() {
        pendingWireFromNode = null;
        pendingWireFromOutputPin = -1;
        pendingWireDragFrozen = false;
    }

    /** Connect pending output wire to {@code newNode}'s first input, if any. */
    private void tryAutoConnectPendingOutput(WNode newNode) {
        if (pendingWireFromNode == null || pendingWireFromOutputPin < 0) {
            return;
        }
        try {
            if (newNode != null && !newNode.getInputs().isEmpty()) {
                recordCheckpointBeforeEdit();
                graph.getConnections()
                        .removeIf(c -> c.targetNode().equals(newNode.getId()) && c.targetPin() == 0);
                graph.connect(
                        pendingWireFromNode.getId(),
                        pendingWireFromOutputPin,
                        newNode.getId(),
                        0);
            }
        } finally {
            clearPendingWireSpawn();
        }
    }

    private void spawnSecretNode() {
        int nx = (int)(-panX + width / 2f); int ny = (int)(-panY + height / 2f);
        WNode node = new WNode(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("websnodelib", "secret"), "Webyep's Gift", nx, ny);
        node.setWidth(160);
        node.addElement(new dev.devce.websnodelib.api.elements.WGif(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("websnodelib", "textures/gui/secret.gif"), 150, 150));
        node.addElement(new dev.devce.websnodelib.api.elements.WLabel("   Made with love from Webyep", 0xFF00FF88));
        recordCheckpointBeforeEdit();
        graph.addNode(node);
    }

    private static boolean dockButtonHovered(int mx, int my, int bx, int by, int btn) {
        return mx >= bx && mx < bx + btn && my >= by && my < by + btn;
    }

    private void renderNodeActionDock(GuiGraphics graphics, int mx, int my, float ease) {
        if (!anyNodeSelectedForDock()) {
            return;
        }
        NodeDockLayout L = computeNodeDockLayout();
        int alphaBg = (int) (230 * ease);
        graphics.fill(L.barX, L.barY, L.barX + L.barW, L.barY + L.barH, (alphaBg << 24) | 0x121212);
        graphics.renderOutline(L.barX, L.barY, L.barW, L.barH, 0xFF00FF88);

        int iconOff = (L.btn - 16) / 2;
        if (dockButtonHovered(mx, my, L.dupX, L.btnY, L.btn)) {
            graphics.fill(L.dupX, L.btnY, L.dupX + L.btn, L.btnY + L.btn, 0x4400FF88);
        }
        graphics.blit(ICON_DUPLICATE, L.dupX + iconOff, L.btnY + iconOff, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        graphics.drawString(font, "+", L.dupX + 3, L.btnY + L.btn - font.lineHeight - 1, 0xFF669966, false);

        if (dockButtonHovered(mx, my, L.delX, L.btnY, L.btn)) {
            graphics.fill(L.delX, L.btnY, L.delX + L.btn, L.btnY + L.btn, 0x44FF6666);
        }
        graphics.blit(ICON_DELETE, L.delX + iconOff, L.btnY + iconOff, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        int dockKey = 11;
        int dk = L.btn - dockKey - 3;
        blitScaledHintTile(graphics, KEY_CAP_DEL, L.delX + dk, L.btnY + dk, dockKey);
        blitScaledHintTile(graphics, KEY_CAP_X, L.delX + 3, L.btnY + dk, dockKey);

        if (dockButtonHovered(mx, my, L.disX, L.btnY, L.btn)) {
            graphics.fill(L.disX, L.btnY, L.disX + L.btn, L.btnY + L.btn, 0x44FFCC66);
        }
        graphics.blit(ICON_DISCONNECT, L.disX + iconOff, L.btnY + iconOff, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        graphics.drawString(font, "~", L.disX + 3, L.btnY + L.btn - font.lineHeight - 1, 0xFF997755, false);

        if (dockButtonHovered(mx, my, L.dupX, L.btnY, L.btn)) {
            graphics.renderTooltip(
                    font,
                    Component.literal(
                            "Duplicate: copies with new IDs. Clipboard paste also fixed so IDs are not reused."),
                    mx,
                    my);
        } else if (dockButtonHovered(mx, my, L.delX, L.btnY, L.btn)) {
            graphics.renderTooltip(font, Component.literal("Delete selected (Del / X)"), mx, my);
        } else if (dockButtonHovered(mx, my, L.disX, L.btnY, L.btn)) {
            graphics.renderTooltip(
                    font, Component.literal("Disconnect: remove all wires to/from selection"), mx, my);
        }
    }

    private boolean tryHandleNodeDockClick(double mouseX, double mouseY, int button) {
        if (isSearching) {
            return false;
        }
        if (button != 0 && button != 1) {
            return false;
        }
        if (!anyNodeSelectedForDock()) {
            return false;
        }
        NodeDockLayout L = computeNodeDockLayout();
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (mx < L.barX || mx >= L.barX + L.barW || my < L.barY || my >= L.barY + L.barH) {
            return false;
        }
        if (dockButtonHovered(mx, my, L.dupX, L.btnY, L.btn)) {
            duplicateSelectedNodes();
            return true;
        }
        if (dockButtonHovered(mx, my, L.delX, L.btnY, L.btn)) {
            deleteSelectedNodes();
            return true;
        }
        if (dockButtonHovered(mx, my, L.disX, L.btnY, L.btn)) {
            disconnectSelectedNodes();
            return true;
        }
        return false;
    }

    private void duplicateSelectedNodes() {
        List<WNode> sel = new ArrayList<>();
        for (WNode n : graph.getNodes()) {
            if (n.isSelected()) {
                sel.add(n);
            }
        }
        if (sel.isEmpty()) {
            return;
        }
        recordCheckpointBeforeEdit();
        playUiClick(1.08f);
        final int dx = 24;
        final int dy = 24;
        graph.getNodes().forEach(n -> n.setSelected(false));
        WNode last = null;
        for (WNode src : sel) {
            if (src.isDuplicationLocked()) {
                continue;
            }
            CompoundTag t = src.save().copy();
            t.remove("id");
            t.putInt("x", src.getX() + dx);
            t.putInt("y", src.getY() + dy);
            net.minecraft.resources.ResourceLocation type =
                    net.minecraft.resources.ResourceLocation.parse(t.getString("typeId"));
            WNode copy = NodeRegistry.createNode(type, t.getInt("x"), t.getInt("y"));
            if (copy != null && !isEditorPeripheralLocked(type)) {
                copy.load(t);
                graph.addNode(copy);
                copy.setSelected(true);
                last = copy;
            }
        }
        selectedNode = last;
    }

    private void deleteSelectedNodes() {
        List<WNode> rm = new ArrayList<>();
        for (WNode n : graph.getNodes()) {
            if (n.isSelected() && !n.isDeletionLocked()) {
                rm.add(n);
            }
        }
        if (rm.isEmpty()) {
            return;
        }
        recordCheckpointBeforeEdit();
        playUiClick(0.92f);
        for (WNode n : rm) {
            graph.removeNode(n);
        }
        selectedNode = null;
    }

    private void disconnectSelectedNodes() {
        Set<UUID> ids = new HashSet<>();
        for (WNode n : graph.getNodes()) {
            if (n.isSelected()) {
                ids.add(n.getId());
            }
        }
        if (!ids.isEmpty()) {
            recordCheckpointBeforeEdit();
            playUiClick(0.98f);
        }
        graph.disconnectNodes(ids);
    }

    private static boolean nodeCenterInsideSection(WNode n, WGraph.WSection s) {
        int cx = n.getX() + n.getWidth() / 2;
        int cy = n.getY() + n.getHeight() / 2;
        return cx >= s.getX()
                && cx <= s.getX() + s.getWidth()
                && cy >= s.getY()
                && cy <= s.getY() + s.getHeight();
    }

    private void deleteSectionAndMembers(UUID sectionId) {
        WGraph.WSection sec = null;
        for (WGraph.WSection s : graph.getSections()) {
            if (s.getId().equals(sectionId)) {
                sec = s;
                break;
            }
        }
        if (sec == null) {
            return;
        }
        recordCheckpointBeforeEdit();
        List<WNode> rm = new ArrayList<>();
        for (WNode n : graph.getNodes()) {
            if (nodeCenterInsideSection(n, sec) && !n.isDeletionLocked()) {
                rm.add(n);
            }
        }
        for (WNode n : rm) {
            graph.removeNode(n);
        }
        List<UUID> removeSectionIds = new ArrayList<>();
        removeSectionIds.add(sectionId);
        for (WGraph.WSection s : graph.getSections()) {
            if (!s.getId().equals(sectionId) && sectionFullyContainedIn(s, sec)) {
                removeSectionIds.add(s.getId());
            }
        }
        graph.getSections().removeIf(s -> removeSectionIds.contains(s.getId()));
        selectedSectionId = null;
        if (sectionColorPickerSectionId != null && removeSectionIds.contains(sectionColorPickerSectionId)) {
            closeSectionColorPicker();
        }
        selectedNode = null;
        playUiClick(0.92f);
    }

    private void copySectionBundle(UUID sectionId) {
        WGraph.WSection sec = null;
        for (WGraph.WSection s : graph.getSections()) {
            if (s.getId().equals(sectionId)) {
                sec = s;
                break;
            }
        }
        if (sec == null) {
            return;
        }
        List<WNode> inside = new ArrayList<>();
        ListTag nodesTag = new ListTag();
        for (WNode node : graph.getNodes()) {
            if (nodeCenterInsideSection(node, sec)) {
                if (node.isDuplicationLocked()) {
                    continue;
                }
                inside.add(node);
                nodesTag.add(node.save());
            }
        }
        Set<UUID> insideIds = new HashSet<>();
        for (WNode n : inside) {
            insideIds.add(n.getId());
        }
        ListTag connTag = new ListTag();
        for (WConnection conn : graph.getConnections()) {
            if (!insideIds.contains(conn.sourceNode()) || !insideIds.contains(conn.targetNode())) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putString("src", conn.sourceNode().toString());
            c.putInt("srcP", conn.sourcePin());
            c.putString("tgt", conn.targetNode().toString());
            c.putInt("tgtP", conn.targetPin());
            if (conn.waypointXs().length > 0) {
                ListTag wps = new ListTag();
                for (int j = 0; j < conn.waypointXs().length; j++) {
                    CompoundTag w = new CompoundTag();
                    w.putInt("x", conn.waypointXs()[j]);
                    w.putInt("y", conn.waypointYs()[j]);
                    wps.add(w);
                }
                c.put("wps", wps);
            }
            connTag.add(c);
        }
        ListTag sectionsTag = new ListTag();
        sectionsTag.add(sec.toNbt());
        List<WGraph.WSection> nested = new ArrayList<>();
        for (WGraph.WSection s : graph.getSections()) {
            if (s.getId().equals(sec.getId())) {
                continue;
            }
            if (sectionFullyContainedIn(s, sec)) {
                nested.add(s);
            }
        }
        nested.sort(Comparator.comparingInt(WGraph.WSection::getLayer));
        for (WGraph.WSection s : nested) {
            sectionsTag.add(s.toNbt());
        }
        CompoundTag root = new CompoundTag();
        root.put("nodes", nodesTag);
        root.put("conns", connTag);
        root.put("sections", sectionsTag);
        root.putBoolean("computedSectionClipboard", true);
        minecraft.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(root.toString().getBytes()));
        playUiClick(1.03f);
    }

    private void copySelected() {
        boolean anyNodes = false;
        for (WNode n : graph.getNodes()) {
            if (n.isSelected()) {
                anyNodes = true;
                break;
            }
        }
        if (anyNodes) {
            copySelectedNodesToClipboard();
            return;
        }
        if (selectedSectionId != null) {
            copySectionBundle(selectedSectionId);
        }
    }

    private void copySelectedNodesToClipboard() {
        ListTag nodesTag = new ListTag();
        for (WNode node : graph.getNodes()) {
            if (node.isSelected() && !node.isDuplicationLocked()) {
                nodesTag.add(node.save());
            }
        }
        if (nodesTag.isEmpty()) {
            return;
        }
        CompoundTag root = new CompoundTag();
        root.put("nodes", nodesTag);
        ListTag connTag = new ListTag();
        for (WConnection conn : graph.getConnections()) {
            WNode src = findNode(conn.sourceNode());
            WNode tgt = findNode(conn.targetNode());
            if (src != null && tgt != null && src.isSelected() && tgt.isSelected()) {
                CompoundTag c = new CompoundTag();
                c.putString("src", conn.sourceNode().toString());
                c.putInt("srcP", conn.sourcePin());
                c.putString("tgt", conn.targetNode().toString());
                c.putInt("tgtP", conn.targetPin());
                if (conn.waypointXs().length > 0) {
                    ListTag wps = new ListTag();
                    for (int j = 0; j < conn.waypointXs().length; j++) {
                        CompoundTag w = new CompoundTag();
                        w.putInt("x", conn.waypointXs()[j]);
                        w.putInt("y", conn.waypointYs()[j]);
                        wps.add(w);
                    }
                    c.put("wps", wps);
                }
                connTag.add(c);
            }
        }
        root.put("conns", connTag);
        minecraft.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(root.toString().getBytes()));
        playUiClick(1.03f);
    }

    private void pasteFromClipboard() {
        String data = minecraft.keyboardHandler.getClipboard(); if (data == null || data.isEmpty()) return;
        try {
            String decoded = new String(Base64.getDecoder().decode(data));
            CompoundTag root = TagParser.parseTag(decoded);
            ListTag nodesTag = root.getList("nodes", 10);
            ListTag sectionsClipboard = root.getList("sections", 10);
            if (nodesTag.isEmpty() && sectionsClipboard.isEmpty()) {
                return;
            }
            recordCheckpointBeforeEdit();
            Map<UUID, UUID> oldToNew = new HashMap<>();
            graph.getNodes().forEach(n -> n.setSelected(false));
            for (int i = 0; i < sectionsClipboard.size(); i++) {
                CompoundTag st = sectionsClipboard.getCompound(i).copy();
                st.remove("id");
                st.putInt("x", st.getInt("x") + 10);
                st.putInt("y", st.getInt("y") + 10);
                graph.getSections().add(WGraph.WSection.fromNbt(st));
            }
            for (int i = 0; i < nodesTag.size(); i++) {
                CompoundTag raw = nodesTag.getCompound(i);
                UUID oldId = UUID.fromString(raw.getString("id"));
                CompoundTag nTag = raw.copy();
                nTag.remove("id");
                net.minecraft.resources.ResourceLocation type =
                        net.minecraft.resources.ResourceLocation.parse(nTag.getString("typeId"));
                if (FunctionStartNode.TYPE_FN_START.equals(type) || FunctionEndNode.TYPE_FN_END.equals(type)) {
                    continue;
                }
                WNode newNode = NodeRegistry.createNode(type, nTag.getInt("x") + 10, nTag.getInt("y") + 10);
                if (newNode != null && !isEditorPeripheralLocked(type)) {
                    newNode.load(nTag);
                    oldToNew.put(oldId, newNode.getId());
                    graph.addNode(newNode);
                    newNode.setSelected(true);
                }
            }
            ListTag connTag = root.getList("conns", 10);
            for (int i = 0; i < connTag.size(); i++) {
                CompoundTag c = connTag.getCompound(i);
                UUID newSrc = oldToNew.get(UUID.fromString(c.getString("src")));
                UUID newTgt = oldToNew.get(UUID.fromString(c.getString("tgt")));
                if (newSrc == null || newTgt == null) {
                    continue;
                }
                if (c.contains("wps")) {
                    ListTag wps = c.getList("wps", 10);
                    int[] wx = new int[wps.size()];
                    int[] wy = new int[wps.size()];
                    for (int j = 0; j < wps.size(); j++) {
                        CompoundTag w = wps.getCompound(j);
                        wx[j] = w.getInt("x") + 10;
                        wy[j] = w.getInt("y") + 10;
                    }
                    graph.connect(
                            new WConnection(newSrc, c.getInt("srcP"), newTgt, c.getInt("tgtP"), wx, wy));
                } else {
                    graph.connect(newSrc, c.getInt("srcP"), newTgt, c.getInt("tgtP"));
                }
            }
            if (!oldToNew.isEmpty() || !sectionsClipboard.isEmpty()) {
                playUiClick(1.07f);
            }
        } catch (Exception e) {}
    }
}
