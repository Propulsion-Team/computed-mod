package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import java.util.List;
import java.util.UUID;

public record GraphAnalysisResult(
        List<UUID> executionOrder,
        List<List<UUID>> combinationalCycles,
        List<ComputedDiagnostic> diagnostics) {

    public GraphAnalysisResult {
        executionOrder = executionOrder == null ? List.of() : List.copyOf(executionOrder);
        combinationalCycles = combinationalCycles == null
                ? List.of()
                : combinationalCycles.stream().map(List::copyOf).toList();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean valid() {
        return diagnostics.stream()
                .noneMatch(diagnostic -> diagnostic.severity() == ComputedDiagnostic.Severity.ERROR);
    }
}
