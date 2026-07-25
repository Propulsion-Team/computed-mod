package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.client.renderer.node.BedrockNodeRenderer;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.node.NodeStyle;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class LuaEditorNode extends WNode {
    private final GraphNode source;
    private final boolean stateBoundary;
    private final String category;
    private final NodeStyle style;

    public LuaEditorNode(
            GraphNode source,
            String title,
            boolean stateBoundary,
            String category,
            NodeStyle style) {
        super(ResourceLocation.parse(source.definitionId()), title, source.x(), source.y());
        this.source = source;
        this.stateBoundary = stateBoundary;
        this.category = category == null ? "utility" : category;
        this.style = style == null ? NodeStyle.STANDARD : style;
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
        updateLayout();
    }

    public GraphNode source() {
        return source;
    }

    public String category() {
        return category;
    }

    public NodeStyle style() {
        return style;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BedrockNodeRenderer.render(graphics, this, category, false, false, mouseX, mouseY);
    }

    @Override
    public boolean isStateBoundary() {
        return stateBoundary;
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
