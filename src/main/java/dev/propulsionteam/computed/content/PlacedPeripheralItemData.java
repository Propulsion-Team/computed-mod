package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Binds a {@link ComputedTags.Items#PERIPHERAL}-tagged placeable item to a computer before the block is placed; on
 * placement the link is registered on the server and the bind is cleared from the stack.
 */
public final class PlacedPeripheralItemData {
    private static final String NBT_BIND_POS = "ComputedBindPcPos";
    private static final String NBT_BIND_DIM = "ComputedBindPcDim";

    private PlacedPeripheralItemData() {}

    public static boolean hasComputerBinding(ItemStack stack) {
        CompoundTag t = readTag(stack);
        return t != null && t.contains(NBT_BIND_POS);
    }

    public static Optional<BlockPos> getBoundComputerPos(ItemStack stack) {
        CompoundTag t = readTag(stack);
        if (t == null || !t.contains(NBT_BIND_POS)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(t.getLong(NBT_BIND_POS)));
    }

    public static Optional<ResourceKey<Level>> getBoundComputerDimension(ItemStack stack) {
        CompoundTag t = readTag(stack);
        if (t == null || !t.contains(NBT_BIND_DIM)) {
            return Optional.empty();
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(t.getString(NBT_BIND_DIM));
            return Optional.of(ResourceKey.create(Registries.DIMENSION, rl));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Returns true when the clicked block is a computer and this method stored a bind on {@code stack}. */
    public static boolean tryBindHeldItemToComputer(ItemStack stack, Level level, BlockPos computerPos, Player player) {
        if (!Peripherals.isBindableHeldPeripheral(stack) || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        CompoundTag tag = copyOrNew(stack);
        tag.putLong(NBT_BIND_POS, computerPos.asLong());
        tag.putString(NBT_BIND_DIM, level.dimension().location().toString());
        writeTag(stack, tag);
        refreshGlint(stack);
        sp.sendSystemMessage(Component.translatable("computed.peripheral_item_bound_to_computer"), false);
        return true;
    }

    /** After a successful placement, registers the block with the bound computer and clears bind data on the stack. */
    public static void onBlockPlacedFromBoundItem(
            ServerPlayer player, ItemStack stackUsedForPlacement, Level level, BlockPos placedPos, BlockState placedState) {
        if (!Peripherals.isBindableHeldPeripheral(stackUsedForPlacement) || !hasComputerBinding(stackUsedForPlacement)) {
            return;
        }
        Optional<BlockPos> cPos = getBoundComputerPos(stackUsedForPlacement);
        Optional<ResourceKey<Level>> cDim = getBoundComputerDimension(stackUsedForPlacement);
        if (cPos.isEmpty() || cDim.isEmpty()) {
            return;
        }
        if (!level.dimension().equals(cDim.get())) {
            return;
        }
        if (!Peripherals.isPlacedPeripheralLinkTargetState(placedState)) {
            return;
        }
        if (stackUsedForPlacement.getItem() instanceof BlockItem bi && bi.getBlock() != placedState.getBlock()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(cPos.get());
        if (!(be instanceof ComputerBlockEntity computer)) {
            return;
        }
        if (computer.addPlacedPeripheralFromWorld(level, placedPos, placedState)) {
            int id = computer.placedPeripheralLinksView().stream()
                    .filter(l -> l.pos().equals(placedPos))
                    .mapToInt(PlacedPeripheralLink::instanceId)
                    .findFirst()
                    .orElse(0);
            clearBinding(stackUsedForPlacement);
            player.sendSystemMessage(
                    Component.translatable("computed.peripheral_placed_registered", id), false);
        }
    }

    public static void clearBinding(ItemStack stack) {
        CompoundTag t = readTag(stack);
        if (t == null) {
            return;
        }
        t.remove(NBT_BIND_POS);
        t.remove(NBT_BIND_DIM);
        if (t.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            writeTag(stack, t);
        }
        refreshGlint(stack);
    }

    public static void refreshGlint(ItemStack stack) {
        boolean glint = hasComputerBinding(stack);
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    private static @Nullable CompoundTag readTag(ItemStack stack) {
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : custom.copyTag();
    }

    private static CompoundTag copyOrNew(ItemStack stack) {
        CompoundTag t = readTag(stack);
        return t == null ? new CompoundTag() : t;
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
