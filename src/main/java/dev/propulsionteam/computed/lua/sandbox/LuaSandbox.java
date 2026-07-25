package dev.propulsionteam.computed.lua.sandbox;

import java.util.List;
import java.util.Objects;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;

public final class LuaSandbox {
    private static final List<String> BLOCKED_GLOBALS = List.of(
            "io",
            "os",
            "debug",
            "package",
            "require",
            "load",
            "loadfile",
            "dofile",
            "luajava");

    private final Globals globals;
    private final LuaInstructionBudget budget;
    private final LuaValue invocationWorker;

    public LuaSandbox() {
        this(new LuaInstructionBudget());
    }

    public LuaSandbox(LuaInstructionBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
        globals = new Globals();
        globals.load(new BaseLib());
        globals.load(new PackageLib());
        globals.load(new MathLib());
        globals.load(new StringLib());
        globals.load(new TableLib());
        globals.load(new Bit32Lib());
        globals.load(new CoroutineLib());
        LuaC.install(globals);
        globals.load(new LuaBudgetDebugLib(budget));
        invocationWorker = globals.load("""
                local unpack_values = table.unpack or unpack
                return function(completion_marker)
                    local callback, context, arguments = coroutine.yield()
                    while true do
                        if arguments then
                            callback(context, unpack_values(arguments, 1, arguments.n))
                        else
                            callback(context)
                        end
                        callback, context, arguments = coroutine.yield(completion_marker)
                    end
                end
                """, "@computed-invocation-worker").call();
        BLOCKED_GLOBALS.forEach(name -> globals.set(name, LuaValue.NIL));
    }

    public Globals createEnvironment() {
        Globals environment = new Globals();
        environment.debuglib = globals.debuglib;
        LuaTable metatable = new LuaTable();
        metatable.set("__index", globals);
        environment.setmetatable(metatable);
        environment.set("_G", environment);
        return environment;
    }

    public Globals globals() {
        return globals;
    }

    public LuaInstructionBudget budget() {
        return budget;
    }

    public LuaValue invocationWorker() {
        return invocationWorker;
    }

    public boolean isBlocked(String name) {
        return globals.get(name).isnil();
    }

    public void installHook(LuaThread thread) {
        Objects.requireNonNull(thread, "thread");
    }
}
