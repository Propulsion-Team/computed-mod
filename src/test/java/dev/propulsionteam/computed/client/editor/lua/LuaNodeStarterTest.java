package dev.propulsionteam.computed.client.editor.lua;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import org.junit.jupiter.api.Test;

class LuaNodeStarterTest {
    @Test
    void createsUniqueCompilableReusableDefinitions() {
        LuaNodeStarter.Starter first = LuaNodeStarter.create();
        LuaNodeStarter.Starter second = LuaNodeStarter.create();
        var definition = new LuaDefinitionLoader().load(
                new LuaSourceCompiler().compile(1, first.source()),
                new LuaSandbox());

        assertTrue(first.id().startsWith("user:node_"));
        assertNotEquals(first.id(), second.id());
        assertTrue(first.source().contains(first.id()));
        assertTrue(definition.id().equals(first.id()));
    }
}
