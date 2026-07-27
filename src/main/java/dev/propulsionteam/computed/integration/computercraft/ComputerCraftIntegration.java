package dev.propulsionteam.computed.integration.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointRuntimeLifecycle;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class ComputerCraftIntegration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<ComputerBlockEntity, ComputedPeripheral> PERIPHERALS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<ComputerCraftPeripheralCall> CALLS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ComputerCraftIntegration() {}

    public static void register(IEventBus modBus) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        registerEndpoints();
        modBus.addListener(ComputerCraftIntegration::registerCapabilities);
        EndpointRuntimeLifecycle.register(new EndpointRuntimeLifecycle.Listener() {
            @Override
            public void tick(java.util.UUID computerId, Object host) {
                if (host instanceof ComputerBlockEntity computer) {
                    poll(computer);
                }
            }

            @Override
            public void unload(java.util.UUID computerId, Object host) {
                if (host instanceof ComputerBlockEntity computer) {
                    cancel(computer);
                    ComputerCraftChannels.remove(computer);
                }
            }
        });
    }

    static Optional<IPeripheral> findPeripheral(ComputerBlockEntity computer, Direction direction) {
        if (computer.getLevel() == null || direction == null) {
            return Optional.empty();
        }
        IPeripheral peripheral = computer.getLevel().getCapability(
                PeripheralCapability.get(),
                computer.getBlockPos().relative(direction),
                direction.getOpposite());
        return Optional.ofNullable(peripheral);
    }

    static synchronized void track(ComputerCraftPeripheralCall call) {
        CALLS.add(call);
    }

    static synchronized void untrack(ComputerCraftPeripheralCall call) {
        CALLS.remove(call);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ComputedRegistries.COMPUTER_BLOCK_ENTITY.get(),
                (computer, side) -> PERIPHERALS.computeIfAbsent(computer, ComputedPeripheral::new));
    }

    private static void registerEndpoints() {
        ComputedEndpoints.register("computercraft:channel", endpoint -> endpoint.method(
                        "read",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        ComputerCraftIntegration::readChannel,
                        ignored -> new EndpointResult.Immediate(List.of(new LuaTable())),
                        "Reads a named value written by an attached CC computer.")
                .method(
                        "publish",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.TABLE),
                                List.of()),
                        EndpointPolicy.computerThread(true, false),
                        ComputerCraftIntegration::publishChannel,
                        null,
                        "Publishes a named graph value to attached CC computers."));
        ComputedEndpoints.register("computercraft:peripheral", endpoint -> endpoint.method(
                        "methods",
                        EndpointSignature.of(List.of(), List.of(EndpointType.TABLE)),
                        EndpointPolicy.serverThread(false, false),
                        ComputerCraftIntegration::peripheralMethods,
                        null,
                        "Lists methods exposed by the adjacent CC peripheral.")
                .method(
                        "call",
                        new EndpointSignature(
                                List.of(EndpointType.STRING),
                                List.of(EndpointType.TABLE),
                                true),
                        EndpointPolicy.serverThread(true, false),
                        ComputerCraftIntegration::callPeripheral,
                        null,
                        "Calls an adjacent CC peripheral method and resumes yielded results."));
    }

    private static EndpointResult readChannel(EndpointInvocation invocation) throws LuaException {
        ComputerBlockEntity computer = requireComputer(invocation);
        Object value = ComputerCraftChannels.store(computer)
                .input(invocation.arguments().getFirst().checkjstring());
        LuaTable wrapper = new LuaTable();
        wrapper.set("value", ComputerCraftValueCodec.toLua(value));
        return new EndpointResult.Immediate(List.of(wrapper));
    }

    private static EndpointResult publishChannel(EndpointInvocation invocation) throws LuaException {
        ComputerBlockEntity computer = requireComputer(invocation);
        ComputerCraftChannels.store(computer).publish(
                invocation.arguments().get(0).checkjstring(),
                ComputerCraftValueCodec.toJava(invocation.arguments().get(1)));
        return new EndpointResult.Immediate(List.of());
    }

    private static EndpointResult peripheralMethods(EndpointInvocation invocation) throws LuaException {
        ResolvedPeripheral resolved = requirePeripheral(invocation);
        LuaTable methods = new LuaTable();
        List<String> names = ComputerCraftPeripheralCall.methods(resolved.peripheral());
        for (int index = 0; index < names.size(); index++) {
            methods.set(index + 1, names.get(index));
        }
        return new EndpointResult.Immediate(List.of(methods));
    }

    private static EndpointResult callPeripheral(EndpointInvocation invocation) throws LuaException {
        ResolvedPeripheral resolved = requirePeripheral(invocation);
        String method = invocation.arguments().getFirst().checkjstring();
        List<Object> arguments = new ArrayList<>();
        for (int index = 1; index < invocation.arguments().size(); index++) {
            arguments.add(ComputerCraftValueCodec.toJava(invocation.arguments().get(index)));
        }
        return ComputerCraftPeripheralCall.invoke(
                resolved.computer(),
                resolved.direction().getName(),
                resolved.peripheral(),
                method,
                arguments);
    }

    private static ComputerBlockEntity requireComputer(EndpointInvocation invocation) throws LuaException {
        if (invocation.host() instanceof ComputerBlockEntity computer) {
            return computer;
        }
        throw new LuaException("CC:Tweaked endpoints require a server computer");
    }

    private static ResolvedPeripheral requirePeripheral(EndpointInvocation invocation) throws LuaException {
        ComputerBlockEntity computer = requireComputer(invocation);
        Direction direction = computer.worldFaceForEndpoint(invocation.target());
        if (direction == null) {
            throw new LuaException("Unknown computer side: " + invocation.target());
        }
        IPeripheral peripheral = findPeripheral(computer, direction)
                .orElseThrow(() -> new LuaException("No CC peripheral is attached on " + invocation.target()));
        return new ResolvedPeripheral(computer, direction, peripheral);
    }

    private static synchronized void poll(ComputerBlockEntity computer) {
        List<ComputerCraftPeripheralCall> invalid = CALLS.stream()
                .filter(call -> !call.valid())
                .toList();
        invalid.forEach(call -> call.cancel("CC peripheral detached while call was yielded"));
    }

    private static synchronized void cancel(ComputerBlockEntity computer) {
        List<ComputerCraftPeripheralCall> pending = CALLS.stream()
                .filter(call -> call.belongsTo(computer))
                .toList();
        pending.forEach(call -> call.cancel("Computer unloaded while CC call was yielded"));
    }

    private record ResolvedPeripheral(
            ComputerBlockEntity computer,
            Direction direction,
            IPeripheral peripheral) {}
}
