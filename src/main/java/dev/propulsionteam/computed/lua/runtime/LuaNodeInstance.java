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
    private final Object endpointHost;
    private final LuaNodeDefinition definition;
    private final boolean endpointCapable;
    private final Map<String, LuaValue> state = new LinkedHashMap<>();
    private final Map<String, LuaValue> outputs = new LinkedHashMap<>();
    private final LuaInvocationContext context = new LuaInvocationContext();
    private LuaInvocationWorker worker;
    private PendingLuaInvocation pending;
    private ComputedDiagnostic lastDiagnostic;
    private LuaNodeStatus status = LuaNodeStatus.IDLE;

    public LuaNodeInstance(
            UUID computerId,
            UUID nodeId,
            LuaSandbox sandbox,
            LuaNodeDefinition definition,
            Object endpointHost,
            boolean endpointCapable) {
        this.computerId = Objects.requireNonNull(computerId, "computerId");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.endpointHost = endpointHost;
        this.endpointCapable = endpointCapable;
        worker = endpointCapable ? new LuaInvocationWorker(sandbox) : null;
        definition.stateDefaults().forEach((id, value) -> state.put(id, LuaValueCopies.copy(value)));
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
            resetWorker();
            status = LuaNodeStatus.CANCELLED;
            lastDiagnostic = diagnostic("yield_cancelled", "Yielded invocation was cancelled");
        }
    }

    public Map<String, LuaValue> state() {
        return LuaValueCopies.copyMap(state);
    }

    public void restoreState(Map<String, LuaValue> restoredState) {
        if (pending != null) {
            throw new IllegalStateException("Cannot restore state while a node is yielded");
        }
        Map<String, LuaValue> checked = LuaValueCopies.copyMap(restoredState);
        for (String id : checked.keySet()) {
            if (!definition.stateDefaults().containsKey(id)) {
                throw new IllegalArgumentException("Unknown state id " + id + " for node " + definition.id());
            }
        }
        state.clear();
        definition.stateDefaults().forEach((id, value) -> state.put(id, LuaValueCopies.copy(value)));
        state.putAll(checked);
    }

    public Map<String, LuaValue> outputs() {
        return LuaValueCopies.copyMap(outputs);
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
        PendingLuaInvocation created = new PendingLuaInvocation(
                computerId,
                nodeId,
                sandbox,
                preview,
                endpointHost,
                tick,
                graphStep,
                LuaValueCopies.copyMap(inputs),
                LuaValueCopies.copyMap(fields),
                LuaValueCopies.copyMap(state),
                LuaValueCopies.copyMap(outputs),
                eventSink == null ? (name, values) -> {} : eventSink,
                callback,
                worker,
                context);
        context.bind(created);
        return created;
    }

    private LuaInvocationResult advance(java.util.function.Supplier<Varargs> action) {
        try (LuaInstructionBudget.Scope ignored = sandbox.budget().beginInvocation()) {
            Varargs result = action.get();
            if (!result.arg1().toboolean()) {
                return failAndDiscard("runtime_error", result.arg(2).tojstring());
            }
            if (pending.completed(result)) {
                commitPending();
                status = LuaNodeStatus.IDLE;
                lastDiagnostic = null;
                return snapshot();
            }
            if (pending.suspended()) {
                if (!pending.waiting()) {
                    return failAndDiscard("unexpected_yield", "Node yielded without an endpoint continuation");
                }
                status = LuaNodeStatus.YIELDED;
                lastDiagnostic = null;
                return snapshot();
            }
            return failAndDiscard("runtime_error", "Lua invocation worker stopped unexpectedly");
        } catch (LuaError error) {
            return failAndDiscard("runtime_error", error.getMessage());
        } catch (StackOverflowError error) {
            return failAndDiscard("runtime_error", "Lua node exceeded the recursion limit");
        } catch (RuntimeException exception) {
            return failAndDiscard("runtime_error", exception.getMessage());
        }
    }

    private void commitPending() {
        Map<String, LuaValue> checkedState = LuaValueCopies.copyMap(pending.state());
        Map<String, LuaValue> checkedOutputs = LuaValueCopies.copyMap(pending.outputs());
        state.clear();
        state.putAll(checkedState);
        outputs.clear();
        outputs.putAll(checkedOutputs);
        pending = null;
    }

    private LuaInvocationResult failAndDiscard(String code, String message) {
        pending = null;
        resetWorker();
        status = LuaNodeStatus.FAILED;
        lastDiagnostic = diagnostic(code, message);
        return snapshot();
    }

    private void resetWorker() {
        if (endpointCapable) {
            worker = new LuaInvocationWorker(sandbox);
        }
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
        return new LuaInvocationResult(status, outputs, diagnostics);
    }

}
