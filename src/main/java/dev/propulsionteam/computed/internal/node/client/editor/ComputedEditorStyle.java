package dev.propulsionteam.computed.internal.node.client.editor;

import net.minecraft.client.gui.GuiGraphics;

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
        graphics.renderOutline(x, y, width, height, outerBorder);
        if (width > 3 && height > 3) {
            graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, innerBorder);
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
        graphics.renderOutline(
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
        graphics.renderOutline(
                x,
                y,
                width,
                height,
                active || hovered ? ComputedEditorTheme.ACCENT : ComputedEditorTheme.BORDER_DEFAULT);
    }

    public static void drawDangerButton(
            GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                hovered ? ComputedEditorTheme.DANGER_HOVER : ComputedEditorTheme.DANGER_BACKGROUND);
        graphics.renderOutline(x, y, width, height, ComputedEditorTheme.STATUS_ERROR);
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
