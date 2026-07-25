package dev.propulsionteam.computed.lua.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;

public record EndpointDefinition(String id, Map<String, EndpointMethod> methods) {
    public EndpointDefinition {
        id = EndpointIds.requireNamespaced(id, "endpoint");
        methods = methods == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(methods));
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Endpoint " + id + " must register at least one method");
        }
    }
}
