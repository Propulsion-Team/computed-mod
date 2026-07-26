package dev.propulsionteam.computed.client.renderer.node;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class BedrockNodeRenderer {
    static final int CONTENT_INSET = 5;
    static final int PIN_LABEL_PADDING = 5;
    private static final int PIN_SIZE = 5;
    private static final int PIN_HOVER_SIZE = 7;

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
        graphics.drawString(
                Minecraft.getInstance().font,
                node.getTitle(),
                x + 6,
                y + 4,
                node.isSelected() && !diagnosticError && !diagnosticWarning
                        ? 0xFF101418
                        : ComputedEditorTheme.TEXT_HEADER,
                false);
        for (int index = 0; index < node.getInputs().size(); index++) {
            renderPin(
                    graphics,
                    node.getInputs().get(index),
                    x - 4,
                    y + 18 + index * 12,
                    true,
                    mouseX,
                    mouseY);
        }
        for (int index = 0; index < node.getOutputs().size(); index++) {
            renderPin(
                    graphics,
                    node.getOutputs().get(index),
                    x + width - 1,
                    y + 18 + index * 12,
                    false,
                    mouseX,
                    mouseY);
        }
    }

    private static void renderPin(
            GuiGraphics graphics,
            WPin pin,
            int x,
            int y,
            boolean input,
            int mouseX,
            int mouseY) {
        boolean hovered = mouseX >= x - 1
                && mouseX <= x + PIN_SIZE
                && mouseY >= y - 1
                && mouseY <= y + PIN_SIZE;
        int size = hovered ? PIN_HOVER_SIZE : PIN_SIZE;
        int left = x - (size - PIN_SIZE) / 2;
        int top = y - (size - PIN_SIZE) / 2;
        int color = pin.getColor();
        graphics.fill(
                left,
                top,
                left + size,
                top + size,
                pin.isConnected() || hovered ? color : color & 0x66FFFFFF);
        ComputedEditorStyle.drawPixelOutline(
                graphics,
                left,
                top,
                size,
                size,
                hovered ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.SOCKET_BORDER);
        int centerLeft = left + Math.max(1, size / 2 - 1);
        int centerTop = top + Math.max(1, size / 2 - 1);
        graphics.fill(
                centerLeft,
                centerTop,
                centerLeft + 2,
                centerTop + 2,
                ComputedEditorTheme.SOCKET_CENTER);
        String label = pin.getName();
        int textX = input
                ? x + 4 + CONTENT_INSET + PIN_LABEL_PADDING
                : x - CONTENT_INSET - PIN_LABEL_PADDING
                        - Minecraft.getInstance().font.width(label);
        graphics.drawString(
                Minecraft.getInstance().font,
                label,
                textX,
                y - 2,
                ComputedEditorTheme.TEXT_SECONDARY,
                false);
    }
}
