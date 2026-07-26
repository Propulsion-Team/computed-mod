package dev.propulsionteam.computed.lua.sandbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

class LuaSandboxTest {
    @Test
    void exposesOnlyTheApprovedLibrarySurface() {
        LuaSandbox sandbox = new LuaSandbox();
        List<String> blocked = List.of(
                "io", "os", "debug", "package", "require", "load", "loadfile", "dofile", "luajava");

        blocked.forEach(name -> assertTrue(sandbox.isBlocked(name), name));

        LuaTable environment = sandbox.createEnvironment();
        environment.set("java", LuaValue.NIL);
        var compiled = new LuaSourceCompiler().compile(
                1,
                "return math ~= nil and string ~= nil and table ~= nil and bit32 ~= nil "
                        + "and coroutine ~= nil and io == nil and os == nil and debug == nil "
                        + "and package == nil and require == nil and luajava == nil");
        LuaValue result;
        try (LuaInstructionBudget.Scope ignored = sandbox.budget().beginInvocation()) {
            result = new LuaClosure(compiled.prototype(), environment).call();
        }

        assertTrue(result.toboolean());
    }
}
