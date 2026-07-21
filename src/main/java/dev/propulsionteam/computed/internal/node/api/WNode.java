package dev.propulsionteam.computed.internal.node.api;

import dev.propulsionteam.computed.api.node.ExecutionPolicy;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an individual node within the graph.
 * A node can have inputs, outputs, UI elements, and a custom evaluation logic.
 */
public class WNode {
    private static final int PIN_SIZE = 5;
    private static final int PIN_HOVER_SIZE = 7;

    private UUID id;
    private final ResourceLocation typeId;
    private String title;
    private int x, y;
    private int width = 120;
    private int height = 40;

    private final List<WElement> elements = new ArrayList<>();
    private final List<WPin> inputs = new ArrayList<>();
    private final List<WPin> outputs = new ArrayList<>();
    private Evaluator evaluator = (node) -> {};
    private int topoDepth = 0;
    private boolean selected = false;
    /** Cached layout left-margin (input-label area + padding); recomputed in {@link #updateLayout()}. */
    private int leftMargin = 5;
    /** True when pins / elements / title changed since last {@link #updateLayout()}. */
    private boolean layoutDirty = true;
    /** Monotonic schema generation used by WGraph to remap stable connection keys lazily. */
    private long pinSchemaRevision;
    private transient WGraph owningGraph;

    /** Called by {@link WElement#markLayoutDirty()} when a child element's measured size changes. */
    public void markLayoutDirty() {
        this.layoutDirty = true;
    }

    public void markPinSchemaChanged() {
        pinSchemaRevision++;
        layoutDirty = true;
        if (owningGraph != null) owningGraph.onNodePinSchemaChanged(this);
    }

    public long getPinSchemaRevision() {
        return pinSchemaRevision;
    }

    void bindOwningGraph(WGraph graph) {
        owningGraph = graph;
    }

    private void ensureLayout() {
        if (layoutDirty) {
            updateLayout();
            layoutDirty = false;
        }
    }

    /** Makes bounds current without rendering this node or any of its elements. */
    public final void ensureLayoutUpToDate() {
        ensureLayout();
    }
    /** Non-null only while {@link #evaluate()} runs as part of a {@link WGraph} step. */
    private transient WGraph evaluationGraph;

    /**
     * Interface for custom node behavior.
     * The evaluate method is called every graph tick.
     */
    public interface Evaluator {
        void evaluate(WNode node);
    }

    /**
     * Sets the custom logic for this node.
     * @param evaluator A lambda or class implementing the logic.
     */
    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Executes the node's custom logic.
     */
    public void evaluate() {
        this.evaluator.evaluate(this);
    }

    /** @see WGraph#isEvalTickPulseGate() */
    public WGraph evaluationGraph() {
        return evaluationGraph;
    }

    void bindEvaluationGraph(WGraph graph) {
        this.evaluationGraph = graph;
    }

    /**
     * Creates a new node instance.
     * @param typeId Unique identifier for the node type.
     * @param title Display title of the node.
     * @param x Initial X coordinate in logical space.
     * @param y Initial Y coordinate in logical space.
     */
    public WNode(ResourceLocation typeId, String title, int x, int y) {
        this.id = UUID.randomUUID();
        this.typeId = typeId;
        this.title = title;
        this.x = x;
        this.y = y;
    }

    /**
     * Adds a UI element (slider, button, text field, etc.) to the node body.
     * @param element The element to add.
     */
    public void addElement(WElement element) {
        this.elements.add(element);
        element.parent = this;
        layoutDirty = true;
    }

    /**
     * Adds an input pin to the left side of the node.
     * @param name Name of the input.
     * @param color Display color of the pin.
     */
    public void addInput(String name, int color) {
        WPin pin = new WPin(name, WPin.Type.INPUT, color);
        this.inputs.add(pin);
        markPinSchemaChanged();
    }

    /** Typed input pin. */
    public void addInput(String name, WPin.DataType dataType, int color) {
        this.inputs.add(new WPin(name, WPin.Type.INPUT, dataType, color));
        markPinSchemaChanged();
    }

    /** Typed input with an explicit stable persistence key. */
    public void addInput(String stableKey, String name, WPin.DataType dataType, int color) {
        this.inputs.add(new WPin(stableKey, name, WPin.Type.INPUT, dataType, color));
        markPinSchemaChanged();
    }

    /**
     * Adds an output pin to the right side of the node.
     * @param name Name of the output.
     * @param color Display color of the pin.
     */
    public void addOutput(String name, int color) {
        WPin pin = new WPin(name, WPin.Type.OUTPUT, color);
        this.outputs.add(pin);
        markPinSchemaChanged();
    }

    /** Typed output pin. */
    public void addOutput(String name, WPin.DataType dataType, int color) {
        this.outputs.add(new WPin(name, WPin.Type.OUTPUT, dataType, color));
        markPinSchemaChanged();
    }

    /** Typed output with an explicit stable persistence key. */
    public void addOutput(String stableKey, String name, WPin.DataType dataType, int color) {
        this.outputs.add(new WPin(stableKey, name, WPin.Type.OUTPUT, dataType, color));
        markPinSchemaChanged();
    }

    /**
     * Recalculates the node's dimensions based on its pins, elements, and title.
     * Automatically adjusts the width and height for a clean look.
     */
    private static int measureTextWidth(String text) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return Math.max(8, text.length() * 6);
        }
        return net.minecraft.client.Minecraft.getInstance().font.width(text);
    }

    public void updateLayout() {
        int headerHeight = 16;
        int maxInputLabelWidth = 0;
        for (WPin pin : inputs) {
            maxInputLabelWidth = Math.max(maxInputLabelWidth, measureTextWidth(pin.getName()));
        }
        
        int maxOutputLabelWidth = 0;
        for (WPin pin : outputs) {
            maxOutputLabelWidth = Math.max(maxOutputLabelWidth, measureTextWidth(pin.getName()));
        }

        this.leftMargin = maxInputLabelWidth > 0 ? maxInputLabelWidth + 15 : 5;
        int rightMargin = maxOutputLabelWidth > 0 ? maxOutputLabelWidth + 15 : 5;

        int bodyWidth = 80;
        int bodyHeight = 0;
        for (WElement element : elements) {
            bodyWidth = Math.max(bodyWidth, element.getWidth());
            bodyHeight += element.getHeight();
        }

        int titleWidth = measureTextWidth(title) + 20;
        this.width = Math.max(titleWidth, this.leftMargin + bodyWidth + rightMargin);
        
        int pinAreaHeight = Math.max(inputs.size(), outputs.size()) * 12;
        this.height = headerHeight + Math.max(bodyHeight, pinAreaHeight) + 5;
    }

    /**
     * Renders the node, its pins, and all internal elements.
     * @param graphics The GuiGraphics context.
     * @param mouseX Transformed mouse X coordinate.
     * @param mouseY Transformed mouse Y coordinate.
     * @param partialTick Animation frame fraction.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureLayout();
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        graphics.fill(x, y, x + width, y + height, ComputedEditorTheme.nodeBody(isHovered, selected, false));

        // Pathmind-style tinted header with Computed's established green identity.
        graphics.fill(x + 1, y + 1, x + width - 1, y + 14, ComputedEditorTheme.ACCENT_HEADER);

        int borderCol = selected
                ? ComputedEditorTheme.TEXT_HEADER
                : (isHovered ? ComputedEditorTheme.BORDER_HIGHLIGHT : ComputedEditorTheme.ACCENT);
        graphics.renderOutline(x, y, width, height, borderCol);

        graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                title,
                x + 5,
                y + 3,
                ComputedEditorTheme.TEXT_HEADER,
                false);

        // Render elements
        int currentY = y + 20;
        for (WElement element : elements) {
            element.render(graphics, x + this.leftMargin, currentY, mouseX, mouseY, partialTick);
            currentY += element.getHeight();
        }

        // Render pins
        for (int i = 0; i < inputs.size(); i++) {
            renderPin(graphics, x - 4, y + 18 + i * 12, inputs.get(i), true, mouseX, mouseY);
        }
        for (int i = 0; i < outputs.size(); i++) {
            renderPin(graphics, x + width - 1, y + 18 + i * 12, outputs.get(i), false, mouseX, mouseY);
        }
    }

    /**
     * Internal method to render a single pin with hover effects and labels.
     */
    private void renderPin(GuiGraphics graphics, int px, int py, WPin pin, boolean isInput, int mouseX, int mouseY) {
        int color = pin.getColor();
        boolean hover = mouseX >= px - 1 && mouseX <= px + PIN_SIZE && mouseY >= py - 1 && mouseY <= py + PIN_SIZE;
        int size = hover ? PIN_HOVER_SIZE : PIN_SIZE;
        int left = px - (size - PIN_SIZE) / 2;
        int top = py - (size - PIN_SIZE) / 2;

        graphics.fill(
                left,
                top,
                left + size,
                top + size,
                pin.isConnected() || hover ? color : (color & 0x66FFFFFF));
        graphics.renderOutline(
                left,
                top,
                size,
                size,
                hover ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.SOCKET_BORDER);
        int centerLeft = left + Math.max(1, size / 2 - 1);
        int centerTop = top + Math.max(1, size / 2 - 1);
        graphics.fill(centerLeft, centerTop, centerLeft + 2, centerTop + 2, ComputedEditorTheme.SOCKET_CENTER);

        // Pin Label
        String name = pin.getName();
        int tx = isInput ? px + 8 : px - 4 - measureTextWidth(name);
        graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                name,
                tx,
                py - 2,
                ComputedEditorTheme.TEXT_SECONDARY,
                false);
    }

    /**
     * Checks if a specific screen position hits a pin on this node.
     * @param px Local X coordinate.
     * @param py Local Y coordinate.
     * @param isInput True to check inputs, false for outputs.
     * @return The index of the pin hit, or -1 if none.
     */
    public int getPinAt(int px, int py, boolean isInput) {
        int startX = isInput ? -4 : width - 1;
        List<WPin> list = isInput ? inputs : outputs;
        for (int i = 0; i < list.size(); i++) {
            int rx = startX;
            int ry = 18 + i * 12;
            if (px >= rx - 1 && px <= rx + PIN_SIZE && py >= ry - 1 && py <= ry + PIN_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Forwards mouse click events to internal UI elements.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ensureLayout();
        boolean handled = false;
        int currentY = 20;
        for (WElement element : new ArrayList<>(elements)) {
            if (element.handleMouseClick(mouseX - this.leftMargin, mouseY - currentY, button)) {
                handled = true;
            }
            currentY += element.getHeight();
        }
        return handled;
    }

    /**
     * Forwards mouse release events to internal UI elements.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ensureLayout();
        int currentY = 20;
        for (WElement element : new ArrayList<>(elements)) {
            element.handleMouseRelease(mouseX - this.leftMargin, mouseY - currentY, button);
            currentY += element.getHeight();
        }
        return false;
    }

    /**
     * Forwards key press events to internal UI elements.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (WElement element : new ArrayList<>(elements)) {
            if (element.handleKeyPress(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    /**
     * Forwards character typing events to internal UI elements.
     */
    public boolean charTyped(char codePoint, int modifiers) {
        for (WElement element : new ArrayList<>(elements)) {
            if (element.handleCharTyped(codePoint, modifiers)) return true;
        }
        return false;
    }

    // Standard Getters and Setters
    public List<WElement> getElements() { return elements; }
    public boolean hasFocusedElement() {
        for (WElement element : new ArrayList<>(elements)) {
            if (element.isFocused()) {
                return true;
            }
        }
        return false;
    }
    public List<WPin> getInputs() { return inputs; }
    public List<WPin> getOutputs() { return outputs; }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("typeId", typeId.toString());
        tag.putString("id", id.toString());
        tag.putString("title", title);
        tag.putInt("x", x);
        tag.putInt("y", y);
        
        net.minecraft.nbt.ListTag inputsTag = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < inputs.size(); i++) {
            WPin pin = inputs.get(i);
            if (pin.getStableKey() == null) pin.setStableKey(stablePortId(inputs, i, "input"));
            inputsTag.add(pin.save());
        }
        tag.put("inputs", inputsTag);
        
        net.minecraft.nbt.ListTag outputsTag = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < outputs.size(); i++) {
            WPin pin = outputs.get(i);
            if (pin.getStableKey() == null) pin.setStableKey(stablePortId(outputs, i, "output"));
            outputsTag.add(pin.save());
        }
        tag.put("outputs", outputsTag);
        
        net.minecraft.nbt.ListTag elementsTag = new net.minecraft.nbt.ListTag();
        for (WElement el : elements) elementsTag.add(el.save());
        tag.put("elements", elementsTag);
        
        return tag;
    }

    static String stablePortId(List<WPin> pins, int index, String direction) {
        String explicit = pins.get(index).getStableKey();
        if (explicit != null && !explicit.isBlank()) return explicit;
        String label = pins.get(index).getName().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^[^a-z]+", "")
                .replaceAll("_+$", "");
        if (label.isEmpty()) label = "port";
        String base = direction + "." + label;
        int duplicate = 0;
        for (int i = 0; i <= index; i++) {
            String otherExplicit = pins.get(i).getStableKey();
            if (base.equals(otherExplicit)) {
                duplicate++;
                continue;
            }
            String other = pins.get(i).getName().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]+", "_")
                    .replaceAll("^[^a-z]+", "")
                    .replaceAll("_+$", "");
            if (other.isEmpty()) other = "port";
            if (other.equals(label)) duplicate++;
        }
        return duplicate <= 1 ? base : base + "." + duplicate;
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("id")) {
            this.id = UUID.fromString(tag.getString("id"));
        }
        if (tag.contains("title")) {
            this.title = tag.getString("title");
        }
        this.x = tag.getInt("x");
        this.y = tag.getInt("y");
        
        net.minecraft.nbt.ListTag inputsTag = tag.getList("inputs", 10);
        for (int i = 0; i < Math.min(inputs.size(), inputsTag.size()); i++) inputs.get(i).load(inputsTag.getCompound(i));
        
        net.minecraft.nbt.ListTag outputsTag = tag.getList("outputs", 10);
        for (int i = 0; i < Math.min(outputs.size(), outputsTag.size()); i++) outputs.get(i).load(outputsTag.getCompound(i));
        
        net.minecraft.nbt.ListTag elementsTag = tag.getList("elements", 10);
        for (int i = 0; i < Math.min(elements.size(), elementsTag.size()); i++) elements.get(i).load(elementsTag.getCompound(i));
        layoutDirty = true;
    }

    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }

    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }

    public UUID getId() { return id; }
    public ResourceLocation getTypeId() { return typeId; }
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
        layoutDirty = true;
    }

    /** When true, editor actions cannot remove this node (selection delete, section bulk delete, etc.). */
    public boolean isDeletionLocked() {
        return false;
    }

    /** When true, duplicate / clipboard duplicate skips this node. */
    public boolean isDuplicationLocked() {
        return false;
    }
    public void clearElementFocus() {
        for (WElement element : new ArrayList<>(elements)) {
            element.clearFocus();
        }
    }

    /**
     * A state boundary publishes prior-step state and therefore breaks combinational dependency cycles.
     * Stateful built-ins and data-driven nodes override this in the rewritten runtime.
     */
    public boolean isStateBoundary() {
        return false;
    }

    /** Scheduling policy used by the dirty-propagation runtime. */
    public ExecutionPolicy executionPolicy() {
        return ExecutionPolicy.INPUT_DRIVEN;
    }

    public boolean isMissingType() {
        return false;
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setPos(int x, int y) { this.x = x; this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTopoDepth() { return topoDepth; }
    public void setTopoDepth(int depth) { this.topoDepth = depth; }
}
