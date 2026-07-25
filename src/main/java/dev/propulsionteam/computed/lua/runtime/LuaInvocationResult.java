package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

public record LuaInvocationResult(
        LuaNodeStatus status,
        Map<String, LuaValue> outputs,
        List<ComputedDiagnostic> diagnostics) {

    public LuaInvocationResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
