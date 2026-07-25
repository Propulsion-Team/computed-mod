package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import org.junit.jupiter.api.Test;

class LuaDefinitionLoaderTest {
    private final LuaSourceCompiler compiler = new LuaSourceCompiler();
    private final LuaDefinitionLoader loader = new LuaDefinitionLoader();

    @Test
    void loadsTheFluentDefinitionContract() {
        String source = """
                local node = computed.node(1, "example:counter", "Counter")
                node:category("state")
                node:style("compact")
                node:input("increment", "number", { default = 1 })
                node:output("count", "number")
                node:field("step", "number", { default = 2, min = 0, max = 10 })
                node:state("count", 0)
                node:execution("tick")
                node:on_run(function(ctx)
                    ctx:output("count", ctx:state("count"))
                end)
                return node
                """;

        LuaNodeDefinition definition =
                loader.load(compiler.compile(1, source), new LuaSandbox());

        assertEquals("example:counter", definition.id());
        assertEquals("state", definition.category());
        assertEquals(NodeStyle.COMPACT, definition.style());
        assertEquals(LuaExecutionPolicy.TICK, definition.executionPolicy());
        assertEquals(ConnectionType.NUMBER, definition.inputs().getFirst().type());
        assertEquals(2.0, definition.fields().getFirst().defaultValue().todouble());
        assertEquals(0.0, definition.stateDefaults().get("count").todouble());
    }

    @Test
    void rejectsDuplicateSchemasAndMissingCallbacks() {
        String duplicate = """
                local node = computed.node(1, "example:bad", "Bad")
                node:input("value", "number")
                node:input("value", "number")
                node:on_run(function(ctx) end)
                return node
                """;
        String missingCallback = """
                local node = computed.node(1, "example:bad", "Bad")
                return node
                """;

        assertThrows(
                LuaDefinitionException.class,
                () -> loader.load(compiler.compile(1, duplicate), new LuaSandbox()));
        assertThrows(
                LuaDefinitionException.class,
                () -> loader.load(compiler.compile(1, missingCallback), new LuaSandbox()));
    }
}
