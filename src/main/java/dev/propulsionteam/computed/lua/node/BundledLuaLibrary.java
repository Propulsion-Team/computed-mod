package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BundledLuaLibrary {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("computed:add", "computed/lua/nodes/math/add.lua"),
            new Entry("computed:counter", "computed/lua/nodes/state/counter.lua"),
            new Entry("computed:world_time", "computed/lua/nodes/world/time.lua"),
            new Entry("computed:command", "computed/lua/nodes/io/command.lua"),
            new Entry("computed:text_widget", "computed/lua/nodes/widgets/text.lua"));

    private BundledLuaLibrary() {}

    public static Map<String, LuaDefinitionSource> load() {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            String source = read(entry.resource());
            LuaDefinitionSource definition = new LuaDefinitionSource(
                    1,
                    entry.id(),
                    source,
                    "",
                    LuaDefinitionSource.Origin.BUNDLED);
            definitions.put(definition.id(), definition);
        }
        return java.util.Collections.unmodifiableMap(definitions);
    }

    private static String read(String path) {
        ClassLoader loader = BundledLuaLibrary.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled Lua definition: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled Lua definition: " + path, exception);
        }
    }

    private record Entry(String id, String resource) {}
}
