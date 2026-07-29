package dev.propulsionteam.computed.lua.endpoint;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public interface ServerEndpointExecutor {
    CompletionStage<EndpointResult> submitServerEndpoint(
            Callable<EndpointResult> endpointCall);
}
