package dev.propulsionteam.computed.lua.endpoint;

import java.util.Objects;

public record EndpointPolicy(
        ExecutionSide executionSide,
        boolean yielding,
        boolean sideEffect,
        boolean previewAvailable) {

    public EndpointPolicy {
        Objects.requireNonNull(executionSide, "executionSide");
        if (executionSide == ExecutionSide.SERVER_THREAD && !yielding) {
            throw new IllegalArgumentException("Server-thread endpoints must yield during dispatch");
        }
    }

    public static EndpointPolicy computerThread(boolean sideEffect, boolean previewAvailable) {
        return new EndpointPolicy(ExecutionSide.COMPUTER_THREAD, false, sideEffect, previewAvailable);
    }

    public static EndpointPolicy serverThread(boolean sideEffect, boolean previewAvailable) {
        return new EndpointPolicy(ExecutionSide.SERVER_THREAD, true, sideEffect, previewAvailable);
    }

    public enum ExecutionSide {
        COMPUTER_THREAD,
        SERVER_THREAD
    }
}
