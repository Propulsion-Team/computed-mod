package dev.propulsionteam.computed.network;

import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.propulsionteam.computed.ComputerEditorBridge;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.customnodes.ComputedCustomNodes;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.widgets.SliderWidget;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.nodes.widgets.InteractiveWidgetNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

public final class ComputedNetworking {
    private static final double MAX_EDIT_DISTANCE_SQ = 16.0 * 16.0;
    /** Pixels per monitor block; widget x/y/w/h are in this coordinate system. */
    public static final int SCREEN_PX_PER_BLOCK = 64;
    /** Monitor model/texture pixels reserved by the bezel on each outer screen edge. */
    public static final int BEZEL_MODEL_PIXELS = 2;
    /** Minecraft model pixels per full block edge. */
    public static final int MODEL_PIXELS_PER_BLOCK = 16;
    /** Bezel inset in widget coordinate pixels for one outer edge. */
    public static final int SCREEN_BEZEL_PX =
            SCREEN_PX_PER_BLOCK * BEZEL_MODEL_PIXELS / MODEL_PIXELS_PER_BLOCK;

    private ComputedNetworking() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ComputedNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ComputedNetworking::onPlayerLogin);
    }

    /** Push the server's data-driven node definitions to a joining player so their editor and graphs match the server. */
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SyncCustomNodesPayload(ComputedCustomNodes.readRawDefinitions()));
        }
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
        registrar.playToServer(
                MonitorClickPayload.TYPE,
                MonitorClickPayload.STREAM_CODEC,
                ComputedNetworking::handleMonitorClick);
        registrar.playToClient(
                SyncCustomNodesPayload.TYPE,
                SyncCustomNodesPayload.STREAM_CODEC,
                ComputedNetworking::handleSyncCustomNodes);
    }

    private static void handleSyncCustomNodes(SyncCustomNodesPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ComputedCustomNodes.applyServerDefinitions(payload.definitions()));
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
                Computed.LOGGER.debug(
                        "Rejected graph save from {} at {} (distance {:.2f} > {:.2f})",
                        player.getGameProfile().getName(),
                        pos,
                        Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos))),
                        Math.sqrt(MAX_EDIT_DISTANCE_SQ));
                return;
            }
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof ComputerBlockEntity computer) {
                computer.applyGraphFromNetwork(payload.graphTag());
            } else {
                Computed.LOGGER.debug(
                        "Rejected graph save from {} at {} (no ComputerBlockEntity)",
                        player.getGameProfile().getName(),
                        pos);
            }
        });
    }

    private static void handleMonitorClick(MonitorClickPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            BlockPos originPos = payload.originPos();
            if (player.distanceToSqr(Vec3.atCenterOf(originPos)) > MAX_EDIT_DISTANCE_SQ) return;
            BlockEntity be = player.level().getBlockEntity(originPos);
            if (!(be instanceof MonitorBlockEntity origin)) return;
            BlockPos ownerPos = origin.getOwnerComputerPos();
            if (ownerPos == null) return;
            BlockEntity ownerBe = player.level().getBlockEntity(ownerPos);
            if (!(ownerBe instanceof ComputerBlockEntity computer)) return;

            int screenW = origin.getWidth() * SCREEN_PX_PER_BLOCK;
            int screenH = origin.getHeight() * SCREEN_PX_PER_BLOCK;
            int px = Math.min(screenW - 1, Math.max(0, (int) Math.floor(payload.u() * screenW)));
            int py = Math.min(screenH - 1, Math.max(0, (int) Math.floor(payload.v() * screenH)));

            for (Widget w : origin.getDrawList().widgets()) {
                if (px < w.x() || px >= w.x() + w.w() || py < w.y() || py >= w.y() + w.h()) continue;
                WNode node = findNodeById(computer.getGraph(), w.id());
                if (!(node instanceof InteractiveWidgetNode interactive)) continue;
                double value = 1.0;
                if (w instanceof SliderWidget) {
                    value = w.w() <= 0 ? 0.0 : (double) (px - w.x()) / (double) w.w();
                }
                interactive.onWidgetInput(value);
                return;
            }
        });
    }

    private static WNode findNodeById(WGraph g, UUID id) {
        for (WNode n : g.getNodes()) {
            if (n.getId().equals(id)) return n;
            if (n instanceof FunctionCardNode fc) {
                WNode hit = findNodeById(fc.getInnerGraph(), id);
                if (hit != null) return hit;
            }
        }
        return null;
    }
}
