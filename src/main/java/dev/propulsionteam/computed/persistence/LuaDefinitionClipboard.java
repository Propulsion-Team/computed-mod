package dev.propulsionteam.computed.persistence;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;

public final class LuaDefinitionClipboard {
    private LuaDefinitionClipboard() {}

    public static String exportSource(LuaDefinitionSource definition) {
        return definition.source();
    }

    public static String importSource(String clipboard) {
        String source = clipboard == null ? "" : clipboard.strip();
        if (source.startsWith("CMP1") || source.startsWith("CMP2")) {
            throw new IllegalArgumentException("Legacy Computed clipboard programs are not supported");
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("Clipboard does not contain Lua source");
        }
        return source;
    }
}
