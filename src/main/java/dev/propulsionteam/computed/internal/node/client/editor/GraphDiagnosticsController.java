package dev.propulsionteam.computed.internal.node.client.editor;

import dev.propulsionteam.computed.client.editor.DiagnosticTarget;
import dev.propulsionteam.computed.client.editor.EditorDiagnostic;
import dev.propulsionteam.computed.client.editor.EditorDiagnosticStore;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import java.util.List;
import java.util.UUID;

/** Aggregates graph diagnostics and transactional-save failures into editor-facing targets. */
public final class GraphDiagnosticsController {
    private EditorDiagnosticStore diagnostics = EditorDiagnosticStore.empty();
    private WGraph cachedGraph;
    private int cachedSignature;
    private String saveFailure = "";

    public EditorDiagnosticStore diagnostics() {
        return diagnostics;
    }

    public void invalidate() {
        cachedGraph = null;
    }

    public void setSaveFailure(String message) {
        String normalized = message == null ? "" : message.trim();
        if (!normalized.equals(saveFailure)) {
            saveFailure = normalized;
            invalidate();
        }
    }

    public void clearSaveFailure() {
        setSaveFailure("");
    }

    public EditorDiagnosticStore refresh(WGraph graph) {
        List<WGraph.GraphDiagnostic> graphDiagnostics = graph.getDiagnostics();
        int signature = 31 * graphDiagnostics.hashCode() + saveFailure.hashCode();
        if (cachedGraph == graph && cachedSignature == signature) {
            return diagnostics;
        }

        EditorDiagnosticStore updated = EditorDiagnosticStore.empty();
        for (WGraph.GraphDiagnostic diagnostic : graphDiagnostics) {
            EditorDiagnostic.Severity severity = diagnostic.severity() == WGraph.DiagnosticSeverity.ERROR
                    ? EditorDiagnostic.Severity.ERROR
                    : EditorDiagnostic.Severity.WARNING;
            if (diagnostic.nodeIds().isEmpty()) {
                updated = updated.with(new EditorDiagnostic(
                        DiagnosticTarget.editor(), severity, diagnostic.code(), diagnostic.message()));
            } else {
                for (UUID nodeId : diagnostic.nodeIds()) {
                    updated = updated.with(new EditorDiagnostic(
                            DiagnosticTarget.node(nodeId), severity, diagnostic.code(), diagnostic.message()));
                }
            }
        }
        if (!saveFailure.isEmpty()) {
            updated = updated.with(new EditorDiagnostic(
                    DiagnosticTarget.editor(),
                    EditorDiagnostic.Severity.ERROR,
                    "computed.save.rejected",
                    saveFailure));
        }
        diagnostics = updated;
        cachedGraph = graph;
        cachedSignature = signature;
        return diagnostics;
    }
}
