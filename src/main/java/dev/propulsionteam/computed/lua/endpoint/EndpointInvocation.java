package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.luaj.vm2.LuaValue;

public record EndpointInvocation(
        UUID computerId,
        UUID nodeId,
        String target,
        List<LuaValue> arguments,
        boolean preview,
        Object host) {

    public EndpointInvocation {
        Objects.requireNonNull(computerId, "computerId");
        Objects.requireNonNull(nodeId, "nodeId");
        target = target == null ? "" : target;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
