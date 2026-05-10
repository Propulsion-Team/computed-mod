package dev.propulsionteam.computed.menu;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ComputerPeripheralMenu extends AbstractContainerMenu {
    private final Container computer;

    public ComputerPeripheralMenu(MenuType<?> type, int containerId, Inventory playerInventory, ComputerBlockEntity computer) {
        super(type, containerId);
        this.computer = computer;
        computer.startOpen(playerInventory.player);

        int rows = 1;
        for (int j = 0; j < ComputerBlockEntity.CONTAINER_SIZE; j++) {
            addSlot(new PeripheralSlot(computer, j, 8 + j * 18, 18));
        }

        int invYOffset = (rows - 4) * 18;
        for (int l = 0; l < 3; l++) {
            for (int j1 = 0; j1 < 9; j1++) {
                addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + invYOffset));
            }
        }
        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 161 + invYOffset));
        }
    }

    public int getContainerRows() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return computer.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack out = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            out = inSlot.copy();
            int containerSlots = ComputerBlockEntity.CONTAINER_SIZE;
            if (index < containerSlots) {
                if (!moveItemStackTo(inSlot, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(inSlot, 0, containerSlots, false)) {
                return ItemStack.EMPTY;
            }
            if (inSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return out;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        computer.stopOpen(player);
    }
}
