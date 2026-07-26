package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record LuaDefinitionSource(int apiVersion, String id, String source, String hash, Origin origin) {
    public LuaDefinitionSource {
        if (apiVersion < 1) {
            throw new IllegalArgumentException("apiVersion must be positive");
        }
        if (id == null || id.isBlank() || id.length() > 128) {
            throw new IllegalArgumentException("Invalid Lua definition id: " + id);
        }
        source = source == null ? "" : source;
        if (source.getBytes(StandardCharsets.UTF_8).length > LuaSourceCompiler.MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Lua definition source exceeds 64 KiB");
        }
        String actualHash = hash(source);
        if (hash == null || hash.isBlank()) {
            hash = actualHash;
        } else if (!hash.equals(actualHash)) {
            throw new IllegalArgumentException("Lua definition hash does not match its source");
        }
        origin = origin == null ? Origin.EMBEDDED : origin;
    }

    public static LuaDefinitionSource embedded(int apiVersion, String id, String source) {
        return new LuaDefinitionSource(apiVersion, id, source, "", Origin.EMBEDDED);
    }

    private static String hash(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public enum Origin {
        BUNDLED,
        INTEGRATION,
        EMBEDDED
    }
}
