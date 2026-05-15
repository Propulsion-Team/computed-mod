package dev.propulsionteam.computed;

import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.ComputedTags;
import dev.propulsionteam.computed.content.PlacedPeripheralItemData;
import dev.propulsionteam.computed.content.PlacedPeripheralLink;
import dev.propulsionteam.computed.content.PeripheralConnectorItem;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Computed.MODID)
public final class ComputedGameplayEvents {
    private ComputedGameplayEvents() {}

    /** Runs before block {@code use} (e.g. Simulated linked typewriter GUI) so linking is not swallowed. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickPeripheralConnector(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PeripheralConnectorItem)) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (state.is(ComputedRegistries.COMPUTER_BLOCK.get())) {
            return;
        }
        if (!PeripheralConnectorItem.hasComputerBinding(stack)) {
            return;
        }
        InteractionResult r = PeripheralConnectorItem.serverTryLinkPeripheral(sp, level, event.getPos(), state, stack);
        event.setCanceled(true);
        event.setCancellationResult(r);
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        BlockPos placedPos = event.getPos();
        BlockState placedBlock = event.getPlacedBlock();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) {
                continue;
            }
            PlacedPeripheralItemData.onBlockPlacedFromBoundItem(player, stack, level, placedPos, placedBlock);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        InteractionHand hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof PeripheralConnectorItem || stack.is(ComputedRegistries.PERIPHERAL_CONNECTOR.get())) {
            return;
        }
        if (!stack.is(ComputedTags.Items.PERIPHERAL)) {
            return;
        }
        if (!PlacedPeripheralItemData.hasComputerBinding(stack)) {
            return;
        }
        if (!Peripherals.isBindableHeldPeripheral(stack)) {
            return;
        }

        BlockState target = level.getBlockState(event.getPos());
        if (!target.is(ComputedTags.Blocks.PERIPHERAL)) {
            return;
        }

        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() != target.getBlock()) {
            player.displayClientMessage(Component.translatable("item.computed.peripheral_connector.link_failed"), true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (!Peripherals.isPlacedPeripheralLinkTargetState(target)) {
            return;
        }

        var cPosOpt = PlacedPeripheralItemData.getBoundComputerPos(stack);
        var cDimOpt = PlacedPeripheralItemData.getBoundComputerDimension(stack);
        if (cPosOpt.isEmpty() || cDimOpt.isEmpty() || !level.dimension().equals(cDimOpt.get())) {
            player.displayClientMessage(
                    Component.translatable("item.computed.peripheral_connector.wrong_dimension"), true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (!(level.getBlockEntity(cPosOpt.get()) instanceof ComputerBlockEntity computer)) {
            PlacedPeripheralItemData.clearBinding(stack);
            player.displayClientMessage(
                    Component.translatable("item.computed.peripheral_connector.no_computer"), true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (event.getPos().distSqr(cPosOpt.get()) > 64 * 64) {
            player.displayClientMessage(Component.translatable("item.computed.peripheral_connector.too_far"), true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (!computer.addPlacedPeripheralFromWorld(level, event.getPos(), target)) {
            player.displayClientMessage(Component.translatable("item.computed.peripheral_connector.link_failed"), true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        int id = computer.placedPeripheralLinksView().stream()
                .filter(l -> l.pos().equals(event.getPos()))
                .mapToInt(PlacedPeripheralLink::instanceId)
                .findFirst()
                .orElse(0);
        PlacedPeripheralItemData.clearBinding(stack);
        player.displayClientMessage(Component.translatable("computed.peripheral_placed_registered", id), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
