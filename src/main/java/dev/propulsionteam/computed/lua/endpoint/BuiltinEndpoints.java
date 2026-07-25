package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class BuiltinEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private BuiltinEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("computed:world", endpoint -> endpoint.method(
                "time",
                EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                EndpointPolicy.computerThread(false, true),
                invocation -> {
                    BuiltinEndpointHost host = requireHost(invocation);
                    return EndpointResult.immediate(LuaValue.valueOf(host.worldTime()));
                },
                invocation -> EndpointResult.immediate(LuaValue.valueOf(6000)),
                "Returns the current world day time."));
        ComputedEndpoints.register("computed:command", endpoint -> endpoint.method(
                "run",
                EndpointSignature.of(List.of(EndpointType.STRING), List.of()),
                EndpointPolicy.computerThread(true, false),
                invocation -> {
                    BuiltinEndpointHost host = requireHost(invocation);
                    host.runCommand(invocation.arguments().getFirst().tojstring());
                    return EndpointResult.immediate();
                },
                null,
                "Runs a command through the computer host."));
        ComputedEndpoints.register("computed:widget", endpoint -> endpoint.method(
                "text",
                EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.TABLE)),
                EndpointPolicy.computerThread(false, true),
                invocation -> EndpointResult.immediate(textWidget(invocation.arguments().getFirst().tojstring())),
                invocation -> EndpointResult.immediate(textWidget(invocation.arguments().getFirst().tojstring())),
                "Creates a text widget value."));
    }

    private static BuiltinEndpointHost requireHost(EndpointInvocation invocation) {
        if (invocation.host() instanceof BuiltinEndpointHost host) {
            return host;
        }
        throw new IllegalStateException("Computer host does not provide built-in endpoint access");
    }

    private static LuaTable textWidget(String text) {
        LuaTable widget = new LuaTable();
        widget.set("type", "text");
        widget.set("text", text);
        return widget;
    }
}
