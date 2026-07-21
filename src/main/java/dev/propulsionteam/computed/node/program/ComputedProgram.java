package dev.propulsionteam.computed.node.program;

import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Versioned root object containing the executable graph and its function library. */
public record ComputedProgram(
        long revision,
        GraphModel rootGraph,
        List<FunctionModel> functions,
        List<ProgramDiagnostic> diagnostics,
        CompoundTag metadata) {

    public static final int FORMAT_VERSION = 2;

    public ComputedProgram {
        revision = Math.max(0L, revision);
        Objects.requireNonNull(rootGraph, "rootGraph");
        functions = functions == null ? List.of() : List.copyOf(functions);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        metadata = metadata == null ? new CompoundTag() : metadata.copy();
    }

    public ComputedProgram(GraphModel rootGraph, List<FunctionModel> functions) {
        this(0L, rootGraph, functions, List.of(), new CompoundTag());
    }

    @Override
    public CompoundTag metadata() {
        return metadata.copy();
    }

    public ComputedProgram withDiagnostics(List<ProgramDiagnostic> newDiagnostics) {
        return new ComputedProgram(revision, rootGraph, functions, newDiagnostics, metadata);
    }

    public ComputedProgram withRevision(long newRevision) {
        return new ComputedProgram(newRevision, rootGraph, functions, diagnostics, metadata);
    }
}
