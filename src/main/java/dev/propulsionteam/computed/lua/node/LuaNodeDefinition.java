package dev.propulsionteam.computed.lua.node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;

public record LuaNodeDefinition(
        int apiVersion,
        String id,
        String title,
        String category,
        NodeStyle style,
        LuaExecutionPolicy executionPolicy,
        List<LuaPortSchema> inputs,
        List<LuaPortSchema> outputs,
        List<LuaFieldSchema> fields,
        Map<String, LuaValue> stateDefaults,
        LuaFunction onRun,
        Map<String, LuaFunction> eventHandlers,
        String sourceHash) {

    public LuaNodeDefinition {
        if (apiVersion != 1) {
            throw new LuaDefinitionException("Unsupported Lua node API version: " + apiVersion);
        }
        id = LuaSchemaNames.requireDefinitionId(id);
        title = title == null ? "" : title.strip();
        if (title.isEmpty() || title.length() > 96) {
            throw new LuaDefinitionException("Node title must contain between 1 and 96 characters");
        }
        category = category == null || category.isBlank() ? "utility" : category;
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(executionPolicy, "executionPolicy");
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        fields = fields == null ? List.of() : List.copyOf(fields);
        stateDefaults = stateDefaults == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(stateDefaults));
        eventHandlers = eventHandlers == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(eventHandlers));
        sourceHash = sourceHash == null ? "" : sourceHash;
        validateUniqueIds(inputs, outputs, fields, stateDefaults);
        if (onRun == null && eventHandlers.isEmpty()) {
            throw new LuaDefinitionException("Node " + id + " must declare on_run or on_event");
        }
    }

    private static void validateUniqueIds(
            List<LuaPortSchema> inputs,
            List<LuaPortSchema> outputs,
            List<LuaFieldSchema> fields,
            Map<String, LuaValue> states) {
        Map<String, String> owners = new LinkedHashMap<>();
        inputs.forEach(schema -> claim(owners, schema.id(), "input"));
        outputs.forEach(schema -> claim(owners, schema.id(), "output"));
        fields.forEach(schema -> claim(owners, schema.id(), "field"));
        states.keySet().forEach(id -> claim(owners, id, "state"));
    }

    private static void claim(Map<String, String> owners, String id, String owner) {
        String previous = owners.putIfAbsent(owner + ':' + id, owner);
        if (previous != null) {
            throw new LuaDefinitionException("Duplicate " + owner + " id: " + id);
        }
    }
}
