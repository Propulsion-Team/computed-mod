package dev.propulsionteam.computed.lua.runtime;

import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class LuaInvocationContext {
    private final LuaTable table = new LuaTable();
    private PendingLuaInvocation invocation;

    LuaInvocationContext() {
        table.set("input", method(args -> invocation.input(args.arg(2).checkjstring())));
        table.set("output", method(args -> {
            invocation.output(args.arg(2).checkjstring(), args.arg(3));
            return LuaValue.NIL;
        }));
        table.set("field", method(args -> invocation.field(args.arg(2).checkjstring())));
        table.set("state", method(args -> invocation.state(args.arg(2).checkjstring())));
        table.set("set_state", method(args -> {
            invocation.state(args.arg(2).checkjstring(), args.arg(3));
            return LuaValue.NIL;
        }));
        table.set("endpoint", method(args -> LuaEndpointProxy.create(
                invocation,
                args.arg(2).checkjstring(),
                args.arg(3).optjstring(""))));
        table.set("emit", method(args -> {
            List<LuaValue> values = new ArrayList<>(Math.max(0, args.narg() - 2));
            for (int index = 3; index <= args.narg(); index++) {
                values.add(args.arg(index));
            }
            invocation.emit(args.arg(2).checkjstring(), values);
            return LuaValue.NIL;
        }));
        table.set("tick", method(args -> LuaValue.valueOf(invocation.tick())));
        table.set("graph_step", method(args -> LuaValue.valueOf(invocation.graphStep())));
        table.set("is_preview", method(args -> LuaValue.valueOf(invocation.preview())));
    }

    void bind(PendingLuaInvocation invocation) {
        this.invocation = invocation;
    }

    LuaTable table() {
        return table;
    }

    private VarArgFunction method(java.util.function.Function<Varargs, LuaValue> action) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.arg1() != table) {
                    throw new LuaError("Context methods must be called with ':'");
                }
                if (invocation == null) {
                    throw new LuaError("Context is not bound to an invocation");
                }
                return action.apply(args);
            }
        };
    }
}
