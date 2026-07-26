package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntegrationLuaLibrary {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("computed:cc_input", "computed/lua/nodes/integration/computercraft/input.lua"),
            new Entry("computed:cc_output", "computed/lua/nodes/integration/computercraft/output.lua"),
            new Entry("computed:create_kinetic", "computed/lua/nodes/integration/create/kinetic.lua"),
            new Entry("computed:create_link_receiver", "computed/lua/nodes/integration/create/link_receiver.lua"),
            new Entry("computed:create_link_sender", "computed/lua/nodes/integration/create/link_sender.lua"));

    private IntegrationLuaLibrary() {}

    public static Map<String, LuaDefinitionSource> load() {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            LuaDefinitionSource source = new LuaDefinitionSource(
                    1,
                    entry.id(),
                    read(entry.resource()),
                    "",
                    LuaDefinitionSource.Origin.INTEGRATION);
            definitions.put(source.id(), source);
        }
        return java.util.Collections.unmodifiableMap(definitions);
    }

    public static String unavailableReason(String definitionId) {
        if (definitionId.startsWith("computed:cc_")
                && !net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            return "CC:Tweaked is not installed";
        }
        if (definitionId.startsWith("computed:create_")
                && !net.neoforged.fml.ModList.get().isLoaded("create")) {
            return "Create is not installed";
        }
        return "";
    }

    private static String read(String path) {
        try (InputStream stream = IntegrationLuaLibrary.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing integration Lua definition: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read integration Lua definition: " + path, exception);
        }
    }

    private record Entry(String id, String resource) {}
}
