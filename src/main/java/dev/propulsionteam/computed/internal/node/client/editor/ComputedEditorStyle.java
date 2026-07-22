package dev.propulsionteam.computed.internal.node.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/** Shared one-pixel editor primitives. All coordinates and hit boxes remain owned by callers. */
public final class ComputedEditorStyle {
    private ComputedEditorStyle() {}

    public static void drawBeveledPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawBeveledPanel(
                graphics,
                x,
                y,
                width,
                height,
                ComputedEditorTheme.BACKGROUND_SECONDARY,
                ComputedEditorTheme.BORDER_MENU,
                ComputedEditorTheme.BORDER_INNER);
    }

    public static void drawMenuPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawBeveledPanel(
                graphics,
                x,
                y,
                width,
                height,
                ComputedEditorTheme.MENU_BACKGROUND,
                ComputedEditorTheme.BORDER_MENU,
                ComputedEditorTheme.BORDER_INNER);
    }

    public static void drawBeveledPanel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int fill,
            int outerBorder,
            int innerBorder) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, fill);
        drawPixelOutline(graphics, x, y, width, height, outerBorder);
        if (width > 3 && height > 3) {
            int inset = pixelStroke(graphics);
            drawPixelOutline(
                    graphics, x + inset, y + inset, width - inset * 2, height - inset * 2, innerBorder);
        }
    }

    public static void drawField(
            GuiGraphics graphics, int x, int y, int width, int height, boolean focused, boolean hovered) {
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                hovered && !focused ? ComputedEditorTheme.BACKGROUND_SECTION : ComputedEditorTheme.BACKGROUND_INPUT);
        drawPixelOutline(
                graphics,
                x,
                y,
                width,
                height,
                focused
                        ? ComputedEditorTheme.ACCENT
                        : hovered ? ComputedEditorTheme.BORDER_HIGHLIGHT : ComputedEditorTheme.BORDER_DEFAULT);
    }

    public static void drawButton(
            GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean active) {
        int fill = active
                ? ComputedEditorTheme.BUTTON_ACTIVE
                : hovered ? ComputedEditorTheme.BUTTON_HOVER : ComputedEditorTheme.BUTTON_BACKGROUND;
        graphics.fill(x, y, x + width, y + height, fill);
        drawPixelOutline(
                graphics,
                x,
                y,
                width,
                height,
                active || hovered ? ComputedEditorTheme.ACCENT : ComputedEditorTheme.BORDER_DEFAULT);
    }

    /** Centers text from the real font metrics instead of caller-specific baseline offsets. */
    public static void drawCenteredString(
            GuiGraphics graphics, Font font, String text, int x, int y, int width, int height, int color) {
        int textX = x + (width - font.width(text)) / 2;
        int textY = y + (height - font.lineHeight) / 2 + 1;
        graphics.drawString(font, text, textX, textY, color, false);
    }

    /**
     * GuiGraphics texture blits are immediate while fills are buffered inside drawManaged. Flush the
     * button/background batch before an icon so later buffered chrome cannot paint over the texture.
     */
    public static void beginTextureIcon(GuiGraphics graphics) {
        graphics.flush();
    }

    /** Returns the graph-space stroke needed to cover at least one physical GUI pixel. */
    public static int pixelStroke(GuiGraphics graphics) {
        float scale = Math.abs(graphics.pose().last().pose().m00());
        return Math.max(1, (int) Math.ceil(1.0f / Math.max(0.001f, scale)));
    }

    /** Integer-aligned outline whose stroke cannot disappear when graph content is zoomed out. */
    public static void drawPixelOutline(
            GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        int stroke = Math.min(pixelStroke(graphics), Math.max(1, Math.min(width, height) / 2));
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, Math.min(bottom, y + stroke), color);
        graphics.fill(x, Math.max(y, bottom - stroke), right, bottom, color);
        graphics.fill(x, y + stroke, Math.min(right, x + stroke), Math.max(y + stroke, bottom - stroke), color);
        graphics.fill(Math.max(x, right - stroke), y + stroke, right, Math.max(y + stroke, bottom - stroke), color);
    }

    public static void drawDangerButton(
            GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                hovered ? ComputedEditorTheme.DANGER_HOVER : ComputedEditorTheme.DANGER_BACKGROUND);
        drawPixelOutline(graphics, x, y, width, height, ComputedEditorTheme.STATUS_ERROR);
    }

    public static void drawMenuRow(
            GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean selected) {
        if (!hovered && !selected) {
            return;
        }
        graphics.fill(
                x + 1,
                y,
                x + width - 1,
                y + height,
                hovered ? ComputedEditorTheme.MENU_HOVER : ComputedEditorTheme.MENU_SELECTED);
    }

    public static void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + Math.max(0, width), y + 1, ComputedEditorTheme.MENU_SEPARATOR);
    }

    public static void drawScrollbar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int thumbY,
            int thumbHeight,
            boolean hovered) {
        graphics.fill(x, y, x + width, y + height, ComputedEditorTheme.BACKGROUND_PRIMARY);
        graphics.fill(
                x,
                thumbY,
                x + width,
                thumbY + thumbHeight,
                hovered ? ComputedEditorTheme.BORDER_HIGHLIGHT : ComputedEditorTheme.BORDER_DEFAULT);
    }
}
