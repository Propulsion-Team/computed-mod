package dev.propulsionteam.computed.lua.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class LuaNodeRuntimeTest {
    @Test
    void commitsAtomicallyAndRetainsTheLastSuccessfulStateAfterFailure() {
        LuaComputerRuntime runtime = new LuaComputerRuntime(UUID.randomUUID());
        LuaNodeInstance node = runtime.createNode(UUID.randomUUID(), 1, """
                local node = computed.node(1, "example:counter", "Counter")
                node:input("fail", "boolean", { default = false })
                node:output("count", "number")
                node:state("count", 0)
                node:on_run(function(ctx)
                    local next = ctx:state("count") + 1
                    ctx:set_state("count", next)
                    ctx:output("count", next)
                    if ctx:input("fail") then
                        error("requested failure")
                    end
                end)
                return node
                """);
        runtime.beginTick(1);

        LuaInvocationResult successful =
                node.run(Map.of("fail", LuaValue.FALSE), Map.of(), 1, runtime.nextGraphStep(), false, null);
        LuaInvocationResult failed =
                node.run(Map.of("fail", LuaValue.TRUE), Map.of(), 1, runtime.nextGraphStep(), false, null);

        assertEquals(LuaNodeStatus.IDLE, successful.status());
        assertEquals(1.0, successful.outputs().get("count").todouble());
        assertEquals(LuaNodeStatus.FAILED, failed.status());
        assertEquals(1.0, failed.outputs().get("count").todouble());
        assertEquals(1.0, node.state().get("count").todouble());
        assertFalse(failed.diagnostics().isEmpty());
        assertTrue(runtime.sandbox().budget().tickRemaining() < LuaInstructionBudget.DEFAULT_TICK_LIMIT);
    }

    @Test
    void abortsAnInfiniteLoopWithoutDiscardingCommittedOutputs() {
        LuaInstructionBudget budget = new LuaInstructionBudget(2_000, 20_000);
        LuaComputerRuntime runtime = new LuaComputerRuntime(UUID.randomUUID(), budget);
        LuaNodeInstance node = runtime.createNode(UUID.randomUUID(), 1, """
                local node = computed.node(1, "example:loop", "Loop")
                node:output("value", "number")
                node:on_run(function(ctx)
                    ctx:output("value", 12)
                    while true do end
                end)
                return node
                """);
        runtime.beginTick(1);

        LuaInvocationResult result =
                node.run(Map.of(), Map.of(), 1, runtime.nextGraphStep(), false, null);

        assertEquals(LuaNodeStatus.FAILED, result.status());
        assertFalse(result.diagnostics().isEmpty());
        assertFalse(result.outputs().containsKey("value"));
    }
}
