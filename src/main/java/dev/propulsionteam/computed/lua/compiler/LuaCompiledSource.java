package dev.propulsionteam.computed.lua.compiler;

import java.util.Objects;
import org.luaj.vm2.Prototype;

public record LuaCompiledSource(int apiVersion, String sourceHash, Prototype prototype) {
    public LuaCompiledSource {
        if (apiVersion < 1) {
            throw new IllegalArgumentException("apiVersion must be positive");
        }
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(prototype, "prototype");
    }
}
