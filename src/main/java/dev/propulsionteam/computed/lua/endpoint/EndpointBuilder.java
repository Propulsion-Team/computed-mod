package dev.propulsionteam.computed.lua.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class EndpointBuilder {
    private final String id;
    private final Map<String, EndpointMethod> methods = new LinkedHashMap<>();

    EndpointBuilder(String id) {
        this.id = EndpointIds.requireNamespaced(id, "endpoint");
    }

    public EndpointBuilder method(
            String methodId,
            EndpointSignature signature,
            EndpointPolicy policy,
            EndpointHandler handler) {
        return method(methodId, signature, policy, handler, null, "");
    }

    public EndpointBuilder method(
            String methodId,
            EndpointSignature signature,
            EndpointPolicy policy,
            EndpointHandler handler,
            Function<EndpointInvocation, EndpointResult.Immediate> previewFixture,
            String documentation) {
        EndpointMethod method =
                new EndpointMethod(methodId, signature, policy, handler, previewFixture, documentation);
        if (methods.putIfAbsent(method.id(), method) != null) {
            throw new IllegalArgumentException("Duplicate endpoint method: " + id + '/' + method.id());
        }
        return this;
    }

    EndpointDefinition build() {
        return new EndpointDefinition(id, methods);
    }
}
