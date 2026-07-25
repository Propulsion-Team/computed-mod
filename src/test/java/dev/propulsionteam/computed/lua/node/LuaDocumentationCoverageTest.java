package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LuaDocumentationCoverageTest {
    @Test
    void documentsEveryRegisteredLuaAndEndpointMethodHeading() throws IOException {
        String luaReference = Files.readString(Path.of("docs/lua/lua-api-reference.md"));
        List<String> luaHeadings = List.of(
                "computed.node(apiVersion, id, title)",
                "node:category(name)",
                "node:style(style)",
                "node:input(id, type, options)",
                "node:output(id, type, options)",
                "node:field(id, fieldType, options)",
                "node:state(id, defaultValue)",
                "node:execution(policy)",
                "node:on_run(callback)",
                "node:on_event(eventName, callback)",
                "ctx:input(id)",
                "ctx:output(id, value)",
                "ctx:field(id)",
                "ctx:state(id)",
                "ctx:set_state(id, value)",
                "ctx:endpoint(id, target)",
                "ctx:emit(eventName, ...)",
                "ctx:tick()",
                "ctx:graph_step()",
                "ctx:is_preview()",
                "endpoint:methods()",
                "endpoint:call(methodName, ...)");
        luaHeadings.forEach(heading -> assertTrue(luaReference.contains("## " + heading), heading));

        BuiltinEndpoints.register();
        String endpointReference = Files.readString(Path.of("docs/lua/endpoint-api.md"));
        ComputedEndpoints.definitions().stream()
                .filter(endpoint -> endpoint.id().startsWith("computed:"))
                .forEach(endpoint -> endpoint.methods().keySet().forEach(method ->
                        assertTrue(endpointReference.contains("## " + endpoint.id() + '/' + method))));
    }
}
