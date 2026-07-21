package dev.propulsionteam.computed.client.editor;

import java.util.Objects;
import java.util.UUID;

/** Stable location to which an editor diagnostic is attached. */
public record DiagnosticTarget(Kind kind, String key) {
    private static final DiagnosticTarget EDITOR = new DiagnosticTarget(Kind.EDITOR, "editor");

    public DiagnosticTarget {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Diagnostic target key must not be blank");
        }
    }

    public static DiagnosticTarget node(String key) {
        return new DiagnosticTarget(Kind.NODE, key);
    }

    public static DiagnosticTarget node(UUID key) {
        return node(Objects.requireNonNull(key, "key").toString());
    }

    public static DiagnosticTarget connection(String key) {
        return new DiagnosticTarget(Kind.CONNECTION, key);
    }

    public static DiagnosticTarget connection(UUID key) {
        return connection(Objects.requireNonNull(key, "key").toString());
    }

    /** Target for save failures and other diagnostics that cannot be localized. */
    public static DiagnosticTarget editor() {
        return EDITOR;
    }

    public enum Kind {
        NODE,
        CONNECTION,
        EDITOR
    }
}
