package dev.propulsionteam.computed.client.editor.lua;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import java.util.List;

public record LuaEditorSnapshot(
        String source,
        LuaNodeDefinition currentDefinition,
        LuaNodeDefinition lastValidDefinition,
        boolean compiling,
        boolean stalePreview,
        List<ComputedDiagnostic> diagnostics) {

    public LuaEditorSnapshot {
        source = source == null ? "" : source;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
