package dev.propulsionteam.computed.lua.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class LuaYieldRuntimeTest {
    @Test
    void resumesYieldedEndpointCallsAndCommitsOnlyAfterCompletion() {
        CompletableFuture<EndpointResult.Immediate> continuation = new CompletableFuture<>();
        String endpointId = "test:yield_" + UUID.randomUUID().toString().replace("-", "");
        ComputedEndpoints.register(endpointId, endpoint -> endpoint.method(
                "wait",
                EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                new EndpointPolicy(EndpointPolicy.ExecutionSide.COMPUTER_THREAD, true, false, false),
                invocation -> EndpointResult.yielded(continuation)));
        LuaComputerRuntime runtime = new LuaComputerRuntime(UUID.randomUUID());
        LuaNodeInstance node = runtime.createNode(UUID.randomUUID(), 1, """
                local node = computed.node(1, "example:yield", "Yield")
                node:output("value", "number")
                node:on_run(function(ctx)
                    local endpoint = ctx:endpoint("%s")
                    ctx:output("value", endpoint:call("wait"))
                end)
                return node
                """.formatted(endpointId));
        runtime.beginTick(1);

        LuaInvocationResult yielded =
                node.run(Map.of(), Map.of(), 1, runtime.nextGraphStep(), false, null);
        assertEquals(LuaNodeStatus.YIELDED, yielded.status());
        assertTrue(yielded.outputs().isEmpty());

        continuation.complete(EndpointResult.immediate(LuaValue.valueOf(42)));
        LuaInvocationResult resumed = node.resumeIfReady();

        assertEquals(LuaNodeStatus.IDLE, resumed.status());
        assertEquals(42.0, resumed.outputs().get("value").todouble());
    }
}
