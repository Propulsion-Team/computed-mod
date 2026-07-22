package dev.propulsionteam.computed.internal.node.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Tiny pixel icons adapted from Pathmind's temporary workspace icon language with permission from
 * its author. They are intentionally code-drawn so Computed has no runtime asset dependency.
 */
public final class ComputedEditorIcons {
    private ComputedEditorIcons() {}

    public static void drawCategory(GuiGraphics g, ResourceLocation id, int x, int y, int color) {
        String path = id.getPath();
        if (path.contains("math")) {
            g.hLine(x + 2, x + 10, y + 6, color); g.vLine(x + 6, y + 2, y + 10, color);
        } else if (path.contains("logic")) {
            g.renderOutline(x + 2, y + 2, 9, 9, color); g.fill(x + 5, y + 5, x + 8, y + 8, color);
        } else if (path.contains("widget") || path.contains("visual")) {
            g.renderOutline(x + 1, y + 2, 11, 9, color); g.hLine(x + 4, x + 8, y + 12, color);
        } else if (path.contains("source") || path.contains("input")) {
            g.hLine(x + 1, x + 8, y + 6, color); g.fill(x + 8, y + 3, x + 11, y + 10, color);
        } else if (path.contains("create") || path.contains("peripheral")) {
            g.renderOutline(x + 2, y + 2, 5, 5, color); g.renderOutline(x + 7, y + 7, 5, 5, color);
            g.hLine(x + 6, x + 8, y + 6, color);
        } else if (path.contains("organiz")) {
            g.renderOutline(x + 1, y + 2, 11, 9, color); g.hLine(x + 2, x + 10, y + 5, color);
        } else {
            int seed = Math.abs(id.hashCode());
            g.renderOutline(x + 2, y + 2, 9, 9, color);
            if ((seed & 1) == 0) g.hLine(x + 4, x + 9, y + 6, color);
            else g.vLine(x + 6, y + 4, y + 9, color);
        }
    }

    public static void drawHome(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 6, x + 10, y + 12, color);
        g.hLine(x + 4, x + 8, y + 4, color); g.hLine(x + 3, x + 9, y + 5, color);
    }

    public static void drawImportExport(GuiGraphics g, int x, int y, int color) {
        g.hLine(x + 1, x + 9, y + 4, color); g.fill(x + 8, y + 2, x + 11, y + 7, color);
        g.hLine(x + 3, x + 11, y + 10, color); g.fill(x + 1, y + 8, x + 4, y + 13, color);
    }

    public static void drawTrash(GuiGraphics g, int x, int y, int color) {
        g.renderOutline(x + 3, y + 4, 7, 8, color); g.hLine(x + 2, x + 10, y + 3, color);
        g.hLine(x + 5, x + 8, y + 1, color);
    }

    public static void drawCenterView(GuiGraphics g, int x, int y, int color) {
        g.hLine(x + 1, x + 4, y + 1, color); g.vLine(x + 1, y + 1, y + 4, color);
        g.hLine(x + 9, x + 12, y + 1, color); g.vLine(x + 12, y + 1, y + 4, color);
        g.hLine(x + 1, x + 4, y + 12, color); g.vLine(x + 1, y + 9, y + 12, color);
        g.hLine(x + 9, x + 12, y + 12, color); g.vLine(x + 12, y + 9, y + 12, color);
        g.fill(x + 6, y + 6, x + 8, y + 8, color);
    }

    public static void drawChevron(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 2, x + 4, y + 4, color);
        g.fill(x + 4, y + 4, x + 5, y + 6, color);
        g.fill(x + 3, y + 6, x + 4, y + 8, color);
    }

    public static void drawMenu(GuiGraphics g, int x, int y, int color) {
        g.hLine(x + 1, x + 12, y + 3, color);
        g.hLine(x + 1, x + 12, y + 6, color);
        g.hLine(x + 1, x + 12, y + 9, color);
    }
}
