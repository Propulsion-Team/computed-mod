package dev.propulsionteam.computed.lua.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import dev.propulsionteam.computed.lua.endpoint.ServerEndpointExecutor;
import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class ServerEndpointDispatchTest {
    @Test
    void dispatchesServerEndpointWithoutBlockingTheLuaCoroutine() {
        String endpointId = endpointId();
        AtomicReference<String> handlerThread = new AtomicReference<>();
        ComputedEndpoints.register(endpointId, endpoint -> endpoint.method(
                "read",
                EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                EndpointPolicy.serverThread(false, false),
                invocation -> {
                    handlerThread.set(Thread.currentThread().getName());
                    return EndpointResult.immediate(LuaValue.valueOf(7));
                },
                null,
                "Test server-thread dispatch."));
        QueuedServerHost host = new QueuedServerHost();
        LuaComputerRuntime runtime =
                new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        LuaNodeInstance node = createNode(runtime, endpointId);
        runtime.beginTick(1);

        LuaInvocationResult first =
                node.run(Map.of(), Map.of(), 1, runtime.nextGraphStep(), false, null);

        assertEquals(LuaNodeStatus.YIELDED, first.status());
        assertNull(handlerThread.get());
        assertTrue(host.hasTasks());

        host.runNext();
        assertFalse(handlerThread.get().startsWith("Coroutine-"));

        runtime.beginTick(2);
        LuaInvocationResult resumed = node.resumeIfReady();

        assertEquals(LuaNodeStatus.IDLE, resumed.status());
        assertEquals(7, resumed.outputs().get("value").checkint());
    }

    @Test
    void flattensAContinuationReturnedByTheServerHandler() {
        String endpointId = endpointId();
        CompletableFuture<EndpointResult.Immediate> nested = new CompletableFuture<>();
        ComputedEndpoints.register(endpointId, endpoint -> endpoint.method(
                "read",
                EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                EndpointPolicy.serverThread(false, false),
                invocation -> EndpointResult.yielded(nested),
                null,
                "Test nested server continuation."));
        QueuedServerHost host = new QueuedServerHost();
        LuaComputerRuntime runtime =
                new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        LuaNodeInstance node = createNode(runtime, endpointId);
        runtime.beginTick(1);

        LuaInvocationResult first =
                node.run(Map.of(), Map.of(), 1, runtime.nextGraphStep(), false, null);
        host.runNext();

        assertEquals(LuaNodeStatus.YIELDED, first.status());
        assertEquals(LuaNodeStatus.YIELDED, node.resumeIfReady().status());

        nested.complete(EndpointResult.immediate(LuaValue.valueOf(11)));
        LuaInvocationResult resumed = node.resumeIfReady();

        assertEquals(LuaNodeStatus.IDLE, resumed.status());
        assertEquals(11, resumed.outputs().get("value").checkint());
    }

    @Test
    void reportsServerHandlerFailuresAsRuntimeDiagnostics() {
        String endpointId = endpointId();
        ComputedEndpoints.register(endpointId, endpoint -> endpoint.method(
                "read",
                EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                EndpointPolicy.serverThread(false, false),
                invocation -> {
                    throw new IllegalStateException("server handler failed");
                },
                null,
                "Test server failure."));
        QueuedServerHost host = new QueuedServerHost();
        LuaComputerRuntime runtime =
                new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        LuaNodeInstance node = createNode(runtime, endpointId);
        runtime.beginTick(1);

        LuaInvocationResult first =
                node.run(Map.of(), Map.of(), 1, runtime.nextGraphStep(), false, null);
        host.runNext();
        LuaInvocationResult resumed = node.resumeIfReady();

        assertEquals(LuaNodeStatus.YIELDED, first.status());
        assertEquals(LuaNodeStatus.FAILED, resumed.status());
        assertTrue(resumed.diagnostics().getFirst().message().contains("server handler failed"));
    }

    @Test
    void reportsCancelledServerContinuationsAsRuntimeDiagnostics() {
        String endpointId = endpointId();
        CompletableFuture<EndpointResult.Immediate> nested = new CompletableFuture<>();
        ComputedEndpoints.register(endpointId, endpoint -> endpoint.method(
                "read",
                EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                EndpointPolicy.serverThread(false, false),
                invocation -> EndpointResult.yielded(nested),
                null,
                "Test server cancellation."));
        QueuedServerHost host = new QueuedServerHost();
        LuaComputerRuntime runtime =
                new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        LuaNodeInstance node = createNode(runtime, endpointId);
        runtime.beginTick(1);

        LuaInvocationResult first =
                node.run(Map.of(), Map.of(), 1, runtime.nextGraphStep(), false, null);
        host.runNext();
        nested.cancel(false);
        LuaInvocationResult resumed = node.resumeIfReady();

        assertEquals(LuaNodeStatus.YIELDED, first.status());
        assertEquals(LuaNodeStatus.FAILED, resumed.status());
        assertTrue(resumed.diagnostics().getFirst().message().contains("cancelled"));
    }

    @Test
    void rejectsServerPoliciesThatDoNotYield() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointPolicy(
                        EndpointPolicy.ExecutionSide.SERVER_THREAD,
                        false,
                        false,
                        false));
    }

    private static LuaNodeInstance createNode(
            LuaComputerRuntime runtime,
            String endpointId) {
        return runtime.createNode(UUID.randomUUID(), 1, """
                local node = computed.node(1, "test:server_node", "Server Node")
                node:output("value", "number")
                node:on_run(function(ctx)
                    local value = ctx:endpoint("%s"):call("read")
                    ctx:output("value", value)
                end)
                return node
                """.formatted(endpointId));
    }

    private static String endpointId() {
        return "test:server_dispatch_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static final class QueuedServerHost implements ServerEndpointExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public CompletionStage<EndpointResult> submitServerEndpoint(
                Callable<EndpointResult> endpointCall) {
            CompletableFuture<EndpointResult> result = new CompletableFuture<>();
            synchronized (tasks) {
                tasks.addLast(() -> {
                    try {
                        result.complete(endpointCall.call());
                    } catch (Exception exception) {
                        result.completeExceptionally(exception);
                    }
                });
            }
            return result;
        }

        boolean hasTasks() {
            synchronized (tasks) {
                return !tasks.isEmpty();
            }
        }

        void runNext() {
            Runnable task;
            synchronized (tasks) {
                task = tasks.removeFirst();
            }
            task.run();
        }
    }
}
