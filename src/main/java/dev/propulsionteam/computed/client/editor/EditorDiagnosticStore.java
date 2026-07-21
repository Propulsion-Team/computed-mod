package dev.propulsionteam.computed.client.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable diagnostic store keyed by node, connection, or the editor itself.
 *
 * <p>Mutation-style methods return a new store, allowing a render pass to retain a consistent
 * snapshot while compilation or save validation publishes a replacement.
 */
public final class EditorDiagnosticStore {
    private static final EditorDiagnosticStore EMPTY = new EditorDiagnosticStore(Map.of());

    private final Map<DiagnosticTarget, List<EditorDiagnostic>> byTarget;
    private final List<EditorDiagnostic> all;

    private EditorDiagnosticStore(Map<DiagnosticTarget, List<EditorDiagnostic>> source) {
        LinkedHashMap<DiagnosticTarget, List<EditorDiagnostic>> copy = new LinkedHashMap<>();
        List<EditorDiagnostic> flattened = new ArrayList<>();
        source.forEach((target, diagnostics) -> {
            List<EditorDiagnostic> targetCopy = List.copyOf(diagnostics);
            if (!targetCopy.isEmpty()) {
                copy.put(target, targetCopy);
                flattened.addAll(targetCopy);
            }
        });
        this.byTarget = Collections.unmodifiableMap(copy);
        this.all = List.copyOf(flattened);
    }

    public static EditorDiagnosticStore empty() {
        return EMPTY;
    }

    /**
     * Adds a diagnostic, replacing an existing diagnostic with the same target and code. This keeps
     * repeated compiler passes from accumulating duplicate messages.
     */
    public EditorDiagnosticStore with(EditorDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        LinkedHashMap<DiagnosticTarget, List<EditorDiagnostic>> replacement = mutableCopy();
        List<EditorDiagnostic> diagnostics = new ArrayList<>(replacement.getOrDefault(diagnostic.target(), List.of()));
        diagnostics.removeIf(existing -> existing.code().equals(diagnostic.code()));
        diagnostics.add(diagnostic);
        replacement.put(diagnostic.target(), diagnostics);
        return new EditorDiagnosticStore(replacement);
    }

    public EditorDiagnosticStore withAll(Collection<EditorDiagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        EditorDiagnosticStore result = this;
        for (EditorDiagnostic diagnostic : diagnostics) {
            result = result.with(diagnostic);
        }
        return result;
    }

    /** Replaces all diagnostics for one target. Every replacement must name that target. */
    public EditorDiagnosticStore replace(
            DiagnosticTarget target, Collection<? extends EditorDiagnostic> diagnostics) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(diagnostics, "diagnostics");
        LinkedHashMap<String, EditorDiagnostic> byCode = new LinkedHashMap<>();
        for (EditorDiagnostic diagnostic : diagnostics) {
            Objects.requireNonNull(diagnostic, "diagnostic");
            if (!target.equals(diagnostic.target())) {
                throw new IllegalArgumentException("Replacement diagnostic belongs to a different target");
            }
            byCode.put(diagnostic.code(), diagnostic);
        }

        LinkedHashMap<DiagnosticTarget, List<EditorDiagnostic>> replacement = mutableCopy();
        if (byCode.isEmpty()) {
            replacement.remove(target);
        } else {
            replacement.put(target, new ArrayList<>(byCode.values()));
        }
        return replacement.isEmpty() ? EMPTY : new EditorDiagnosticStore(replacement);
    }

    public EditorDiagnosticStore without(DiagnosticTarget target, String code) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(code, "code");
        List<EditorDiagnostic> existing = byTarget.get(target);
        if (existing == null || existing.stream().noneMatch(diagnostic -> diagnostic.code().equals(code))) {
            return this;
        }
        List<EditorDiagnostic> retained = existing.stream()
                .filter(diagnostic -> !diagnostic.code().equals(code))
                .toList();
        return replace(target, retained);
    }

    public EditorDiagnosticStore without(DiagnosticTarget target) {
        Objects.requireNonNull(target, "target");
        if (!byTarget.containsKey(target)) {
            return this;
        }
        LinkedHashMap<DiagnosticTarget, List<EditorDiagnostic>> replacement = mutableCopy();
        replacement.remove(target);
        return replacement.isEmpty() ? EMPTY : new EditorDiagnosticStore(replacement);
    }

    public List<EditorDiagnostic> forTarget(DiagnosticTarget target) {
        return byTarget.getOrDefault(Objects.requireNonNull(target, "target"), List.of());
    }

    public List<EditorDiagnostic> forNode(String nodeKey) {
        return forTarget(DiagnosticTarget.node(nodeKey));
    }

    public List<EditorDiagnostic> forConnection(String connectionKey) {
        return forTarget(DiagnosticTarget.connection(connectionKey));
    }

    public List<EditorDiagnostic> all() {
        return all;
    }

    public Set<DiagnosticTarget> targets() {
        return byTarget.keySet();
    }

    public boolean hasErrors() {
        return all.stream().anyMatch(diagnostic -> diagnostic.severity() == EditorDiagnostic.Severity.ERROR);
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public int size() {
        return all.size();
    }

    private LinkedHashMap<DiagnosticTarget, List<EditorDiagnostic>> mutableCopy() {
        return new LinkedHashMap<>(byTarget);
    }
}
