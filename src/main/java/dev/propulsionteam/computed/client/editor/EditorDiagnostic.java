package dev.propulsionteam.computed.client.editor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable diagnostic presented on an editor entity and in the diagnostics panel. */
public record EditorDiagnostic(
        DiagnosticTarget target, Severity severity, String code, String message, Map<String, String> metadata) {
    public EditorDiagnostic {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Diagnostic code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("Diagnostic message must not be blank");
        }
        Objects.requireNonNull(metadata, "metadata");
        LinkedHashMap<String, String> metadataCopy = new LinkedHashMap<>();
        metadata.forEach((key, value) -> metadataCopy.put(
                Objects.requireNonNull(key, "metadata key"), Objects.requireNonNull(value, "metadata value")));
        metadata = Collections.unmodifiableMap(metadataCopy);
    }

    public EditorDiagnostic(DiagnosticTarget target, Severity severity, String code, String message) {
        this(target, severity, code, message, Map.of());
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
