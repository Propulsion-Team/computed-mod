package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.util.List;

public record LuaLibraryUpdate(
        Status status,
        LuaDefinitionSource definition,
        List<String> retainedPorts,
        List<String> removedPorts,
        String message) {

    public LuaLibraryUpdate {
        retainedPorts = retainedPorts == null ? List.of() : List.copyOf(retainedPorts);
        removedPorts = removedPorts == null ? List.of() : List.copyOf(removedPorts);
        message = message == null ? "" : message;
    }

    public enum Status {
        ADDED,
        UNCHANGED,
        REPLACED,
        CONFIRMATION_REQUIRED
    }
}
