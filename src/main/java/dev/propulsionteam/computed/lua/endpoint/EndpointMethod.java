package dev.propulsionteam.computed.lua.endpoint;

import java.util.Objects;
import java.util.function.Function;

public record EndpointMethod(
        String id,
        EndpointSignature signature,
        EndpointPolicy policy,
        EndpointHandler handler,
        Function<EndpointInvocation, EndpointResult.Immediate> previewFixture,
        String documentation) {

    public EndpointMethod {
        id = EndpointIds.requireMember(id, "method");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(handler, "handler");
        documentation = documentation == null ? "" : documentation;
        if (policy.previewAvailable() && previewFixture == null) {
            throw new IllegalArgumentException("Preview-enabled endpoint method " + id + " requires a fixture");
        }
    }
}
