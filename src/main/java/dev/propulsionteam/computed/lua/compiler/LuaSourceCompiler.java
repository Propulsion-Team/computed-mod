package dev.propulsionteam.computed.lua.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.LuaC;

public final class LuaSourceCompiler {
    public static final int MAX_SOURCE_BYTES = 64 * 1024;

    private static final ConcurrentMap<CacheKey, Prototype> PROTOTYPES = new ConcurrentHashMap<>();
    private final Globals compilerGlobals;

    public LuaSourceCompiler() {
        compilerGlobals = new Globals();
        LuaC.install(compilerGlobals);
    }

    public LuaCompiledSource compile(int apiVersion, String source) {
        if (apiVersion < 1) {
            throw new IllegalArgumentException("apiVersion must be positive");
        }
        Objects.requireNonNull(source, "source");
        byte[] encoded = source.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_SOURCE_BYTES) {
            throw new LuaCompilationException(
                    "Lua source exceeds the " + MAX_SOURCE_BYTES + "-byte limit",
                    new IllegalArgumentException("source too large"));
        }
        String hash = sha256(encoded);
        CacheKey key = new CacheKey(apiVersion, hash);
        try {
            Prototype prototype = PROTOTYPES.computeIfAbsent(key, ignored -> compilePrototype(source, hash));
            return new LuaCompiledSource(apiVersion, hash, prototype);
        } catch (CompilationFailure failure) {
            throw new LuaCompilationException(failure.getCause().getMessage(), failure.getCause());
        }
    }

    public static int cachedPrototypeCount() {
        return PROTOTYPES.size();
    }

    static void clearCache() {
        PROTOTYPES.clear();
    }

    private Prototype compilePrototype(String source, String hash) {
        try {
            return compilerGlobals.compilePrototype(
                    new java.io.StringReader(source),
                    "@computed/" + hash.substring(0, 12) + ".lua");
        } catch (IOException | LuaError exception) {
            throw new CompilationFailure(exception);
        }
    }

    private static String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CacheKey(int apiVersion, String hash) {}

    private static final class CompilationFailure extends RuntimeException {
        private CompilationFailure(Throwable cause) {
            super(cause);
        }
    }
}
