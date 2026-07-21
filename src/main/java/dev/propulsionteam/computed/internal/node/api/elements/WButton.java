package dev.propulsionteam.computed.internal.node.api.elements;

import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class WButton extends WElement {
    private String label;
    private Runnable onClick;

    public WButton(String label, int width, Runnable onClick) {
        this.label = label;
        this.width = width;
        this.height = 14;
        this.onClick = onClick;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        
        ComputedEditorStyle.drawButton(graphics, x, y, width, height, hovered, false);
        
        // Label
        int textW = net.minecraft.client.Minecraft.getInstance().font.width(label);
        graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                label,
                x + (width - textW) / 2,
                y + 3,
                hovered ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.TEXT_PRIMARY,
                false);
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height) {
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }
}
