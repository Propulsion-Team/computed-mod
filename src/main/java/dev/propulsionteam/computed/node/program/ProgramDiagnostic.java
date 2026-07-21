package dev.propulsionteam.computed.node.program;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Persistable migration, validation, or compilation diagnostic. Location ids may be {@code null}. */
public record ProgramDiagnostic(
        Severity severity,
        String code,
        String message,
        UUID graphId,
        UUID nodeId,
        UUID connectionId,
        CompoundTag details) {

    public ProgramDiagnostic {
        Objects.requireNonNull(severity, "severity");
        code = code == null || code.isBlank() ? "unknown" : code;
        message = message == null ? "" : message;
        details = details == null ? new CompoundTag() : details.copy();
    }

    public ProgramDiagnostic(Severity severity, String code, String message, UUID graphId, UUID nodeId, UUID connectionId) {
        this(severity, code, message, graphId, nodeId, connectionId, new CompoundTag());
    }

    @Override
    public CompoundTag details() {
        return details.copy();
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
