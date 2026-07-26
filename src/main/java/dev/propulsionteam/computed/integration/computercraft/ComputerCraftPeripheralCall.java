package dev.propulsionteam.computed.integration.computercraft;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;

final class ComputerCraftPeripheralCall {
    private static final AtomicLong TASK_IDS = new AtomicLong();

    private final ComputerBlockEntity computer;
    private final String directionName;
    private final IPeripheral peripheral;
    private final CompletableFuture<EndpointResult.Immediate> future = new CompletableFuture<>();
    private final Access access = new Access();
    private final Context context = new Context();
    private ILuaCallback callback;
    private boolean closed;

    private ComputerCraftPeripheralCall(
            ComputerBlockEntity computer,
            String directionName,
            IPeripheral peripheral) {
        this.computer = computer;
        this.directionName = directionName;
        this.peripheral = peripheral;
    }

    static List<String> methods(IPeripheral peripheral) {
        Map<String, Method> annotated = annotatedMethods(peripheral);
        List<String> names = new ArrayList<>(annotated.keySet());
        if (peripheral instanceof IDynamicPeripheral dynamic) {
            for (String name : dynamic.getMethodNames()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    static EndpointResult invoke(
            ComputerBlockEntity computer,
            String directionName,
            IPeripheral peripheral,
            String methodName,
            List<Object> arguments) throws LuaException {
        ComputerCraftPeripheralCall call =
                new ComputerCraftPeripheralCall(computer, directionName, peripheral);
        peripheral.attach(call.access);
        try {
            MethodResult result = call.invokeMethod(methodName, arguments);
            if (result.getCallback() == null) {
                call.close();
                return immediate(result.getResult());
            }
            call.callback = result.getCallback();
            ComputerCraftIntegration.track(call);
            call.future.whenComplete((value, error) -> call.close());
            return new EndpointResult.Yielded(call.future);
        } catch (LuaException | RuntimeException exception) {
            call.close();
            throw exception;
        }
    }

    boolean valid() {
        return !closed
                && !computer.isRemoved()
                && ComputerCraftIntegration.findPeripheral(computer, Direction.byName(directionName))
                        .map(current -> current.equals(peripheral) || peripheral.equals(current))
                        .orElse(false);
    }

    boolean belongsTo(ComputerBlockEntity computer) {
        return this.computer == computer;
    }

    void cancel(String reason) {
        future.completeExceptionally(new IllegalStateException(reason));
        close();
    }

    private MethodResult invokeMethod(String methodName, List<Object> arguments) throws LuaException {
        if (peripheral instanceof IDynamicPeripheral dynamic) {
            String[] names = dynamic.getMethodNames();
            for (int index = 0; index < names.length; index++) {
                if (names[index].equals(methodName)) {
                    return dynamic.callMethod(
                            access,
                            context,
                            index,
                            new ObjectArguments(arguments));
                }
            }
        }
        Method method = annotatedMethods(peripheral).get(methodName);
        if (method == null) {
            throw new LuaException("Peripheral method is unavailable: " + methodName);
        }
        return invokeAnnotated(method, arguments);
    }

    private MethodResult invokeAnnotated(Method method, List<Object> arguments) throws LuaException {
        Object[] invocationArguments = bind(method, arguments);
        try {
            Object result = method.invoke(peripheral, invocationArguments);
            if (result instanceof MethodResult methodResult) {
                return methodResult;
            }
            if (result instanceof Object[] values) {
                return MethodResult.of(values);
            }
            return result == null ? MethodResult.of() : MethodResult.of(result);
        } catch (IllegalAccessException exception) {
            throw new LuaException("Peripheral method is not accessible");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof LuaException lua) {
                throw lua;
            }
            throw new LuaException(cause == null ? exception.getMessage() : cause.getMessage());
        }
    }

    private Object[] bind(Method method, List<Object> arguments) throws LuaException {
        Object[] bound = new Object[method.getParameterCount()];
        int argumentIndex = 0;
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            Class<?> type = parameters[index].getType();
            if (type == IComputerAccess.class) {
                bound[index] = access;
            } else if (type == ILuaContext.class) {
                bound[index] = context;
            } else if (type == IArguments.class) {
                bound[index] = new ObjectArguments(arguments.subList(argumentIndex, arguments.size()));
                argumentIndex = arguments.size();
            } else {
                if (argumentIndex >= arguments.size()) {
                    throw new LuaException("Peripheral method received too few arguments");
                }
                bound[index] = coerce(arguments.get(argumentIndex++), type);
            }
        }
        if (argumentIndex != arguments.size()) {
            throw new LuaException("Peripheral method received too many arguments");
        }
        return bound;
    }

    private static Object coerce(Object value, Class<?> target) throws LuaException {
        if (value == null && !target.isPrimitive()) {
            return null;
        }
        if (target == Object.class || target.isInstance(value)) {
            return value;
        }
        if (target == String.class) {
            return Objects.toString(value, "");
        }
        if (target == boolean.class || target == Boolean.class) {
            if (value instanceof Boolean bool) {
                return bool;
            }
        }
        if (value instanceof Number number) {
            if (target == double.class || target == Double.class) {
                return number.doubleValue();
            }
            if (target == float.class || target == Float.class) {
                return number.floatValue();
            }
            if (target == long.class || target == Long.class) {
                return number.longValue();
            }
            if (target == int.class || target == Integer.class) {
                return number.intValue();
            }
            if (target == short.class || target == Short.class) {
                return number.shortValue();
            }
            if (target == byte.class || target == Byte.class) {
                return number.byteValue();
            }
        }
        throw new LuaException("Cannot convert peripheral argument to " + target.getSimpleName());
    }

    private synchronized void queueEvent(String eventName, Object... arguments) {
        if (callback == null || closed) {
            return;
        }
        Object[] event = new Object[(arguments == null ? 0 : arguments.length) + 1];
        event[0] = eventName;
        if (arguments != null) {
            System.arraycopy(arguments, 0, event, 1, arguments.length);
        }
        try {
            MethodResult result = callback.resume(event);
            if (result.getCallback() == null) {
                callback = null;
                future.complete(immediate(result.getResult()));
            } else {
                callback = result.getCallback();
            }
        } catch (LuaException exception) {
            future.completeExceptionally(exception);
        }
    }

    private synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        callback = null;
        peripheral.detach(access);
        ComputerCraftIntegration.untrack(this);
    }

    private static EndpointResult.Immediate immediate(Object[] values) throws LuaException {
        return new EndpointResult.Immediate(List.of(ComputerCraftValueCodec.results(values)));
    }

    private static Map<String, Method> annotatedMethods(IPeripheral peripheral) {
        Map<String, Method> methods = new LinkedHashMap<>();
        Arrays.stream(peripheral.getClass().getMethods()).forEach(method -> {
            LuaFunction annotation = method.getAnnotation(LuaFunction.class);
            if (annotation == null) {
                return;
            }
            String[] aliases = annotation.value();
            if (aliases.length == 0) {
                methods.putIfAbsent(method.getName(), method);
            } else {
                for (String alias : aliases) {
                    methods.putIfAbsent(alias, method);
                }
            }
        });
        return methods;
    }

    private final class Context implements ILuaContext {
        @Override
        public long issueMainThreadTask(LuaTask task) throws LuaException {
            long id = TASK_IDS.incrementAndGet();
            if (computer.getLevel() == null || computer.getLevel().getServer() == null) {
                throw new LuaException("Computer server is unavailable");
            }
            computer.getLevel().getServer().execute(() -> {
                try {
                    Object[] result = task.execute();
                    Object[] event = new Object[(result == null ? 0 : result.length) + 2];
                    event[0] = id;
                    event[1] = true;
                    if (result != null) {
                        System.arraycopy(result, 0, event, 2, result.length);
                    }
                    access.queueEvent("task_complete", event);
                } catch (LuaException exception) {
                    access.queueEvent("task_complete", id, false, exception.getMessage());
                }
            });
            return id;
        }
    }

    private final class Access implements IComputerAccess {
        @Override
        public String mount(String desiredLocation, Mount mount, String driveName) {
            throw new UnsupportedOperationException("Filesystem mounts are unavailable through Computed");
        }

        @Override
        public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            throw new UnsupportedOperationException("Filesystem mounts are unavailable through Computed");
        }

        @Override
        public void unmount(String location) {
            throw new UnsupportedOperationException("Filesystem mounts are unavailable through Computed");
        }

        @Override
        public int getID() {
            return computer.getOrCreateUuid().hashCode();
        }

        @Override
        public void queueEvent(String event, Object... arguments) {
            ComputerCraftPeripheralCall.this.queueEvent(event, arguments);
        }

        @Override
        public String getAttachmentName() {
            return "computed_" + directionName;
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            Map<String, IPeripheral> available = new LinkedHashMap<>();
            for (Direction candidate : Direction.values()) {
                ComputerCraftIntegration.findPeripheral(computer, candidate)
                        .ifPresent(value -> available.put(candidate.getName(), value));
            }
            return Map.copyOf(available);
        }

        @Override
        public IPeripheral getAvailablePeripheral(String name) {
            Direction requested = Direction.byName(name);
            return requested == null
                    ? null
                    : ComputerCraftIntegration.findPeripheral(computer, requested).orElse(null);
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            return new WorkMonitor() {
                @Override
                public boolean canWork() {
                    return true;
                }

                @Override
                public boolean shouldWork() {
                    return true;
                }

                @Override
                public void trackWork(long time, TimeUnit unit) {}
            };
        }
    }
}
