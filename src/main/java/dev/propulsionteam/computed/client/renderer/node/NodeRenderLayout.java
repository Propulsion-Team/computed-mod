package dev.propulsionteam.computed.client.renderer.node;

import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.NodeStyle;

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
        int rows = Math.max(
                Math.max(definition.inputs().size(), definition.outputs().size()),
                definition.fields().size());
        boolean compact = definition.style() == NodeStyle.COMPACT;
        boolean sideRail = compact || definition.style() == NodeStyle.SINK;
        int width = compact ? 96 : 144;
        int titleHeight = 18;
        int panelHeight = Math.max(18, rows * 18 + 8);
        return new NodeRenderLayout(
                width,
                titleHeight + panelHeight + 4,
                titleHeight,
                5,
                titleHeight + 1,
                width - 10,
                panelHeight,
                18,
                sideRail);
    }
}
