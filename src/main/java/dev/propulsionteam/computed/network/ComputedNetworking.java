package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.ComputerEditorBridge;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ComputedNetworking {
    private static final double MAX_EDIT_DISTANCE_SQ = 8.0 * 8.0;

    private ComputedNetworking() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ComputedNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenComputerEditorPayload.TYPE,
                OpenComputerEditorPayload.STREAM_CODEC,
                ComputedNetworking::handleOpenEditor);
        registrar.playToServer(
                SaveComputerGraphPayload.TYPE,
                SaveComputerGraphPayload.STREAM_CODEC,
                ComputedNetworking::handleSaveGraph);
    }

    public static OpenComputerEditorPayload openPayload(BlockPos pos, CompoundTag graphTag) {
        return new OpenComputerEditorPayload(pos, graphTag);
    }

    private static void handleOpenEditor(OpenComputerEditorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ComputerEditorBridge.open(payload.pos(), payload.graphTag()));
    }

    private static void handleSaveGraph(SaveComputerGraphPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_EDIT_DISTANCE_SQ) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof ComputerBlockEntity computer) {
                computer.applyGraphFromNetwork(payload.graphTag());
            }
        });
    }
}
