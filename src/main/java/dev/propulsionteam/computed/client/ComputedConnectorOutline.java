package dev.propulsionteam.computed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.PeripheralConnectorItem;
import dev.propulsionteam.computed.content.Peripherals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT)
public final class ComputedConnectorOutline {
    private ComputedConnectorOutline() {}

    @SubscribeEvent
    public static void onHighlightBlock(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            return;
        }

        ItemStack stack = connectorInHands(player);
        if (stack.isEmpty()) {
            return;
        }

        if (!(event.getTarget() instanceof BlockHitResult bh)
                || bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = bh.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean compatible = connectorPreviewCompatible(level, stack, pos, state);

        PoseStack pose = event.getPoseStack();
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.lines());
        var cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.bounds().inflate(0.002);
        LevelRenderer.renderLineBox(pose, vc, box, compatible ? 0.2f : 1f, compatible ? 0.95f : 0.2f, compatible ? 0.35f : 0.2f, 0.95f);
        pose.popPose();

        event.setCanceled(true);
    }

    private static ItemStack connectorInHands(Player player) {
        var m = player.getMainHandItem();
        if (!m.isEmpty() && m.is(ComputedRegistries.PERIPHERAL_CONNECTOR.get())) {
            return m;
        }
        var o = player.getOffhandItem();
        if (!o.isEmpty() && o.is(ComputedRegistries.PERIPHERAL_CONNECTOR.get())) {
            return o;
        }
        return ItemStack.EMPTY;
    }

    private static boolean connectorPreviewCompatible(Level level, ItemStack connector, BlockPos targetPos, BlockState targetState) {
        boolean hasBind = PeripheralConnectorItem.hasComputerBinding(connector);

        boolean isComputer = targetState.is(ComputedRegistries.COMPUTER_BLOCK.get());
        boolean linkTarget = Peripherals.isPlacedPeripheralLinkTargetState(targetState);

        if (!hasBind) {
            return isComputer;
        }

        var dim = PeripheralConnectorItem.peekBoundDimension(connector);
        var cpos = PeripheralConnectorItem.peekBoundComputer(connector);
        if (dim.isEmpty() || cpos.isEmpty()) {
            return false;
        }
        if (!level.dimension().equals(dim.get())) {
            return false;
        }
        if (!linkTarget) {
            return false;
        }
        return targetPos.distSqr(cpos.get()) <= 64 * 64;
    }
}