package dev.propulsionteam.computed.client.editor.lua;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Phase;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Severity;
import dev.propulsionteam.computed.lua.compiler.LuaCompilationException;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.LuaDefinitionException;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.List;

public final class LuaEditorSession {
    public static final long DEBOUNCE_MILLIS = 250;

    private final LuaSourceCompiler compiler = new LuaSourceCompiler();
    private final LuaDefinitionLoader loader = new LuaDefinitionLoader();
    private String source = "";
    private long changedAt;
    private boolean dirty;
    private LuaNodeDefinition currentDefinition;
    private LuaNodeDefinition lastValidDefinition;
    private List<ComputedDiagnostic> diagnostics = List.of();

    public void sourceChanged(String source, long nowMillis) {
        this.source = source == null ? "" : source;
        changedAt = nowMillis;
        dirty = true;
        currentDefinition = null;
    }

    public boolean update(long nowMillis) {
        if (!dirty || nowMillis - changedAt < DEBOUNCE_MILLIS) {
            return false;
        }
        dirty = false;
        try {
            var compiled = compiler.compile(1, source);
            currentDefinition = loader.load(compiled, new LuaSandbox());
            lastValidDefinition = currentDefinition;
            diagnostics = List.of();
        } catch (LuaCompilationException exception) {
            currentDefinition = null;
            diagnostics = List.of(diagnostic(Phase.COMPILE, "compile_error", exception.getMessage()));
        } catch (LuaDefinitionException exception) {
            currentDefinition = null;
            diagnostics = List.of(diagnostic(Phase.DEFINITION, "definition_error", exception.getMessage()));
        }
        return true;
    }

    public LuaEditorSnapshot snapshot() {
        return new LuaEditorSnapshot(
                source,
                currentDefinition,
                lastValidDefinition,
                dirty,
                currentDefinition == null && lastValidDefinition != null,
                diagnostics);
    }

    private static ComputedDiagnostic diagnostic(Phase phase, String code, String message) {
        return new ComputedDiagnostic(Severity.ERROR, phase, code, message, null, null, null);
    }
}
