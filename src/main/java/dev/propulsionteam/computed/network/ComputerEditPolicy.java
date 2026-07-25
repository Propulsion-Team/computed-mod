package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.graph.ComputedProgramV3;

public final class ComputerEditPolicy {
    public static final double MAX_DISTANCE_SQ = 16.0 * 16.0;
    public static final int MAX_NODES = 4096;
    public static final int MAX_CONNECTIONS = 20_000;
    public static final int MAX_PROGRAM_BYTES = 4 * 1024 * 1024;

    private ComputerEditPolicy() {}

    public static String access(double distanceSquared, boolean mayBuild, boolean mayInteract) {
        if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_DISTANCE_SQ) {
            return "computer is too far away";
        }
        if (!mayBuild || !mayInteract) {
            return "you do not have permission to edit this computer";
        }
        return null;
    }

    public static String revision(long authoritative, long expected) {
        return authoritative == expected
                ? null
                : "stale editor revision (expected " + authoritative + ", received " + expected + ")";
    }

    public static String encodedSize(int bytes) {
        if (bytes < 0) {
            return "program NBT could not be measured safely";
        }
        return bytes > MAX_PROGRAM_BYTES
                ? "program exceeds the encoded size limit of " + MAX_PROGRAM_BYTES + " bytes"
                : null;
    }

    public static String programShape(ComputedProgramV3 candidate) {
        if (candidate.rootGraph().nodes().size() > MAX_NODES) {
            return "program exceeds the node limit of " + MAX_NODES;
        }
        if (candidate.rootGraph().connections().size() > MAX_CONNECTIONS) {
            return "program exceeds the connection limit of " + MAX_CONNECTIONS;
        }
        if (candidate.library().size() > ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS) {
            return "program exceeds the embedded definition limit of "
                    + ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS;
        }
        return null;
    }
}
