package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.luaj.vm2.LuaValue;

public record LuaGraphTickResult(
        Map<UUID, Map<String, LuaValue>> outputs,
        List<ComputedDiagnostic> diagnostics,
        long graphSteps) {

    public LuaGraphTickResult {
        Map<UUID, Map<String, LuaValue>> copied = new LinkedHashMap<>();
        if (outputs != null) {
            outputs.forEach((node, values) -> copied.put(node, Map.copyOf(values)));
        }
        outputs = java.util.Collections.unmodifiableMap(copied);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
