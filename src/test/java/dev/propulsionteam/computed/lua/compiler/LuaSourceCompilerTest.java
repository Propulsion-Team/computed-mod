package dev.propulsionteam.computed.lua.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LuaSourceCompilerTest {
    @BeforeEach
    void resetCache() {
        LuaSourceCompiler.clearCache();
    }

    @Test
    void cachesPrototypesByApiVersionAndSourceHash() {
        LuaSourceCompiler firstCompiler = new LuaSourceCompiler();
        LuaSourceCompiler secondCompiler = new LuaSourceCompiler();

        LuaCompiledSource first = firstCompiler.compile(1, "return 42");
        LuaCompiledSource second = secondCompiler.compile(1, "return 42");
        LuaCompiledSource otherApi = secondCompiler.compile(2, "return 42");

        assertEquals(first.sourceHash(), second.sourceHash());
        assertSame(first.prototype(), second.prototype());
        assertNotSame(first.prototype(), otherApi.prototype());
        assertEquals(2, LuaSourceCompiler.cachedPrototypeCount());
    }

    @Test
    void rejectsMalformedAndOversizedSources() {
        LuaSourceCompiler compiler = new LuaSourceCompiler();
        assertThrows(LuaCompilationException.class, () -> compiler.compile(1, "local ="));
        assertThrows(
                LuaCompilationException.class,
                () -> compiler.compile(1, "x".repeat(LuaSourceCompiler.MAX_SOURCE_BYTES + 1)));
    }
}
