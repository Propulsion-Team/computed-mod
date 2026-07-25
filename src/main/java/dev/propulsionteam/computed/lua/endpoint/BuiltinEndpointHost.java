package dev.propulsionteam.computed.lua.endpoint;

public interface BuiltinEndpointHost {
    double worldTime();

    void runCommand(String command);
}
