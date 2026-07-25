package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Phase;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Severity;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class LuaNodeInstance {
    private final UUID computerId;
    private final UUID nodeId;
    private final LuaSandbox sandbox;
    private final LuaNodeDefinition definition;
    private final LuaStateCodec stateCodec = new LuaStateCodec();
    private final Map<String, LuaValue> state = new LinkedHashMap<>();
    private final Map<String, LuaValue> outputs = new LinkedHashMap<>();
    private PendingLuaInvocation pending;
    private ComputedDiagnostic lastDiagnostic;
    private LuaNodeStatus status = LuaNodeStatus.IDLE;

    public LuaNodeInstance(
            UUID computerId,
            UUID nodeId,
            LuaSandbox sandbox,
            LuaNodeDefinition definition) {
        this.computerId = Objects.requireNonNull(computerId, "computerId");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
        this.definition = Objects.requireNonNull(definition, "definition");
        definition.stateDefaults().forEach((id, value) -> state.put(id, copy(value)));
    }

    public LuaInvocationResult run(
            Map<String, LuaValue> inputs,
            Map<String, LuaValue> fields,
            long tick,
            long graphStep,
            boolean preview,
            BiConsumer<String, List<LuaValue>> eventSink) {
        if (pending != null) {
            return failure("already_yielded", "Node is already waiting for an endpoint continuation");
        }
        if (definition.onRun() == null) {
            return failure("missing_on_run", "Node does not declare an on_run callback");
        }
        pending = createPending(
                definition.onRun(), inputs, fields, tick, graphStep, preview, eventSink);
        return advance(() -> pending.start(List.of()));
    }

    public LuaInvocationResult event(
            String eventName,
            List<LuaValue> arguments,
            Map<String, LuaValue> inputs,
            Map<String, LuaValue> fields,
            long tick,
            long graphStep,
            boolean preview,
            BiConsumer<String, List<LuaValue>> eventSink) {
        if (pending != null) {
            return failure("already_yielded", "Node is already waiting for an endpoint continuation");
        }
        LuaValue handler = definition.eventHandlers().get(eventName);
        if (handler == null) {
            return snapshot();
        }
        pending = createPending(handler, inputs, fields, tick, graphStep, preview, eventSink);
        return advance(() -> pending.start(arguments == null ? List.of() : arguments));
    }

    public LuaInvocationResult resumeIfReady() {
        if (pending == null || !pending.continuationReady()) {
            return snapshot();
        }
        return advance(pending::resume);
    }

    public void cancelYield() {
        if (pending != null) {
            pending = null;
            status = LuaNodeStatus.CANCELLED;
            lastDiagnostic = diagnostic("yield_cancelled", "Yielded invocation was cancelled");
        }
    }

    public Map<String, LuaValue> state() {
        return copyMap(state);
    }

    public Map<String, LuaValue> outputs() {
        return copyMap(outputs);
    }

    public LuaNodeStatus status() {
        return status;
    }

    public LuaNodeDefinition definition() {
        return definition;
    }

    private PendingLuaInvocation createPending(
            LuaValue callback,
            Map<String, LuaValue> inputs,
            Map<String, LuaValue> fields,
            long tick,
            long graphStep,
            boolean preview,
            BiConsumer<String, List<LuaValue>> eventSink) {
        return new PendingLuaInvocation(
                computerId,
                nodeId,
                sandbox,
                preview,
                tick,
                graphStep,
                copyMap(inputs),
                copyMap(fields),
                copyMap(state),
                copyMap(outputs),
                eventSink == null ? (name, values) -> {} : eventSink,
                callback);
    }

    private LuaInvocationResult advance(java.util.function.Supplier<Varargs> action) {
        try (LuaInstructionBudget.Scope ignored = sandbox.budget().beginInvocation()) {
            Varargs result = action.get();
            if (!result.arg1().toboolean()) {
                return failAndDiscard("runtime_error", result.arg(2).tojstring());
            }
            if (pending.suspended()) {
                if (!pending.waiting()) {
                    return failAndDiscard("unexpected_yield", "Node yielded without an endpoint continuation");
                }
                status = LuaNodeStatus.YIELDED;
                lastDiagnostic = null;
                return snapshot();
            }
            commitPending();
            status = LuaNodeStatus.IDLE;
            lastDiagnostic = null;
            return snapshot();
        } catch (LuaError error) {
            return failAndDiscard("runtime_error", error.getMessage());
        } catch (RuntimeException exception) {
            return failAndDiscard("runtime_error", exception.getMessage());
        }
    }

    private void commitPending() {
        Map<String, LuaValue> checkedState = copyMap(pending.state());
        Map<String, LuaValue> checkedOutputs = copyMap(pending.outputs());
        state.clear();
        state.putAll(checkedState);
        outputs.clear();
        outputs.putAll(checkedOutputs);
        pending = null;
    }

    private LuaInvocationResult failAndDiscard(String code, String message) {
        pending = null;
        status = LuaNodeStatus.FAILED;
        lastDiagnostic = diagnostic(code, message);
        return snapshot();
    }

    private LuaInvocationResult failure(String code, String message) {
        lastDiagnostic = diagnostic(code, message);
        return snapshot();
    }

    private ComputedDiagnostic diagnostic(String code, String message) {
        return new ComputedDiagnostic(
                Severity.ERROR,
                Phase.RUNTIME,
                code,
                message,
                nodeId,
                null,
                null);
    }

    private LuaInvocationResult snapshot() {
        List<ComputedDiagnostic> diagnostics =
                lastDiagnostic == null ? List.of() : List.of(lastDiagnostic);
        return new LuaInvocationResult(status, outputs(), diagnostics);
    }

    private Map<String, LuaValue> copyMap(Map<String, LuaValue> source) {
        Map<String, LuaValue> copied = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((id, value) -> copied.put(id, copy(value)));
        }
        return copied;
    }

    private LuaValue copy(LuaValue value) {
        return stateCodec.decode(stateCodec.encode(value));
    }
}
