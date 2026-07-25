package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LuaDefinitionFiles {
    private LuaDefinitionFiles() {}

    public static Path export(Path configDirectory, LuaDefinitionSource definition) throws IOException {
        Path root = configDirectory.toAbsolutePath().normalize().resolve("computed").resolve("nodes");
        String fileName = definition.id().replace(':', '_').replace('/', '_') + ".lua";
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Definition path escapes the Computed node directory");
        }
        Files.createDirectories(root);
        Files.writeString(target, definition.source(), StandardCharsets.UTF_8);
        return target;
    }

    public static String importSource(Path configDirectory, String fileName) throws IOException {
        Path root = configDirectory.toAbsolutePath().normalize().resolve("computed").resolve("nodes");
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root) || !target.getFileName().toString().endsWith(".lua")) {
            throw new IllegalArgumentException("Lua import must stay inside config/computed/nodes");
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }
}
