package dev.propulsionteam.computed.lua.node;

import java.util.Locale;

public enum FieldType {
    NUMBER,
    TEXT,
    BOOLEAN,
    CHOICE,
    COLOR,
    DIRECTION,
    ITEM;

    public static FieldType parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new LuaDefinitionException("Unknown field type: " + value);
        }
    }
}
