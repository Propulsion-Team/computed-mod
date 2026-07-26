package dev.propulsionteam.computed.client.renderer.node;

import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.LuaFieldSchema;
import dev.propulsionteam.computed.lua.node.NodeStyle;
import java.util.List;

public record NodeRenderLayout(
        int width,
        int height,
        int titleHeight,
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int socketSpacing,
        boolean sideRail) {

    public static NodeRenderLayout measure(LuaNodeDefinition definition) {
        return measure(definition, definition.fields());
    }

    public static NodeRenderLayout measure(
            LuaNodeDefinition definition,
            List<LuaFieldSchema> visibleFields) {
        int portRows = Math.max(definition.inputs().size(), definition.outputs().size());
        int fieldRows = visibleFields.size();
        boolean compact = definition.style() == NodeStyle.COMPACT;
        boolean sideRail = compact || definition.style() == NodeStyle.SINK;
        int titleWidth = definition.title().length() * 6 + 20;
        int inputWidth = definition.inputs().stream()
                .mapToInt(port -> port.id().length() * 6)
                .max()
                .orElse(0);
        int outputWidth = definition.outputs().stream()
                .mapToInt(port -> port.id().length() * 6)
                .max()
                .orElse(0);
        int portWidth = inputWidth + outputWidth + 38;
        int fieldWidth = visibleFields.stream()
                .mapToInt(field -> field.label().length() * 6 + 108)
                .max()
                .orElse(0);
        int width = Math.max(
                compact && fieldRows == 0 ? 96 : 144,
                Math.max(titleWidth, Math.max(portWidth, fieldWidth)));
        int titleHeight = 18;
        int contentHeight = portRows * 12
                + (portRows > 0 && fieldRows > 0 ? 4 : 0)
                + fieldRows * 18;
        int panelHeight = Math.max(18, contentHeight + 8);
        return new NodeRenderLayout(
                width,
                titleHeight + panelHeight + 4,
                titleHeight,
                5,
                titleHeight + 1,
                width - 10,
                panelHeight,
                12,
                sideRail);
    }
}
