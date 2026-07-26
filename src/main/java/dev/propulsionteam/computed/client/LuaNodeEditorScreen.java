package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.client.editor.canvas.LuaEditorGraphAdapter;
import dev.propulsionteam.computed.client.editor.canvas.LuaEditorNode;
import dev.propulsionteam.computed.client.editor.lua.LuaEditorSession;
import dev.propulsionteam.computed.client.editor.lua.LuaSyntaxHighlighter;
import dev.propulsionteam.computed.client.editor.preview.LuaLivePreview;
import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.LuaDefinitionFiles;
import dev.propulsionteam.computed.persistence.LuaDefinitionClipboard;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.luaj.vm2.LuaValue;

public final class LuaNodeEditorScreen extends Screen {
    private static final List<String> COMPLETIONS = List.of(
            "computed.node(1, \"namespace:id\", \"Title\")",
            "node:category(\"utility\")",
            "node:style(\"standard\")",
            "node:input(\"id\", \"number\", { default = 0 })",
            "node:output(\"id\", \"number\")",
            "node:field(\"id\", \"number\", { default = 0 })",
            "node:field(\"id\", \"number\", { default = 0, min = 0, max = 1, control = \"slider\", step = 0.01, label = \"Value\" })",
            "node:state(\"id\", 0)",
            "node:execution(\"input\")",
            "node:on_run(function(ctx)",
            "node:on_event(\"event\", function(ctx, value)",
            "ctx:input(\"id\")",
            "ctx:output(\"id\", value)",
            "ctx:field(\"id\")",
            "ctx:state(\"id\")",
            "ctx:set_state(\"id\", value)",
            "ctx:endpoint(\"namespace:endpoint\")",
            "ctx:emit(\"event\")",
            "ctx:tick()",
            "ctx:graph_step()",
            "ctx:is_preview()");

    private final ComputerEditorScreen parent;
    private final ComputedProgramV3 program;
    private final boolean creationMode;
    private final int placementX;
    private final int placementY;
    private final LuaEditorSession session = new LuaEditorSession();
    private final Map<String, LuaValue> sampleInputs = new LinkedHashMap<>();
    private final Map<String, LuaValue> sampleFields = new LinkedHashMap<>();
    private String source;
    private int cursor;
    private int firstLine;
    private int horizontalScroll;
    private List<List<LuaSyntaxHighlighter.Span>> highlightedLines;
    private boolean sourceFocused = true;
    private boolean completionOpen;
    private int completionIndex;
    private boolean replacementConfirmation;
    private String status = "";
    private LuaLivePreview preview;
    private LuaEditorNode previewNode;
    private LuaNodeDefinition previewDefinition;
    private String fileDefinitionId = "";

    public LuaNodeEditorScreen(
            ComputerEditorScreen parent,
            ComputedProgramV3 program,
            String source) {
        this(parent, program, source, false, 0, 0);
    }

    public LuaNodeEditorScreen(
            ComputerEditorScreen parent,
            ComputedProgramV3 program,
            String source,
            boolean creationMode,
            int placementX,
            int placementY) {
        super(Component.literal("Lua Node Editor"));
        this.parent = parent;
        this.program = program;
        this.creationMode = creationMode;
        this.placementX = placementX;
        this.placementY = placementY;
        this.source = source == null ? "" : source;
        cursor = this.source.length();
        highlightedLines = LuaSyntaxHighlighter.highlight(this.source);
        BuiltinEndpoints.register();
        session.sourceChanged(this.source, net.minecraft.Util.getMillis() - LuaEditorSession.DEBOUNCE_MILLIS);
        updateCompilation(net.minecraft.Util.getMillis());
    }

    @Override
    public void tick() {
        updateCompilation(net.minecraft.Util.getMillis());
    }

    @Override
    protected void init() {
        centerPreviewNode();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ComputedEditorTheme.BACKGROUND_PRIMARY);
        int divider = Math.max(300, width * 3 / 5);
        graphics.fill(divider, 0, divider + 1, height, ComputedEditorTheme.BORDER_MENU);
        renderHeader(graphics, divider, mouseX, mouseY);
        renderSource(graphics, divider);
        renderPreview(graphics, divider, mouseX, mouseY, partialTick);
        renderDiagnostics(graphics, divider);
        if (completionOpen) {
            renderCompletion(graphics, divider);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // This screen supplies its own opaque background. The vanilla pass would
        // apply the in-world menu blur after the editor has already been drawn.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        int divider = Math.max(300, width * 3 / 5);
        if (contains(mouseX, mouseY, divider - 292, 5, 42, 18)) {
            minecraft.keyboardHandler.setClipboard(source);
            status = "Lua source copied";
            return true;
        }
        if (contains(mouseX, mouseY, divider - 247, 5, 42, 18)) {
            loadClipboard();
            return true;
        }
        if (contains(mouseX, mouseY, divider - 202, 5, 42, 18)) {
            exportFile();
            return true;
        }
        if (contains(mouseX, mouseY, divider - 157, 5, 42, 18)) {
            importFile();
            return true;
        }
        if (contains(mouseX, mouseY, divider - 112, 5, 50, 18)) {
            resetPreview();
            return true;
        }
        if (contains(mouseX, mouseY, divider - 59, 5, 53, 18)) {
            apply();
            return true;
        }
        if (contains(mouseX, mouseY, 0, 26, divider, Math.max(0, height - 41))) {
            sourceFocused = true;
            completionOpen = false;
            int line = firstLine + Math.max(0, ((int) mouseY - 31) / 11);
            String[] lines = source.split("\n", -1);
            int clampedLine = Mth.clamp(line, 0, Math.max(0, lines.length - 1));
            cursor = offsetAtLine(
                    clampedLine,
                    columnAtPixel(lines[clampedLine], (int) mouseX - 42 + horizontalScroll));
            return true;
        }
        sourceFocused = false;
        return handleSampleClick(mouseX, mouseY, divider);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        int divider = Math.max(300, width * 3 / 5);
        if (mouseX < divider) {
            if (hasShiftDown() || scrollX != 0) {
                double amount = scrollX != 0 ? scrollX : scrollY;
                horizontalScroll = Mth.clamp(
                        horizontalScroll - (int) Math.signum(amount) * 24,
                        0,
                        maximumHorizontalScroll(divider));
                return true;
            }
            int lines = source.split("\n", -1).length;
            int visible = Math.max(1, (height - 62) / 11);
            firstLine = Mth.clamp(
                    firstLine - (int) Math.signum(scrollY) * 3,
                    0,
                    Math.max(0, lines - visible));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (completionOpen) {
                completionOpen = false;
            } else {
                onClose();
            }
            return true;
        }
        if (!sourceFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_ENTER) {
            apply();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_SPACE) {
            completionOpen = true;
            completionIndex = 0;
            return true;
        }
        if (completionOpen && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            completionIndex = Math.floorMod(
                    completionIndex + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1),
                    COMPLETIONS.size());
            return true;
        }
        if (completionOpen && keyCode == GLFW.GLFW_KEY_ENTER) {
            insert(COMPLETIONS.get(completionIndex));
            completionOpen = false;
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            cursor = source.length();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            minecraft.keyboardHandler.setClipboard(source);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            insert(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            cursor = Math.max(0, cursor - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            cursor = Math.min(source.length(), cursor + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            moveVertical(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursor = lineStart(cursor);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            cursor = lineEnd(cursor);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cursor > 0) {
            source = source.substring(0, cursor - 1) + source.substring(cursor);
            cursor--;
            changed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && cursor < source.length()) {
            source = source.substring(0, cursor) + source.substring(cursor + 1);
            changed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            insert("\n" + indentation());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            insert("    ");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (sourceFocused
                && !Character.isISOControl(codePoint)
                && source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 65_536) {
            insert(Character.toString(codePoint));
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void renderHeader(GuiGraphics graphics, int divider, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, 26, ComputedEditorTheme.BACKGROUND_SECONDARY);
        graphics.hLine(0, width, 25, ComputedEditorTheme.BORDER_MENU);
        graphics.drawString(font, "Lua Source", 8, 9, ComputedEditorTheme.TEXT_HEADER, false);
        button(graphics, divider - 292, 5, 42, "Copy", mouseX, mouseY, false);
        button(graphics, divider - 247, 5, 42, "Paste", mouseX, mouseY, false);
        button(graphics, divider - 202, 5, 42, "Save", mouseX, mouseY, false);
        button(graphics, divider - 157, 5, 42, "File", mouseX, mouseY, false);
        button(graphics, divider - 112, 5, 50, "Reset", mouseX, mouseY, false);
        button(
                graphics,
                divider - 59,
                5,
                53,
                replacementConfirmation ? "Replace" : "Apply",
                mouseX,
                mouseY,
                replacementConfirmation);
        graphics.drawString(font, "Live Preview", divider + 8, 9, ComputedEditorTheme.TEXT_HEADER, false);
    }

    private void renderSource(GuiGraphics graphics, int divider) {
        String[] lines = source.split("\n", -1);
        int visible = Math.max(1, (height - 62) / 11);
        int cursorLine = lineOf(cursor);
        int cursorColumn = cursor - lineStart(cursor);
        ensureCursorVisible(divider, lines, cursorLine, cursorColumn);
        for (int lineIndex = firstLine;
                lineIndex < Math.min(lines.length, firstLine + visible);
                lineIndex++) {
            int y = 31 + (lineIndex - firstLine) * 11;
            graphics.drawString(
                    font,
                    Integer.toString(lineIndex + 1),
                    4,
                    y,
                    lineIndex == cursorLine
                            ? ComputedEditorTheme.ACCENT
                            : ComputedEditorTheme.TEXT_TERTIARY,
                    false);
        }
        graphics.enableScissor(37, 26, divider, height - 15);
        for (int lineIndex = firstLine;
                lineIndex < Math.min(lines.length, firstLine + visible);
                lineIndex++) {
            int y = 31 + (lineIndex - firstLine) * 11;
            String line = lines[lineIndex];
            int x = 42 - horizontalScroll;
            List<LuaSyntaxHighlighter.Span> spans = lineIndex < highlightedLines.size()
                    ? highlightedLines.get(lineIndex)
                    : List.of(new LuaSyntaxHighlighter.Span(line, LuaSyntaxHighlighter.DEFAULT));
            for (LuaSyntaxHighlighter.Span span : spans) {
                graphics.drawString(font, span.text(), x, y, span.color(), false);
                x += font.width(span.text());
            }
            if (sourceFocused
                    && lineIndex == cursorLine
                    && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = 42
                        - horizontalScroll
                        + font.width(line.substring(0, Math.min(cursorColumn, line.length())));
                graphics.vLine(cursorX, y - 1, y + 9, ComputedEditorTheme.TEXT_HEADER);
            }
        }
        graphics.disableScissor();
        graphics.vLine(36, 26, height - 1, ComputedEditorTheme.BORDER_SUBTLE);
        String help = signatureHelp();
        if (!help.isEmpty()) {
            graphics.fill(42, height - 30, divider - 6, height - 16, 0xEE1A1A1A);
            graphics.drawString(font, help, 47, height - 27, ComputedEditorTheme.TEXT_SECONDARY, false);
        }
    }

    private void renderPreview(
            GuiGraphics graphics,
            int divider,
            int mouseX,
            int mouseY,
            float partialTick) {
        int previewX = divider + 12;
        int previewY = 38;
        int previewWidth = width - divider - 24;
        int previewHeight = Math.max(80, height / 2 - 42);
        graphics.fill(
                previewX,
                previewY,
                previewX + previewWidth,
                previewY + previewHeight,
                0xFF0B0E10);
        graphics.renderOutline(
                previewX,
                previewY,
                previewWidth,
                previewHeight,
                ComputedEditorTheme.BORDER_DEFAULT);
        if (previewNode != null) {
            graphics.enableScissor(
                    previewX + 1,
                    previewY + 1,
                    previewX + previewWidth - 1,
                    previewY + previewHeight - 1);
            previewNode.render(graphics, mouseX, mouseY, partialTick);
            graphics.disableScissor();
        }
        if (session.snapshot().stalePreview()) {
            graphics.fill(
                    previewX + 1,
                    previewY + 1,
                    previewX + previewWidth - 1,
                    previewY + previewHeight - 1,
                    0x66000000);
            graphics.drawCenteredString(
                    font,
                    "STALE PREVIEW",
                    previewX + previewWidth / 2,
                    previewY + previewHeight - 15,
                    ComputedEditorTheme.STATUS_WARNING_TEXT);
        }
        renderSamples(graphics, divider, previewY + previewHeight + 12, mouseX, mouseY);
    }

    private void renderSamples(
            GuiGraphics graphics,
            int divider,
            int startY,
            int mouseX,
            int mouseY) {
        int x = divider + 12;
        graphics.drawString(font, "Sample Inputs and Fields", x, startY, ComputedEditorTheme.TEXT_HEADER, false);
        int row = 0;
        for (Map.Entry<String, LuaValue> entry : sampleInputs.entrySet()) {
            sampleRow(graphics, x, startY + 14 + row++ * 15, "Input", entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, LuaValue> entry : sampleFields.entrySet()) {
            sampleRow(graphics, x, startY + 14 + row++ * 15, "Field", entry.getKey(), entry.getValue());
        }
        if (previewDefinition != null) {
            for (String event : previewDefinition.eventHandlers().keySet()) {
                int y = startY + 14 + row++ * 15;
                graphics.fill(x, y, Math.min(width - 12, x + 160), y + 13, ComputedEditorTheme.BUTTON_BACKGROUND);
                graphics.drawString(font, "Emit " + event, x + 4, y + 3, ComputedEditorTheme.TEXT_PRIMARY, false);
            }
        }
    }

    private void sampleRow(
            GuiGraphics graphics,
            int x,
            int y,
            String kind,
            String id,
            LuaValue value) {
        graphics.fill(x, y, width - 12, y + 13, ComputedEditorTheme.BACKGROUND_TERTIARY);
        graphics.drawString(
                font,
                kind + "  " + id + " = " + display(value),
                x + 4,
                y + 3,
                ComputedEditorTheme.TEXT_PRIMARY,
                false);
    }

    private void renderDiagnostics(GuiGraphics graphics, int divider) {
        int y = height - 15;
        graphics.fill(0, y, width, height, ComputedEditorTheme.BACKGROUND_SECONDARY);
        graphics.hLine(0, width, y, ComputedEditorTheme.BORDER_MENU);
        var diagnostics = session.snapshot().diagnostics();
        String message = diagnostics.isEmpty()
                ? status.isEmpty() ? "Valid Lua definition" : status
                : diagnostics.getFirst().message();
        int color = diagnostics.isEmpty()
                ? ComputedEditorTheme.ACCENT_MUTED
                : ComputedEditorTheme.STATUS_ERROR_TEXT;
        graphics.drawString(font, message, 6, y + 4, color, false);
    }

    private void renderCompletion(GuiGraphics graphics, int divider) {
        int shown = Math.min(7, COMPLETIONS.size());
        int x = 48;
        int y = Math.min(height - shown * 14 - 34, 54 + (lineOf(cursor) - firstLine) * 11);
        int width = Math.min(divider - x - 8, 330);
        graphics.fill(x, y, x + width, y + shown * 14 + 2, 0xFA151515);
        graphics.renderOutline(x, y, width, shown * 14 + 2, ComputedEditorTheme.BORDER_MENU);
        int first = Mth.clamp(completionIndex - shown / 2, 0, COMPLETIONS.size() - shown);
        for (int index = first; index < first + shown; index++) {
            int rowY = y + 1 + (index - first) * 14;
            if (index == completionIndex) {
                graphics.fill(x + 1, rowY, x + width - 1, rowY + 14, ComputedEditorTheme.MENU_SELECTED);
            }
            graphics.drawString(
                    font,
                    COMPLETIONS.get(index),
                    x + 5,
                    rowY + 3,
                    ComputedEditorTheme.TEXT_PRIMARY,
                    false);
        }
    }

    private void updateCompilation(long now) {
        if (!session.update(now)) {
            return;
        }
        var snapshot = session.snapshot();
        if (snapshot.currentDefinition() == null) {
            status = "Preview retained from the last valid definition";
            return;
        }
        previewDefinition = snapshot.currentDefinition();
        fileDefinitionId = previewDefinition.id();
        sampleInputs.clear();
        previewDefinition.inputs().forEach(input -> sampleInputs.put(input.id(), input.defaultValue()));
        sampleFields.clear();
        previewDefinition.fields().forEach(field -> sampleFields.put(field.id(), field.defaultValue()));
        try {
            preview = new LuaLivePreview(source);
            sampleInputs.forEach(preview::setInput);
            sampleFields.forEach(preview::setField);
            preview.run();
            LuaDefinitionSource definition =
                    LuaDefinitionSource.embedded(1, previewDefinition.id(), source);
            ComputedProgramV3 previewProgram = new ComputedProgramV3(
                    0,
                    new ComputedGraph(UUID.randomUUID(), List.of(), List.of()),
                    Map.of(definition.id(), definition),
                    Map.of(),
                    null);
            previewNode = LuaEditorGraphAdapter.createEditorNode(
                    previewProgram,
                    definition.id(),
                    0,
                    0);
            centerPreviewNode();
            status = "Preview updated";
            replacementConfirmation = false;
        } catch (RuntimeException exception) {
            status = "Preview unavailable: " + exception.getMessage();
        }
    }

    private void apply() {
        var snapshot = session.snapshot();
        LuaNodeDefinition definition = snapshot.currentDefinition();
        if (definition == null) {
            status = "Fix diagnostics before applying";
            return;
        }
        LuaDefinitionSource replacement = LuaDefinitionSource.embedded(1, definition.id(), source);
        String existingHash = parent.definitionHash(definition.id());
        if (!existingHash.isEmpty()
                && !existingHash.equals(replacement.hash())
                && !replacementConfirmation) {
            replacementConfirmation = true;
            status = "Click Replace to confirm the changed definition";
            return;
        }
        parent.applyLuaSource(
                source,
                definition.id(),
                creationMode,
                placementX,
                placementY);
        status = "Applied; awaiting authoritative autosave";
        minecraft.setScreen(parent);
    }

    private void resetPreview() {
        if (preview == null) {
            return;
        }
        try {
            preview.reset(source);
            sampleInputs.forEach(preview::setInput);
            sampleFields.forEach(preview::setField);
            preview.run();
            status = "Preview state reset";
        } catch (RuntimeException exception) {
            status = "Reset failed: " + exception.getMessage();
        }
    }

    private boolean handleSampleClick(double mouseX, double mouseY, int divider) {
        int previewY = 38;
        int previewHeight = Math.max(80, height / 2 - 42);
        int startY = previewY + previewHeight + 12;
        if (mouseX < divider + 12 || mouseY < startY + 14) {
            return false;
        }
        int row = ((int) mouseY - startY - 14) / 15;
        int index = 0;
        for (Map.Entry<String, LuaValue> entry : sampleInputs.entrySet()) {
            if (index++ == row) {
                LuaValue value = nextValue(entry.getValue());
                entry.setValue(value);
                preview.setInput(entry.getKey(), value);
                preview.run();
                return true;
            }
        }
        for (Map.Entry<String, LuaValue> entry : sampleFields.entrySet()) {
            if (index++ == row) {
                LuaValue value = nextValue(entry.getValue());
                entry.setValue(value);
                preview.setField(entry.getKey(), value);
                preview.run();
                return true;
            }
        }
        if (previewDefinition != null) {
            for (String event : previewDefinition.eventHandlers().keySet()) {
                if (index++ == row) {
                    preview.event(event, LuaValue.ONE);
                    return true;
                }
            }
        }
        return false;
    }

    private void replaceSource(String replacement) {
        source = replacement == null ? "" : replacement;
        cursor = source.length();
        changed();
    }

    private void loadClipboard() {
        try {
            replaceSource(LuaDefinitionClipboard.importSource(minecraft.keyboardHandler.getClipboard()));
            status = "Lua source loaded from clipboard";
        } catch (IllegalArgumentException exception) {
            status = exception.getMessage();
        }
    }

    private void exportFile() {
        var snapshot = session.snapshot();
        LuaNodeDefinition definition = snapshot.currentDefinition();
        if (definition == null) {
            status = "Fix diagnostics before exporting";
            return;
        }
        try {
            var sourceDefinition = LuaDefinitionSource.embedded(1, definition.id(), source);
            var path = LuaDefinitionFiles.export(
                    net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(),
                    sourceDefinition);
            fileDefinitionId = definition.id();
            status = "Saved " + path.getFileName();
        } catch (java.io.IOException | RuntimeException exception) {
            status = "File export failed: " + exception.getMessage();
        }
    }

    private void importFile() {
        if (fileDefinitionId.isBlank()) {
            status = "Compile a definition before loading its file";
            return;
        }
        String fileName = fileDefinitionId.replace(':', '_').replace('/', '_') + ".lua";
        try {
            replaceSource(LuaDefinitionFiles.importSource(
                    net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(),
                    fileName));
            status = "Loaded " + fileName;
        } catch (java.io.IOException | RuntimeException exception) {
            status = "File import failed: " + exception.getMessage();
        }
    }

    private void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        source = source.substring(0, cursor) + text + source.substring(cursor);
        cursor += text.length();
        changed();
    }

    private void changed() {
        replacementConfirmation = false;
        completionOpen = false;
        highlightedLines = LuaSyntaxHighlighter.highlight(source);
        session.sourceChanged(source, net.minecraft.Util.getMillis());
    }

    private void centerPreviewNode() {
        if (previewNode == null || width <= 0 || height <= 0) {
            return;
        }
        int divider = Math.max(300, width * 3 / 5);
        int previewX = divider + 12;
        int previewY = 38;
        int previewWidth = width - divider - 24;
        int previewHeight = Math.max(80, height / 2 - 42);
        previewNode.ensureLayoutUpToDate();
        previewNode.setPos(
                previewX + (previewWidth - previewNode.getWidth()) / 2,
                previewY + (previewHeight - previewNode.getHeight()) / 2);
    }

    private void ensureCursorVisible(
            int divider,
            String[] lines,
            int cursorLine,
            int cursorColumn) {
        if (cursorLine < 0 || cursorLine >= lines.length) {
            return;
        }
        String line = lines[cursorLine];
        int prefixWidth =
                font.width(line.substring(0, Math.min(cursorColumn, line.length())));
        int viewportWidth = Math.max(1, divider - 50);
        if (prefixWidth - horizontalScroll < 0) {
            horizontalScroll = prefixWidth;
        } else if (prefixWidth - horizontalScroll > viewportWidth) {
            horizontalScroll = prefixWidth - viewportWidth;
        }
        horizontalScroll = Mth.clamp(horizontalScroll, 0, maximumHorizontalScroll(divider));
    }

    private int maximumHorizontalScroll(int divider) {
        int widest = 0;
        for (String line : source.split("\n", -1)) {
            widest = Math.max(widest, font.width(line));
        }
        return Math.max(0, widest - Math.max(1, divider - 50));
    }

    private int columnAtPixel(String line, int targetPixel) {
        if (targetPixel <= 0) {
            return 0;
        }
        int width = 0;
        for (int index = 0; index < line.length(); index++) {
            int characterWidth = font.width(line.substring(index, index + 1));
            if (targetPixel < width + characterWidth / 2) {
                return index;
            }
            width += characterWidth;
        }
        return line.length();
    }

    private void moveVertical(int direction) {
        int column = cursor - lineStart(cursor);
        int target = lineOf(cursor) + direction;
        if (target < 0) {
            cursor = 0;
            return;
        }
        String[] lines = source.split("\n", -1);
        if (target >= lines.length) {
            cursor = source.length();
            return;
        }
        cursor = offsetAtLine(target, Math.min(column, lines[target].length()));
        int visible = Math.max(1, (height - 62) / 11);
        firstLine = Mth.clamp(firstLine, Math.max(0, target - visible + 1), target);
    }

    private int offsetAtLine(int targetLine, int column) {
        String[] lines = source.split("\n", -1);
        targetLine = Mth.clamp(targetLine, 0, Math.max(0, lines.length - 1));
        int offset = 0;
        for (int line = 0; line < targetLine; line++) {
            offset += lines[line].length() + 1;
        }
        return Math.min(source.length(), offset + Math.min(column, lines[targetLine].length()));
    }

    private int lineOf(int offset) {
        int line = 0;
        for (int index = 0; index < Math.min(offset, source.length()); index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int lineStart(int offset) {
        int newline = source.lastIndexOf('\n', Math.max(0, offset - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    private int lineEnd(int offset) {
        int newline = source.indexOf('\n', offset);
        return newline < 0 ? source.length() : newline;
    }

    private String indentation() {
        int start = lineStart(cursor);
        int end = start;
        while (end < source.length() && source.charAt(end) == ' ') {
            end++;
        }
        return source.substring(start, end);
    }

    private String signatureHelp() {
        int start = Math.max(0, cursor - 80);
        String prefix = source.substring(start, cursor);
        if (prefix.contains("ctx:endpoint")) {
            return "ctx:endpoint(id, target?) → safe endpoint proxy";
        }
        if (prefix.contains("node:input")) {
            return "node:input(id, type, options?)";
        }
        if (prefix.contains("node:field")) {
            return "node:field(id, fieldType, { default, min, max, choices, control, step, label })";
        }
        if (prefix.contains("ctx:output")) {
            return "ctx:output(id, value)";
        }
        return "";
    }

    private static LuaValue nextValue(LuaValue value) {
        if (value.isboolean()) {
            return LuaValue.valueOf(!value.toboolean());
        }
        if (value.isnumber()) {
            return LuaValue.valueOf(value.todouble() + 1);
        }
        if (value.isstring()) {
            return LuaValue.valueOf(value.tojstring() + "*");
        }
        return value;
    }

    private static String display(LuaValue value) {
        if (value == null || value.isnil()) {
            return "nil";
        }
        return value.tojstring();
    }

    private static void button(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String label,
            int mouseX,
            int mouseY,
            boolean danger) {
        boolean hovered = contains(mouseX, mouseY, x, y, width, 18);
        int color = danger
                ? hovered ? ComputedEditorTheme.DANGER_HOVER : ComputedEditorTheme.DANGER_BACKGROUND
                : hovered ? ComputedEditorTheme.BUTTON_HOVER : ComputedEditorTheme.BUTTON_BACKGROUND;
        graphics.fill(x, y, x + width, y + 18, color);
        graphics.renderOutline(x, y, width, 18, ComputedEditorTheme.BORDER_MENU);
        graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                label,
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
}
