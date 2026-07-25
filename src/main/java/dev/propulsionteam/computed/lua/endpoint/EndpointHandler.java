package dev.propulsionteam.computed.lua.endpoint;

@FunctionalInterface
public interface EndpointHandler {
    EndpointResult invoke(EndpointInvocation invocation) throws Exception;
}
