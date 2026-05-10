package dev.propulsionteam.computed.menu;

import dev.propulsionteam.computed.content.Peripherals;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PeripheralSlot extends Slot {
    public PeripheralSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return Peripherals.mayPlaceInComputer(container, index, stack);
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
