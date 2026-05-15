package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Shear-like durability tool: bind a computer, then link in-world peripheral blocks. */
public class PeripheralConnectorItem extends Item {
    private static final String NBT_POS = "ComputedConnPos";
    private static final String NBT_DIM = "ComputedConnDim";
    private static final int MAX_LINK_DISTANCE_SQ = 64 * 64;

    public PeripheralConnectorItem(Properties props) {
        super(props);
    }

    private static @Nullable CompoundTag read(ItemStack stack) {
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : custom.copyTag();
    }

    private static CompoundTag copyOrNew(ItemStack stack) {
        CompoundTag t = read(stack);
        return t == null ? new CompoundTag() : t;
    }

    private static void write(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static Optional<BlockPos> peekBoundComputer(ItemStack stack) {
        CompoundTag t = read(stack);
        if (t == null || !t.contains(NBT_POS)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(t.getLong(NBT_POS)));
    }

    public static Optional<ResourceKey<Level>> peekBoundDimension(ItemStack stack) {
        CompoundTag t = read(stack);
        if (t == null || !t.contains(NBT_DIM)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString(NBT_DIM))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static void refreshGlint(ItemStack stack) {
        if (hasComputerBinding(stack)) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    public static boolean hasComputerBinding(ItemStack stack) {
        CompoundTag t = read(stack);
        return t != null && t.contains(NBT_POS);
    }

    public static void clearBinding(ItemStack stack) {
        CompoundTag t = read(stack);
        if (t == null) {
            return;
        }
        t.remove(NBT_POS);
        t.remove(NBT_DIM);
        if (t.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            write(stack, t);
        }
        refreshGlint(stack);
    }

    private static void bindComputer(ItemStack stack, Level level, BlockPos computerPos) {
        CompoundTag tag = copyOrNew(stack);
        tag.putLong(NBT_POS, computerPos.asLong());
        tag.putString(NBT_DIM, level.dimension().location().toString());
        write(stack, tag);
        refreshGlint(stack);
    }

    private static boolean tryResolveComputer(Level level, ItemStack stack, @Nullable BlockPos[] outPos) {
        CompoundTag t = read(stack);
        if (t == null || !t.contains(NBT_POS) || !t.contains(NBT_DIM)) {
            return false;
        }
        ResourceKey<Level> dim;
        try {
            dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString(NBT_DIM)));
        } catch (Exception e) {
            return false;
        }
        if (!level.dimension().equals(dim)) {
            return false;
        }
        BlockPos c = BlockPos.of(t.getLong(NBT_POS));
        if (outPos != null && outPos.length > 0) {
            outPos[0] = c;
        }
        return true;
    }

    /**
     * Server-only: link {@code targetState} at {@code targetPos} to {@code stack}'s bound computer.
     * Use from {@link PlayerInteractEvent} at high priority so block GUIs (e.g. Simulated typewriter) do not swallow the click.
     */
    public static InteractionResult serverTryLinkPeripheral(
            ServerPlayer sp, Level level, BlockPos targetPos, BlockState targetState, ItemStack stack) {
        if (!hasComputerBinding(stack)) {
            sp.displayClientMessage(
                    Component.translatable("item.computed.peripheral_connector.bind_computer_first"), true);
            return InteractionResult.CONSUME;
        }
        BlockPos[] cRef = new BlockPos[1];
        if (!tryResolveComputer(level, stack, cRef) || cRef[0] == null) {
            sp.displayClientMessage(
                    Component.translatable("item.computed.peripheral_connector.wrong_dimension"), true);
            return InteractionResult.CONSUME;
        }
        BlockEntity cbe = level.getBlockEntity(cRef[0]);
        if (!(cbe instanceof ComputerBlockEntity computer)) {
            sp.displayClientMessage(
                    Component.translatable("item.computed.peripheral_connector.no_computer"), true);
            clearBinding(stack);
            return InteractionResult.CONSUME;
        }
        if (targetPos.distSqr(cRef[0]) > MAX_LINK_DISTANCE_SQ) {
            sp.displayClientMessage(Component.translatable("item.computed.peripheral_connector.too_far"), true);
            return InteractionResult.CONSUME;
        }
        if (!Peripherals.isPlacedPeripheralLinkTargetState(targetState)) {
            sp.displayClientMessage(Component.translatable("item.computed.peripheral_connector.link_failed"), true);
            return InteractionResult.CONSUME;
        }
        if (!computer.addPlacedPeripheralFromWorld(level, targetPos, targetState)) {
            sp.displayClientMessage(Component.translatable("item.computed.peripheral_connector.link_failed"), true);
            return InteractionResult.CONSUME;
        }
        sp.displayClientMessage(Component.translatable("item.computed.peripheral_connector.linked_block"), true);
        stack.hurtAndBreak(1, (ServerLevel) level, sp, ignored -> {});
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (hasComputerBinding(stack)) {
            clearBinding(stack);
            player.displayClientMessage(Component.translatable("item.computed.peripheral_connector.cleared"), true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (state.is(ComputedRegistries.COMPUTER_BLOCK.get())) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            bindComputer(stack, level, pos);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("item.computed.peripheral_connector.bound_computer"), true);
            }
            return InteractionResult.CONSUME;
        }

        if (!(player instanceof ServerPlayer sp)) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        return serverTryLinkPeripheral(sp, level, pos, state, stack);
    }
}
