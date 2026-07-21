package dev.propulsionteam.computed.api.node;

/** Receives diagnostics from graph compilation, migration, and node execution. */
@FunctionalInterface
public interface DiagnosticSink {
    DiagnosticSink NONE = ignored -> {};

    void report(NodeDiagnostic diagnostic);
}
