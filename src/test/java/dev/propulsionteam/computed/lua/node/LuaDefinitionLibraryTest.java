package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LuaDefinitionLibraryTest {
    @Test
    void requiresReplacementConfirmationAndReportsStablePortReconnections() {
        LuaDefinitionLibrary library = new LuaDefinitionLibrary(Map.of());
        String first = source("number", "number");
        String replacement = source("number", "string");

        LuaLibraryUpdate added = library.importSource(1, first, false, ignored -> true);
        LuaLibraryUpdate unchanged = library.importSource(1, first, false, ignored -> true);
        LuaLibraryUpdate confirmation = library.importSource(1, replacement, false, ignored -> true);
        LuaLibraryUpdate replaced = library.importSource(1, replacement, true, ignored -> true);

        assertEquals(LuaLibraryUpdate.Status.ADDED, added.status());
        assertEquals(LuaLibraryUpdate.Status.UNCHANGED, unchanged.status());
        assertEquals(LuaLibraryUpdate.Status.CONFIRMATION_REQUIRED, confirmation.status());
        assertEquals(LuaLibraryUpdate.Status.REPLACED, replaced.status());
        assertTrue(replaced.retainedPorts().contains("input:value:NUMBER"));
        assertTrue(replaced.removedPorts().contains("output:result:NUMBER"));
        assertEquals("output:result:STRING", library.schema("example:convert")
                .outputs()
                .stream()
                .map(port -> "output:" + port.id() + ':' + port.type())
                .findFirst()
                .orElseThrow());
    }

    private static String source(String inputType, String outputType) {
        return """
                local node = computed.node(1, "example:convert", "Convert")
                node:input("value", "%s")
                node:output("result", "%s")
                node:on_run(function(ctx)
                    ctx:output("result", ctx:input("value"))
                end)
                return node
                """.formatted(inputType, outputType);
    }
}
