package dev.propulsionteam.computed.lua.node;

import java.util.Locale;

public enum NodeStyle {
    STANDARD,
    COMPACT,
    SOURCE,
    SINK;

    public static NodeStyle parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new LuaDefinitionException("Unknown node style: " + value);
        }
    }
}
