package dev.propulsionteam.computed.lua.node;

import java.util.Objects;
import org.luaj.vm2.LuaValue;

public record LuaPortSchema(String id, ConnectionType type, boolean required, LuaValue defaultValue) {
    public LuaPortSchema {
        id = LuaSchemaNames.requireStableId(id, "port");
        Objects.requireNonNull(type, "type");
        defaultValue = defaultValue == null ? LuaValue.NIL : defaultValue;
    }
}
