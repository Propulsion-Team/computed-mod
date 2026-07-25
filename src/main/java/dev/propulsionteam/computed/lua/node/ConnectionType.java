package dev.propulsionteam.computed.lua.node;

import java.util.Locale;

public enum ConnectionType {
    NUMBER,
    BOOLEAN,
    STRING,
    EVENT,
    WIDGET,
    TABLE;

    public static ConnectionType parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new LuaDefinitionException("Unknown connection type: " + value);
        }
    }
}
