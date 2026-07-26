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
                node:field("step", "number", {
                    default = 2,
                    min = 0,
                    max = 10,
                    control = "slider",
                    step = 0.5,
                    label = "Step Size"
                })
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
        assertEquals(FieldControl.SLIDER, definition.fields().getFirst().control());
        assertEquals(0.5, definition.fields().getFirst().step());
        assertEquals("Step Size", definition.fields().getFirst().label());
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

    @Test
    void rejectsInvalidFieldPresentationMetadata() {
        String missingRange = definitionWithField(
                "node:field(\"value\", \"number\", { default = 1, control = \"slider\" })");
        String invalidStep = definitionWithField(
                "node:field(\"value\", \"number\", { default = 1, step = 0 })");
        String nonFiniteRange = definitionWithField(
                "node:field(\"value\", \"number\", { default = 1, min = -math.huge, max = 2 })");

        assertThrows(
                LuaDefinitionException.class,
                () -> loader.load(compiler.compile(1, missingRange), new LuaSandbox()));
        assertThrows(
                LuaDefinitionException.class,
                () -> loader.load(compiler.compile(1, invalidStep), new LuaSandbox()));
        assertThrows(
                LuaDefinitionException.class,
                () -> loader.load(compiler.compile(1, nonFiniteRange), new LuaSandbox()));
    }

    private static String definitionWithField(String field) {
        return """
                local node = computed.node(1, "example:field", "Field")
                %s
                node:on_run(function(ctx) end)
                return node
                """.formatted(field);
    }
}
