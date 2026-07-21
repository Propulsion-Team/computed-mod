package dev.propulsionteam.computed.internal.node.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WConnectionTest {
    @Test
    void stablePortIdentitySurvivesSchemaRemapAndWaypointEdits() {
        WConnection connection = new WConnection(
                new UUID(0L, 1L),
                0,
                new UUID(0L, 2L),
                1,
                new int[] {10},
                new int[] {20},
                "output.signal",
                "input.signal");

        connection.resolvePins(4, 7);
        WConnection rerouted = connection.withWaypoints(new int[] {30, 40}, new int[] {50, 60});

        assertEquals("output.signal", rerouted.sourcePortKey());
        assertEquals("input.signal", rerouted.targetPortKey());
        assertEquals(4, rerouted.sourcePin());
        assertEquals(7, rerouted.targetPin());
        assertArrayEquals(new int[] {30, 40}, rerouted.waypointXs());
        assertArrayEquals(new int[] {50, 60}, rerouted.waypointYs());
        assertNotSame(connection.waypointXs(), rerouted.waypointXs());
    }
}
