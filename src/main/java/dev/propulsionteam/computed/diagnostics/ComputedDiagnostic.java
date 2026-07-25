package dev.propulsionteam.computed.diagnostics;

import java.util.Objects;
import java.util.UUID;

public record ComputedDiagnostic(
        Severity severity,
        Phase phase,
        String code,
        String message,
        UUID nodeId,
        Integer line,
        Integer column) {

    public ComputedDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        code = code == null || code.isBlank() ? "unknown" : code;
        message = message == null ? "" : message;
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public enum Phase {
        COMPILE,
        DEFINITION,
        GRAPH,
        RUNTIME,
        ENDPOINT,
        PERSISTENCE,
        PREVIEW
    }
}
