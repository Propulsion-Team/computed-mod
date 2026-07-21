package dev.propulsionteam.computed.api.node;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Structured diagnostic that can be attached to a graph node and, optionally, one of its ports. */
public record NodeDiagnostic(
        DiagnosticSeverity severity,
        String code,
        Component message,
        Optional<UUID> nodeId,
        Optional<String> portId) {
    private static final Pattern VALID_CODE = Pattern.compile("[a-z][a-z0-9_.-]*");

    public NodeDiagnostic {
        Objects.requireNonNull(severity, "severity");
        if (code == null || !VALID_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Diagnostic code must match " + VALID_CODE.pattern());
        }
        Objects.requireNonNull(message, "message");
        nodeId = Objects.requireNonNull(nodeId, "nodeId");
        portId = Objects.requireNonNull(portId, "portId");
    }

    public static NodeDiagnostic info(String code, Component message) {
        return create(DiagnosticSeverity.INFO, code, message);
    }

    public static NodeDiagnostic warning(String code, Component message) {
        return create(DiagnosticSeverity.WARNING, code, message);
    }

    public static NodeDiagnostic error(String code, Component message) {
        return create(DiagnosticSeverity.ERROR, code, message);
    }

    public static NodeDiagnostic create(DiagnosticSeverity severity, String code, Component message) {
        return new NodeDiagnostic(severity, code, message, Optional.empty(), Optional.empty());
    }

    public NodeDiagnostic forNode(UUID nodeId) {
        return new NodeDiagnostic(severity, code, message, Optional.of(nodeId), portId);
    }

    public NodeDiagnostic atPort(String portId) {
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be blank");
        }
        return new NodeDiagnostic(severity, code, message, nodeId, Optional.of(portId));
    }
}
