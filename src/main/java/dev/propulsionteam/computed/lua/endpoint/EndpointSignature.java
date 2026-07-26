package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;

public record EndpointSignature(List<EndpointType> arguments, List<EndpointType> returns, boolean variadic) {
    public EndpointSignature {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        returns = returns == null ? List.of() : List.copyOf(returns);
    }

    public static EndpointSignature of(List<EndpointType> arguments, List<EndpointType> returns) {
        return new EndpointSignature(arguments, returns, false);
    }
}
