package dev.propulsionteam.computed.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class Peripherals {
    private Peripherals() {}

    public static boolean isPeripheral(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ComputedTags.Items.PERIPHERALS);
    }

    /** Node type id matches item id (e.g. {@code computed:antenna}). */
    public static ResourceLocation nodeTypeFor(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static boolean isPeripheralNodeType(ResourceLocation typeId) {
        if (!BuiltInRegistries.ITEM.containsKey(typeId)) {
            return false;
        }
        return BuiltInRegistries.ITEM.get(typeId).getDefaultInstance().is(ComputedTags.Items.PERIPHERALS);
    }

    public static boolean mayPlaceInComputer(Container container, int slot, ItemStack stack) {
        if (stack.isEmpty() || !isPeripheral(stack)) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == slot) {
                continue;
            }
            ItemStack other = container.getItem(i);
            if (!other.isEmpty() && ItemStack.isSameItemSameComponents(stack, other)) {
                return false;
            }
        }
        return true;
    }
}
