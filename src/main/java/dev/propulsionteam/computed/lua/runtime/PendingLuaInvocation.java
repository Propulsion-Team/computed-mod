package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class PendingLuaInvocation {
    private final UUID computerId;
    private final UUID nodeId;
    private final LuaSandbox sandbox;
    private final boolean preview;
    private final long tick;
    private final long graphStep;
    private final Map<String, LuaValue> inputs;
    private final Map<String, LuaValue> fields;
    private final Map<String, LuaValue> state;
    private final Map<String, LuaValue> outputs;
    private final BiConsumer<String, List<LuaValue>> eventSink;
    private final LuaThread thread;
    private CompletionStage<EndpointResult.Immediate> continuation;

    PendingLuaInvocation(
            UUID computerId,
            UUID nodeId,
            LuaSandbox sandbox,
            boolean preview,
            long tick,
            long graphStep,
            Map<String, LuaValue> inputs,
            Map<String, LuaValue> fields,
            Map<String, LuaValue> state,
            Map<String, LuaValue> outputs,
            BiConsumer<String, List<LuaValue>> eventSink,
            LuaValue callback) {
        this.computerId = computerId;
        this.nodeId = nodeId;
        this.sandbox = sandbox;
        this.preview = preview;
        this.tick = tick;
        this.graphStep = graphStep;
        this.inputs = new LinkedHashMap<>(inputs);
        this.fields = new LinkedHashMap<>(fields);
        this.state = new LinkedHashMap<>(state);
        this.outputs = new LinkedHashMap<>(outputs);
        this.eventSink = eventSink;
        this.thread = new LuaThread(sandbox.globals(), callback);
        sandbox.installHook(thread);
    }

    LuaTable context() {
        LuaTable context = new LuaTable();
        context.set("input", method(context, args -> value(inputs, args.arg(2))));
        context.set("output", method(context, args -> {
            outputs.put(args.arg(2).checkjstring(), args.arg(3));
            return LuaValue.NIL;
        }));
        context.set("field", method(context, args -> value(fields, args.arg(2))));
        context.set("state", method(context, args -> value(state, args.arg(2))));
        context.set("set_state", method(context, args -> {
            state.put(args.arg(2).checkjstring(), args.arg(3));
            return LuaValue.NIL;
        }));
        context.set("endpoint", method(context, args -> LuaEndpointProxy.create(
                this,
                args.arg(2).checkjstring(),
                args.arg(3).optjstring(""))));
        context.set("emit", method(context, args -> {
            String eventName = args.arg(2).checkjstring();
            List<LuaValue> values = new java.util.ArrayList<>();
            for (int index = 3; index <= args.narg(); index++) {
                values.add(args.arg(index));
            }
            eventSink.accept(eventName, values);
            return LuaValue.NIL;
        }));
        context.set("tick", method(context, args -> LuaValue.valueOf(tick)));
        context.set("graph_step", method(context, args -> LuaValue.valueOf(graphStep)));
        context.set("is_preview", method(context, args -> LuaValue.valueOf(preview)));
        return context;
    }

    Varargs start(List<LuaValue> eventArguments) {
        LuaValue[] arguments = new LuaValue[eventArguments.size() + 1];
        arguments[0] = context();
        for (int index = 0; index < eventArguments.size(); index++) {
            arguments[index + 1] = eventArguments.get(index);
        }
        return thread.resume(LuaValue.varargsOf(arguments));
    }

    Varargs resume() {
        CompletableFuture<EndpointResult.Immediate> future = continuation.toCompletableFuture();
        EndpointResult.Immediate result = future.join();
        continuation = null;
        return thread.resume(LuaValue.varargsOf(result.values().toArray(LuaValue[]::new)));
    }

    void yieldFor(CompletionStage<EndpointResult.Immediate> continuation) {
        if (this.continuation != null) {
            throw new IllegalStateException("A Lua node cannot wait for multiple endpoint calls");
        }
        this.continuation = continuation;
    }

    boolean continuationReady() {
        return continuation != null && continuation.toCompletableFuture().isDone();
    }

    boolean waiting() {
        return continuation != null;
    }

    boolean suspended() {
        return LuaThread.STATUS_NAMES[LuaThread.STATUS_SUSPENDED].equals(thread.getStatus());
    }

    Map<String, LuaValue> state() {
        return state;
    }

    Map<String, LuaValue> outputs() {
        return outputs;
    }

    UUID computerId() {
        return computerId;
    }

    UUID nodeId() {
        return nodeId;
    }

    LuaSandbox sandbox() {
        return sandbox;
    }

    boolean preview() {
        return preview;
    }

    private static LuaValue value(Map<String, LuaValue> values, LuaValue key) {
        return values.getOrDefault(key.checkjstring(), LuaValue.NIL);
    }

    private static VarArgFunction method(
            LuaTable context,
            java.util.function.Function<Varargs, LuaValue> action) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.arg1() != context) {
                    throw new org.luaj.vm2.LuaError("Context methods must be called with ':'");
                }
                return action.apply(args);
            }
        };
    }
}
