package dev.devce.websnodelib.api;

import java.util.Arrays;
import java.util.UUID;

/**
 * A directed edge from one node's output pin to another node's input pin.
 * Optional {@code waypointXs}/{@code waypointYs} are editor spline control points in graph space (same length).
 */
public record WConnection(
        UUID sourceNode,
        int sourcePin,
        UUID targetNode,
        int targetPin,
        int[] waypointXs,
        int[] waypointYs) {

    private static final int[] EMPTY = new int[0];

    public WConnection {
        if (waypointXs == null || waypointYs == null || waypointXs.length == 0) {
            waypointXs = EMPTY;
            waypointYs = EMPTY;
        } else if (waypointXs.length != waypointYs.length) {
            waypointXs = EMPTY;
            waypointYs = EMPTY;
        } else {
            waypointXs = Arrays.copyOf(waypointXs, waypointXs.length);
            waypointYs = Arrays.copyOf(waypointYs, waypointYs.length);
        }
    }

    public static WConnection withoutWaypoints(UUID sourceNode, int sourcePin, UUID targetNode, int targetPin) {
        return new WConnection(sourceNode, sourcePin, targetNode, targetPin, EMPTY, EMPTY);
    }

    public WConnection withWaypoints(int[] xs, int[] ys) {
        return new WConnection(sourceNode, sourcePin, targetNode, targetPin, xs, ys);
    }
}
