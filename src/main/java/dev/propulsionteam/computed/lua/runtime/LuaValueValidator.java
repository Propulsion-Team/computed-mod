package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import org.luaj.vm2.LuaValue;

final class LuaValueValidator {
    private LuaValueValidator() {}

    static boolean matches(EndpointType type, LuaValue value) {
        return switch (type) {
            case ANY -> true;
            case NIL -> value.isnil();
            case NUMBER -> value.isnumber();
            case BOOLEAN -> value.isboolean();
            case STRING -> value.isstring();
            case TABLE -> value.istable();
        };
    }
}
