package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ComputedEndpoints {
    private static final Map<String, EndpointDefinition> ENDPOINTS = new ConcurrentHashMap<>();

    private ComputedEndpoints() {}

    public static EndpointDefinition register(String id, Consumer<EndpointBuilder> registration) {
        EndpointBuilder builder = new EndpointBuilder(id);
        registration.accept(builder);
        EndpointDefinition definition = builder.build();
        EndpointDefinition previous = ENDPOINTS.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Endpoint is already registered: " + definition.id());
        }
        return definition;
    }

    public static Optional<EndpointDefinition> find(String id) {
        return Optional.ofNullable(ENDPOINTS.get(id));
    }

    public static List<EndpointDefinition> definitions() {
        return ENDPOINTS.values().stream()
                .sorted(java.util.Comparator.comparing(EndpointDefinition::id))
                .toList();
    }

    static void clearForTests() {
        ENDPOINTS.clear();
    }
}
