package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.client.renderer.node.BedrockNodeRenderer;
import dev.propulsionteam.computed.client.renderer.node.NodeRenderLayout;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.NodeStyle;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class LuaEditorNode extends WNode {
    private final GraphNode source;
    private final boolean stateBoundary;
    private final String category;
    private final NodeStyle style;
    private final LuaNodeDefinition definition;
    private final List<LuaNodeFieldControl> fieldControls = new ArrayList<>();
    private final LuaStateCodec fieldCodec = new LuaStateCodec();

    public LuaEditorNode(
            GraphNode source,
            String title,
            boolean stateBoundary,
            String category,
            NodeStyle style,
            LuaNodeDefinition definition) {
        super(ResourceLocation.parse(source.definitionId()), title, source.x(), source.y());
        this.source = source;
        this.stateBoundary = stateBoundary;
        this.category = category == null ? "utility" : category;
        this.style = style == null ? NodeStyle.STANDARD : style;
        this.definition = definition;
        for (PortSnapshot port : source.ports()) {
            WPin.DataType dataType = dataType(port.type());
            int color = color(port.type());
            if (port.direction() == PortDirection.INPUT) {
                addInput(port.id(), port.label(), dataType, color);
            } else {
                addOutput(port.id(), port.label(), dataType, color);
            }
        }
        CompoundTag identity = new CompoundTag();
        identity.putString("id", source.id().toString());
        identity.putString("title", title);
        identity.putInt("x", source.x());
        identity.putInt("y", source.y());
        load(identity);
        if (definition != null) {
            Map<String, CompoundTag> encodedFields = source.fields();
            definition.fields().forEach(schema -> {
                CompoundTag encoded = encodedFields.get(schema.id());
                org.luaj.vm2.LuaValue value = encoded == null
                        ? schema.defaultValue()
                        : fieldCodec.decode(encoded);
                fieldControls.add(new LuaNodeFieldControl(schema, value));
            });
        }
        updateLayout();
    }

    public GraphNode source() {
        return toGraphNode(getX(), getY(), source.id());
    }

    public String category() {
        return category;
    }

    public NodeStyle style() {
        return style;
    }

    LuaNodeDefinition definition() {
        return definition;
    }

    public org.luaj.vm2.LuaValue fieldValue(String id) {
        return fieldControls.stream()
                .filter(control -> control.schema().id().equals(id))
                .map(LuaNodeFieldControl::value)
                .findFirst()
                .orElse(org.luaj.vm2.LuaValue.NIL);
    }

    public boolean setFieldValue(String id, org.luaj.vm2.LuaValue value) {
        for (int index = 0; index < fieldControls.size(); index++) {
            LuaNodeFieldControl control = fieldControls.get(index);
            if (control.schema().id().equals(id)) {
                fieldControls.set(index, new LuaNodeFieldControl(control.schema(), value));
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BedrockNodeRenderer.render(graphics, this, category, false, false, mouseX, mouseY);
        int fieldsY = getY() + fieldsTop();
        for (int index = 0; index < fieldControls.size(); index++) {
            fieldControls.get(index).render(
                    graphics,
                    getX(),
                    fieldsY + index * LuaNodeFieldControl.ROW_HEIGHT,
                    getWidth(),
                    mouseX,
                    mouseY);
        }
    }

    @Override
    public void updateLayout() {
        if (definition == null) {
            super.updateLayout();
            return;
        }
        NodeRenderLayout layout = NodeRenderLayout.measure(definition);
        setMeasuredSize(layout.width(), layout.height());
    }

    @Override
    public boolean hasInteractiveElementAt(double mouseX, double mouseY) {
        if (fieldControls.stream().anyMatch(LuaNodeFieldControl::focused)) {
            return true;
        }
        int first = fieldsTop();
        return !fieldControls.isEmpty()
                && mouseX >= 6
                && mouseX < getWidth() - 6
                && mouseY >= first
                && mouseY < first + fieldControls.size() * LuaNodeFieldControl.ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int first = fieldsTop();
        for (int index = 0; index < fieldControls.size(); index++) {
            LuaNodeFieldControl control = fieldControls.get(index);
            if (control.mouseClicked(
                    mouseX,
                    mouseY,
                    button,
                    getWidth(),
                    first + index * LuaNodeFieldControl.ROW_HEIGHT)) {
                fieldControls.stream()
                        .filter(other -> other != control)
                        .forEach(LuaNodeFieldControl::clearFocus);
                return true;
            }
        }
        clearElementFocus();
        return false;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        for (LuaNodeFieldControl control : fieldControls) {
            if (control.mouseDragged(mouseX, button, getWidth())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (LuaNodeFieldControl control : fieldControls) {
            if (control.mouseReleased(button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (LuaNodeFieldControl control : fieldControls) {
            if (control.keyPressed(keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (LuaNodeFieldControl control : fieldControls) {
            if (control.charTyped(codePoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFocusedElement() {
        return fieldControls.stream().anyMatch(LuaNodeFieldControl::focused);
    }

    @Override
    public void clearElementFocus() {
        fieldControls.forEach(LuaNodeFieldControl::clearFocus);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        CompoundTag fields = new CompoundTag();
        fieldControls.forEach(control ->
                fields.put(control.schema().id(), fieldCodec.encode(control.value())));
        tag.put("luaFields", fields);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains("luaFields") || fieldControls.isEmpty()) {
            return;
        }
        CompoundTag fields = tag.getCompound("luaFields");
        Map<String, LuaNodeFieldControl> byId = new LinkedHashMap<>();
        fieldControls.forEach(control -> byId.put(control.schema().id(), control));
        for (String id : fields.getAllKeys()) {
            LuaNodeFieldControl current = byId.get(id);
            if (current == null) {
                continue;
            }
            int index = fieldControls.indexOf(current);
            fieldControls.set(index, new LuaNodeFieldControl(
                    current.schema(),
                    fieldCodec.decode(fields.getCompound(id))));
        }
    }

    @Override
    public boolean isStateBoundary() {
        return stateBoundary;
    }

    public GraphNode toGraphNode(int x, int y, UUID id) {
        Map<String, CompoundTag> fields = new LinkedHashMap<>();
        fieldControls.forEach(control ->
                fields.put(control.schema().id(), fieldCodec.encode(control.value())));
        if (fieldControls.isEmpty()) {
            fields.putAll(source.fields());
        }
        return new GraphNode(
                id,
                source.definitionId(),
                source.definitionHash(),
                x,
                y,
                source.ports(),
                fields);
    }

    private int fieldsTop() {
        int portRows = Math.max(getInputs().size(), getOutputs().size());
        return 20 + portRows * 12 + (portRows == 0 ? 0 : 4);
    }

    private static WPin.DataType dataType(ConnectionType type) {
        return switch (type) {
            case STRING -> WPin.DataType.STRING;
            case WIDGET, TABLE -> WPin.DataType.WIDGET;
            case NUMBER, BOOLEAN, EVENT -> WPin.DataType.NUMBER;
        };
    }

    private static int color(ConnectionType type) {
        return switch (type) {
            case NUMBER -> 0xFF4E86E8;
            case BOOLEAN -> 0xFF985AD6;
            case STRING -> 0xFFD653B5;
            case EVENT -> 0xFF27C7D9;
            case WIDGET -> 0xFF9BCB45;
            case TABLE -> 0xFF8A9099;
        };
    }
}
