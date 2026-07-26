package dev.propulsionteam.computed.lua.node;

import java.util.regex.Pattern;

final class LuaSchemaNames {
    private static final Pattern STABLE_ID = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Pattern DEFINITION_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private LuaSchemaNames() {}

    static String requireStableId(String value, String kind) {
        if (value == null || !STABLE_ID.matcher(value).matches()) {
            throw new LuaDefinitionException("Invalid " + kind + " id: " + value);
        }
        return value;
    }

    static String requireDefinitionId(String value) {
        if (value == null || value.length() > 128 || !DEFINITION_ID.matcher(value).matches()) {
            throw new LuaDefinitionException("Invalid node definition id: " + value);
        }
        return value;
    }
}
