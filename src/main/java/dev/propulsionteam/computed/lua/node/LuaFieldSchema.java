package dev.propulsionteam.computed.lua.node;

import java.util.List;
import java.util.Objects;
import org.luaj.vm2.LuaValue;

public record LuaFieldSchema(
        String id,
        FieldType type,
        LuaValue defaultValue,
        List<String> choices,
        Double minimum,
        Double maximum) {

    public LuaFieldSchema {
        id = LuaSchemaNames.requireStableId(id, "field");
        Objects.requireNonNull(type, "type");
        defaultValue = defaultValue == null ? LuaValue.NIL : defaultValue;
        choices = choices == null ? List.of() : List.copyOf(choices);
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new LuaDefinitionException("Field " + id + " has a minimum greater than its maximum");
        }
        if (type == FieldType.CHOICE && choices.isEmpty()) {
            throw new LuaDefinitionException("Choice field " + id + " must declare at least one choice");
        }
    }
}
