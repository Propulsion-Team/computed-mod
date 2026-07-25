package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointDefinition;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointMethod;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class LuaEndpointProxy {
    private LuaEndpointProxy() {}

    static LuaTable create(PendingLuaInvocation pending, String endpointId, String target) {
        EndpointDefinition endpoint = ComputedEndpoints.find(endpointId)
                .orElseThrow(() -> new LuaError("Unknown endpoint: " + endpointId));
        LuaTable proxy = new LuaTable();
        boolean boundProxy = endpoint.methods().containsKey("methods")
                && endpoint.methods().containsKey("call");
        proxy.set("methods", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                requireSelf(proxy, args);
                if (boundProxy) {
                    return LuaEndpointProxy.call(
                            pending,
                            endpointId,
                            target,
                            endpoint.methods().get("methods"),
                            List.of());
                }
                LuaTable methods = new LuaTable();
                int index = 1;
                for (String method : endpoint.methods().keySet()) {
                    methods.set(index++, method);
                }
                return methods;
            }
        });
        proxy.set("call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                requireSelf(proxy, args);
                if (boundProxy) {
                    List<LuaValue> arguments = new ArrayList<>();
                    for (int index = 2; index <= args.narg(); index++) {
                        arguments.add(args.arg(index));
                    }
                    return LuaEndpointProxy.call(
                            pending,
                            endpointId,
                            target,
                            endpoint.methods().get("call"),
                            arguments);
                }
                String methodId = args.arg(2).checkjstring();
                EndpointMethod method = endpoint.methods().get(methodId);
                if (method == null) {
                    throw new LuaError("Unknown endpoint method: " + endpointId + '/' + methodId);
                }
                List<LuaValue> arguments = new ArrayList<>();
                for (int index = 3; index <= args.narg(); index++) {
                    arguments.add(args.arg(index));
                }
                return LuaEndpointProxy.call(pending, endpointId, target, method, arguments);
            }
        });
        return proxy;
    }

    private static Varargs call(
            PendingLuaInvocation pending,
            String endpointId,
            String target,
            EndpointMethod method,
            List<LuaValue> arguments) {
        validateArguments(method, arguments);
        EndpointInvocation invocation =
                new EndpointInvocation(
                        pending.computerId(),
                        pending.nodeId(),
                        target,
                        arguments,
                        pending.preview(),
                        pending.endpointHost());
        EndpointResult result = LuaEndpointProxy.invoke(method, invocation, pending.preview());
        return switch (result) {
            case EndpointResult.Immediate immediate -> {
                validateReturns(method, immediate.values());
                yield values(immediate.values());
            }
            case EndpointResult.Unavailable unavailable ->
                    throw new LuaError(unavailable.reason());
            case EndpointResult.Yielded yielded -> {
                if (!method.policy().yielding()) {
                    throw new LuaError("Endpoint returned a continuation but is not declared yielding");
                }
                pending.yieldFor(yielded.continuation());
                yield pending.sandbox().globals().yield(LuaValue.NIL);
            }
        };
    }

    private static EndpointResult invoke(
            EndpointMethod method,
            EndpointInvocation invocation,
            boolean preview) {
        try {
            if (preview) {
                if (!method.policy().previewAvailable()) {
                    return EndpointResult.unavailable("Endpoint method is unavailable in previews");
                }
                return method.previewFixture().apply(invocation);
            }
            return method.handler().invoke(invocation);
        } catch (LuaError error) {
            throw error;
        } catch (Exception exception) {
            throw new LuaError("Endpoint call failed: " + exception.getMessage());
        }
    }

    private static void validateArguments(EndpointMethod method, List<LuaValue> arguments) {
        List<EndpointType> expected = method.signature().arguments();
        if (!method.signature().variadic() && arguments.size() != expected.size()) {
            throw new LuaError(
                    "Endpoint method " + method.id() + " expects " + expected.size() + " arguments");
        }
        if (method.signature().variadic() && arguments.size() < expected.size()) {
            throw new LuaError(
                    "Endpoint method " + method.id() + " expects at least " + expected.size() + " arguments");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!LuaValueValidator.matches(expected.get(index), arguments.get(index))) {
                throw new LuaError("Endpoint argument " + (index + 1) + " must be "
                        + expected.get(index).name().toLowerCase());
            }
        }
    }

    private static Varargs values(List<LuaValue> values) {
        return LuaValue.varargsOf(values.toArray(LuaValue[]::new));
    }

    private static void validateReturns(EndpointMethod method, List<LuaValue> values) {
        List<EndpointType> expected = method.signature().returns();
        if (values.size() != expected.size()) {
            throw new LuaError(
                    "Endpoint method " + method.id() + " returned " + values.size() + " values; expected "
                            + expected.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!LuaValueValidator.matches(expected.get(index), values.get(index))) {
                throw new LuaError("Endpoint return " + (index + 1) + " must be "
                        + expected.get(index).name().toLowerCase());
            }
        }
    }

    private static void requireSelf(LuaTable proxy, Varargs args) {
        if (args.arg1() != proxy) {
            throw new LuaError("Endpoint methods must be called with ':'");
        }
    }
}
