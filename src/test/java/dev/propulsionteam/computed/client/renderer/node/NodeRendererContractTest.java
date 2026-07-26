package dev.propulsionteam.computed.client.renderer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.NodeStyle;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class NodeRendererContractTest {
    @Test
    void mapsEverySemanticCategoryAndKeepsStatusColorsDistinct() {
        assertEquals(NodePalette.FLOW, NodePalette.category("flow"));
        assertEquals(NodePalette.LOGIC, NodePalette.category("logic"));
        assertEquals(NodePalette.MATH, NodePalette.category("math"));
        assertEquals(NodePalette.WORLD, NodePalette.category("world"));
        assertEquals(NodePalette.STATE, NodePalette.category("state"));
        assertEquals(NodePalette.TEXT, NodePalette.category("text"));
        assertEquals(NodePalette.WIDGETS, NodePalette.category("widgets"));
        assertEquals(NodePalette.IO, NodePalette.category("io"));
        assertEquals(NodePalette.LUA, NodePalette.category("lua"));
        assertEquals(NodePalette.INTEGRATION, NodePalette.category("integration/create"));
        assertEquals(NodePalette.UTILITY, NodePalette.category("unknown"));
        assertEquals(NodePalette.values().length, Arrays.stream(NodePalette.values())
                .map(NodePalette::frameArgb)
                .distinct()
                .count());
        assertNotEquals(NodePalette.SELECTION, NodePalette.ERROR);
        assertNotEquals(NodePalette.ERROR, NodePalette.WARNING);
    }

    @Test
    void measuresEveryStyleAndAllFieldRowsWithStablePixelSpacing() {
        for (NodeStyle style : NodeStyle.values()) {
            String source = """
                    local node = computed.node(1, "example:layout_%s", "Layout")
                    node:style("%s")
                    node:input("number", "number")
                    node:output("table", "table")
                    node:field("number", "number", { default = 1 })
                    node:field("text", "text", { default = "x" })
                    node:field("boolean", "boolean", { default = true })
                    node:field("choice", "choice", { default = "a", choices = { "a", "b" } })
                    node:field("color", "color", { default = 0 })
                    node:field("direction", "direction", { default = "north" })
                    node:field("item", "item", { default = "minecraft:stone" })
                    node:on_run(function(ctx) end)
                    return node
                    """.formatted(style.name().toLowerCase(), style.name().toLowerCase());
            var definition = new LuaDefinitionLoader().load(
                    new LuaSourceCompiler().compile(1, source),
                    new LuaSandbox());
            NodeRenderLayout layout = NodeRenderLayout.measure(definition);

            assertTrue(layout.width() >= 96);
            assertEquals(12, layout.socketSpacing());
            assertTrue(layout.panelHeight() >= 7 * 18);
            if (style == NodeStyle.COMPACT) {
                assertTrue(layout.sideRail());
                assertTrue(layout.width() >= 144);
            } else {
                assertFalse(layout.width() < 96);
            }
        }
    }
}
