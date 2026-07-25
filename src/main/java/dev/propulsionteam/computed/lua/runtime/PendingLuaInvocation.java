package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class PendingLuaInvocation {
    private final UUID computerId;
    private final UUID nodeId;
    private final LuaSandbox sandbox;
    private final boolean preview;
    private final Object endpointHost;
    private final long tick;
    private final long graphStep;
    private final Map<String, LuaValue> inputs;
    private final Map<String, LuaValue> fields;
    private final Map<String, LuaValue> state;
    private final Map<String, LuaValue> outputs;
    private final BiConsumer<String, List<LuaValue>> eventSink;
    private final LuaValue callback;
    private final LuaInvocationWorker worker;
    private final LuaInvocationContext context;
    private CompletionStage<EndpointResult.Immediate> continuation;

    PendingLuaInvocation(
            UUID computerId,
            UUID nodeId,
            LuaSandbox sandbox,
            boolean preview,
            Object endpointHost,
            long tick,
            long graphStep,
            Map<String, LuaValue> inputs,
            Map<String, LuaValue> fields,
            Map<String, LuaValue> state,
            Map<String, LuaValue> outputs,
            BiConsumer<String, List<LuaValue>> eventSink,
            LuaValue callback,
            LuaInvocationWorker worker,
            LuaInvocationContext context) {
        this.computerId = computerId;
        this.nodeId = nodeId;
        this.sandbox = sandbox;
        this.preview = preview;
        this.endpointHost = endpointHost;
        this.tick = tick;
        this.graphStep = graphStep;
        this.inputs = inputs;
        this.fields = fields;
        this.state = state;
        this.outputs = outputs;
        this.eventSink = eventSink;
        this.callback = callback;
        this.worker = worker;
        this.context = context;
    }

    Varargs start(List<LuaValue> eventArguments) {
        if (worker == null) {
            LuaValue[] arguments = new LuaValue[eventArguments.size() + 1];
            arguments[0] = context.table();
            for (int index = 0; index < eventArguments.size(); index++) {
                arguments[index + 1] = eventArguments.get(index);
            }
            callback.invoke(LuaValue.varargsOf(arguments));
            return LuaValue.TRUE;
        }
        return worker.invoke(callback, context.table(), eventArguments);
    }

    Varargs resume() {
        CompletableFuture<EndpointResult.Immediate> future = continuation.toCompletableFuture();
        EndpointResult.Immediate result = future.join();
        continuation = null;
        return worker.resume(result.values());
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
        return worker != null && worker.suspended();
    }

    boolean completed(Varargs result) {
        return worker == null || worker.completed(result);
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

    Object endpointHost() {
        return endpointHost;
    }

    LuaValue input(String id) {
        return inputs.getOrDefault(id, LuaValue.NIL);
    }

    void output(String id, LuaValue value) {
        outputs.put(id, value);
    }

    LuaValue field(String id) {
        return fields.getOrDefault(id, LuaValue.NIL);
    }

    LuaValue state(String id) {
        return state.getOrDefault(id, LuaValue.NIL);
    }

    void state(String id, LuaValue value) {
        state.put(id, value);
    }

    void emit(String name, List<LuaValue> values) {
        eventSink.accept(name, values);
    }

    long tick() {
        return tick;
    }

    long graphStep() {
        return graphStep;
    }
}
