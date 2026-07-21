package dev.propulsionteam.computed.internal.node.api;

import java.util.Arrays;
import java.util.UUID;

/**
 * A directed edge from one node's output pin to another node's input pin.
 *
 * <p>The integer positions are a transitional rendering/runtime cache. Stable port keys remain the
 * connection identity when a property-dependent schema inserts, removes, or reorders pins.
 */
public final class WConnection {
    private static final int[] EMPTY = new int[0];

    private final UUID sourceNode;
    private int sourcePin;
    private final UUID targetNode;
    private int targetPin;
    private final int[] waypointXs;
    private final int[] waypointYs;
    private final String sourcePortKey;
    private final String targetPortKey;

    public WConnection(
            UUID sourceNode,
            int sourcePin,
            UUID targetNode,
            int targetPin,
            int[] waypointXs,
            int[] waypointYs) {
        this(sourceNode, sourcePin, targetNode, targetPin, waypointXs, waypointYs, null, null);
    }

    public WConnection(
            UUID sourceNode,
            int sourcePin,
            UUID targetNode,
            int targetPin,
            int[] waypointXs,
            int[] waypointYs,
            String sourcePortKey,
            String targetPortKey) {
        this.sourceNode = sourceNode;
        this.sourcePin = sourcePin;
        this.targetNode = targetNode;
        this.targetPin = targetPin;
        if (waypointXs == null || waypointYs == null || waypointXs.length == 0 || waypointXs.length != waypointYs.length) {
            this.waypointXs = EMPTY;
            this.waypointYs = EMPTY;
        } else {
            this.waypointXs = Arrays.copyOf(waypointXs, waypointXs.length);
            this.waypointYs = Arrays.copyOf(waypointYs, waypointYs.length);
        }
        this.sourcePortKey = normalizeKey(sourcePortKey);
        this.targetPortKey = normalizeKey(targetPortKey);
    }

    public static WConnection withoutWaypoints(UUID sourceNode, int sourcePin, UUID targetNode, int targetPin) {
        return new WConnection(sourceNode, sourcePin, targetNode, targetPin, EMPTY, EMPTY);
    }

    public UUID sourceNode() {
        return sourceNode;
    }

    public int sourcePin() {
        return sourcePin;
    }

    public UUID targetNode() {
        return targetNode;
    }

    public int targetPin() {
        return targetPin;
    }

    public int[] waypointXs() {
        return waypointXs;
    }

    public int[] waypointYs() {
        return waypointYs;
    }

    public String sourcePortKey() {
        return sourcePortKey;
    }

    public String targetPortKey() {
        return targetPortKey;
    }

    void resolvePins(int sourcePin, int targetPin) {
        this.sourcePin = sourcePin;
        this.targetPin = targetPin;
    }

    public WConnection withWaypoints(int[] xs, int[] ys) {
        return new WConnection(
                sourceNode, sourcePin, targetNode, targetPin, xs, ys, sourcePortKey, targetPortKey);
    }

    public WConnection withStablePorts(String sourcePortKey, String targetPortKey) {
        return new WConnection(
                sourceNode,
                sourcePin,
                targetNode,
                targetPin,
                waypointXs,
                waypointYs,
                sourcePortKey,
                targetPortKey);
    }

    private static String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key;
    }
}
