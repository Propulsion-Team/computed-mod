package dev.propulsionteam.computed.lua.node;

import java.util.Locale;

public enum FieldControl {
    VALUE,
    SLIDER;

    public static FieldControl parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new LuaDefinitionException("Unknown field control: " + value);
        }
    }
}
