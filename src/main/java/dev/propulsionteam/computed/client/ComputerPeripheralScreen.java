package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ComputerPeripheralScreen extends AbstractContainerScreen<ComputerPeripheralMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public ComputerPeripheralScreen(ComputerPeripheralMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        int rows = menu.getContainerRows();
        int topHeight = 17 + rows * 18;
        this.imageWidth = 176;
        this.imageHeight = topHeight + 96;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int hTop = 17 + menu.getContainerRows() * 18;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, hTop, 256, 256);
        graphics.blit(TEXTURE, x, y + hTop, 0, 126, imageWidth, 96, 256, 256);
    }
}
