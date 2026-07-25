package dev.propulsionteam.computed.integration.create;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointRuntimeLifecycle;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;
import org.luaj.vm2.LuaValue;

public final class CreateIntegration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private CreateIntegration() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("create:kinetic", endpoint -> endpoint.method(
                        "speed",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> number(kinetic(invocation, Metric.SPEED)),
                        ignored -> number(0),
                        "Returns the adjacent Create kinetic speed.")
                .method(
                        "stress",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> number(kinetic(invocation, Metric.STRESS)),
                        ignored -> number(0),
                        "Returns the adjacent Create kinetic stress.")
                .method(
                        "capacity",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> number(kinetic(invocation, Metric.CAPACITY)),
                        ignored -> number(0),
                        "Returns the adjacent Create kinetic capacity."));
        ComputedEndpoints.register("create:redstone_link", endpoint -> endpoint.method(
                        "receive",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.STRING),
                                List.of(EndpointType.NUMBER)),
                        EndpointPolicy.computerThread(false, false),
                        CreateIntegration::receive,
                        null,
                        "Reads a Create redstone-link frequency pair.")
                .method(
                        "transmit",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.STRING, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.computerThread(true, false),
                        CreateIntegration::transmit,
                        null,
                        "Writes a Create redstone-link frequency pair."));
        EndpointRuntimeLifecycle.register(new EndpointRuntimeLifecycle.Listener() {
            @Override
            public void unload(java.util.UUID computerId, Object host) {
                if (host instanceof ComputerBlockEntity computer) {
                    CreateRedstoneLinks.clear(computer);
                }
            }
        });
    }

    private static double kinetic(EndpointInvocation invocation, Metric metric) {
        ComputerBlockEntity computer = requireComputer(invocation);
        requireCreate();
        Direction direction = requireDirection(computer, invocation.target());
        BlockPos target = computer.getBlockPos().relative(direction);
        return switch (metric) {
            case SPEED -> CreateKineticAccess.speed(computer.getLevel(), target);
            case STRESS -> CreateKineticAccess.stress(computer.getLevel(), target);
            case CAPACITY -> CreateKineticAccess.capacity(computer.getLevel(), target);
        };
    }

    private static EndpointResult receive(EndpointInvocation invocation) {
        ComputerBlockEntity computer = requireComputer(invocation);
        requireCreate();
        int strength = CreateRedstoneLinks.receive(
                computer,
                invocation.nodeId(),
                invocation.arguments().get(0).checkjstring(),
                invocation.arguments().get(1).checkjstring());
        return number(strength);
    }

    private static EndpointResult transmit(EndpointInvocation invocation) {
        ComputerBlockEntity computer = requireComputer(invocation);
        requireCreate();
        CreateRedstoneLinks.transmit(
                computer,
                invocation.nodeId(),
                invocation.arguments().get(0).checkjstring(),
                invocation.arguments().get(1).checkjstring(),
                invocation.arguments().get(2).checkint());
        return new EndpointResult.Immediate(List.of());
    }

    private static ComputerBlockEntity requireComputer(EndpointInvocation invocation) {
        if (invocation.host() instanceof ComputerBlockEntity computer && computer.getLevel() != null) {
            return computer;
        }
        throw new IllegalStateException("Create endpoints require a server computer");
    }

    private static Direction requireDirection(ComputerBlockEntity computer, String target) {
        Direction direction = computer.worldFaceForEndpoint(target);
        if (direction == null) {
            throw new IllegalArgumentException("Unknown computer side: " + target);
        }
        return direction;
    }

    private static void requireCreate() {
        if (!ModList.get().isLoaded("create")) {
            throw new IllegalStateException("Create is not installed");
        }
    }

    private static EndpointResult.Immediate number(double value) {
        return new EndpointResult.Immediate(List.of(LuaValue.valueOf(value)));
    }

    private enum Metric {
        SPEED,
        STRESS,
        CAPACITY
    }
}
