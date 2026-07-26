package dev.propulsionteam.computed.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class LuaSchedulerBenchmarkTest {
    private static final int NODE_COUNT = 500;
    private static final int WARMUP_TICKS = 60;
    private static final int SAMPLE_TICKS = 120;
    private static final long MAX_P95_NANOS = 10_000_000;

    @Test
    void fiveHundredActiveNodesStayWithinTheP95Budget() {
        LuaDefinitionSource definition = LuaDefinitionSource.embedded(1, "benchmark:active", """
                local node = computed.node(1, "benchmark:active", "Active")
                node:category("utility")
                node:execution("tick")
                node:output("value", "number")
                node:on_run(function(ctx)
                    ctx:output("value", ctx:tick())
                end)
                return node
                """);
        List<GraphNode> nodes = new ArrayList<>(NODE_COUNT);
        for (int index = 0; index < NODE_COUNT; index++) {
            nodes.add(new GraphNode(
                    new UUID(1, index + 1L),
                    definition.id(),
                    definition.hash(),
                    index % 25 * 80,
                    index / 25 * 50,
                    List.of(new PortSnapshot(
                            "value",
                            PortDirection.OUTPUT,
                            ConnectionType.NUMBER,
                            "value")),
                    Map.of()));
        }
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(new UUID(2, 1), nodes, List.of()),
                Map.of(definition.id(), definition),
                Map.of(),
                null);
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, new UUID(3, 1), null);
        for (int tick = 0; tick < WARMUP_TICKS; tick++) {
            scheduler.tick(false);
        }
        long[] samples = new long[SAMPLE_TICKS];
        for (int tick = 0; tick < SAMPLE_TICKS; tick++) {
            long started = System.nanoTime();
            scheduler.tick(false);
            samples[tick] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        long p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1];
        System.out.printf(
                "Computed Lua benchmark: nodes=%d samples=%d p95=%.3fms%n",
                NODE_COUNT,
                SAMPLE_TICKS,
                p95 / 1_000_000.0);
        assertTrue(
                p95 <= MAX_P95_NANOS,
                () -> "500-node Lua scheduler p95 was " + p95 / 1_000_000.0 + "ms");
    }
}
