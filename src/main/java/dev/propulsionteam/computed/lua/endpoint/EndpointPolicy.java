package dev.propulsionteam.computed.lua.endpoint;

import java.util.Objects;

public record EndpointPolicy(
        ExecutionSide executionSide,
        boolean yielding,
        boolean sideEffect,
        boolean previewAvailable) {

    public EndpointPolicy {
        Objects.requireNonNull(executionSide, "executionSide");
    }

    public static EndpointPolicy computerThread(boolean sideEffect, boolean previewAvailable) {
        return new EndpointPolicy(ExecutionSide.COMPUTER_THREAD, false, sideEffect, previewAvailable);
    }

    public enum ExecutionSide {
        COMPUTER_THREAD,
        SERVER_THREAD
    }
}
