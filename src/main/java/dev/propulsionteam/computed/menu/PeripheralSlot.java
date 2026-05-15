package dev.propulsionteam.computed.menu;

import dev.propulsionteam.computed.content.Peripherals;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PeripheralSlot extends Slot {
    /** Index within the computer {@link Container} (same as the value passed to {@link Slot#Slot}). */
    private final int computerSlotIndex;

    public PeripheralSlot(Container container, int computerSlotIndex, int x, int y) {
        super(container, computerSlotIndex, x, y);
        this.computerSlotIndex = computerSlotIndex;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return Peripherals.mayPlaceInComputer(container, computerSlotIndex, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }
}
