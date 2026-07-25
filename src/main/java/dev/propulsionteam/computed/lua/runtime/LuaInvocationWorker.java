package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.List;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class LuaInvocationWorker {
    private final LuaTable completionMarker = new LuaTable();
    private final LuaThread thread;

    LuaInvocationWorker(LuaSandbox sandbox) {
        thread = new LuaThread(sandbox.globals(), sandbox.invocationWorker());
        Varargs primed = thread.resume(completionMarker);
        if (!primed.arg1().toboolean() || !suspended()) {
            throw new IllegalStateException("Failed to initialize Lua invocation worker");
        }
    }

    Varargs invoke(LuaValue callback, LuaTable context, List<LuaValue> eventArguments) {
        if (eventArguments.isEmpty()) {
            return thread.resume(LuaValue.varargsOf(new LuaValue[] {callback, context}));
        }
        LuaTable packed = new LuaTable();
        packed.set("n", eventArguments.size());
        for (int index = 0; index < eventArguments.size(); index++) {
            packed.set(index + 1, eventArguments.get(index));
        }
        return thread.resume(LuaValue.varargsOf(new LuaValue[] {callback, context, packed}));
    }

    Varargs resume(List<LuaValue> arguments) {
        return thread.resume(LuaValue.varargsOf(arguments.toArray(LuaValue[]::new)));
    }

    boolean completed(Varargs result) {
        return result.arg(2) == completionMarker;
    }

    boolean suspended() {
        return LuaThread.STATUS_NAMES[LuaThread.STATUS_SUSPENDED].equals(thread.getStatus());
    }
}
