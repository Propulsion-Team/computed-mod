package dev.propulsionteam.computed.client.renderer.node;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class BedrockNodeRenderer {
    private BedrockNodeRenderer() {}

    public static void render(
            GuiGraphics graphics,
            WNode node,
            String category,
            boolean diagnosticError,
            boolean diagnosticWarning,
            int mouseX,
            int mouseY) {
        node.ensureLayoutUpToDate();
        int x = node.getX();
        int y = node.getY();
        int width = node.getWidth();
        int height = node.getHeight();
        boolean hovered = mouseX >= x
                && mouseX <= x + width
                && mouseY >= y
                && mouseY <= y + height;
        int frame = diagnosticError
                ? NodePalette.ERROR
                : diagnosticWarning
                        ? NodePalette.WARNING
                        : node.isSelected()
                                ? NodePalette.SELECTION
                                : NodePalette.category(category).frameArgb();
        graphics.fill(x, y, x + width, y + height, 0xFF090B0D);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, frame);
        graphics.fill(
                x + 2,
                y + 2,
                x + width - 2,
                y + height - 2,
                hovered ? 0xFF20252A : 0xFF171B1F);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 15, frame);
        graphics.fill(x + 4, y + 4, x + width - 4, y + 14, 0xCC101418);
        graphics.fill(x + 5, y + 17, x + width - 5, y + height - 5, 0xFF0E1114);
        ComputedEditorStyle.drawPixelOutline(
                graphics,
                x + 5,
                y + 17,
                width - 10,
                Math.max(1, height - 22),
                0xFF303941);
        graphics.drawString(
                Minecraft.getInstance().font,
                node.getTitle(),
                x + 6,
                y + 4,
                ComputedEditorTheme.TEXT_HEADER,
                false);
        for (int index = 0; index < node.getInputs().size(); index++) {
            renderPin(graphics, node.getInputs().get(index), x - 4, y + 18 + index * 12, true);
        }
        for (int index = 0; index < node.getOutputs().size(); index++) {
            renderPin(
                    graphics,
                    node.getOutputs().get(index),
                    x + width - 1,
                    y + 18 + index * 12,
                    false);
        }
    }

    private static void renderPin(
            GuiGraphics graphics,
            WPin pin,
            int x,
            int y,
            boolean input) {
        int color = pin.getColor();
        graphics.fill(x, y, x + 5, y + 5, color);
        ComputedEditorStyle.drawPixelOutline(
                graphics,
                x,
                y,
                5,
                5,
                ComputedEditorTheme.SOCKET_BORDER);
        graphics.fill(x + 2, y + 2, x + 4, y + 4, ComputedEditorTheme.SOCKET_CENTER);
        String label = pin.getName();
        int textX = input
                ? x + 8
                : x - 4 - Minecraft.getInstance().font.width(label);
        graphics.drawString(
                Minecraft.getInstance().font,
                label,
                textX,
                y - 2,
                ComputedEditorTheme.TEXT_SECONDARY,
                false);
    }
}
