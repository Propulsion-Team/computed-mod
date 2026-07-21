package dev.propulsionteam.computed.internal.node.client.editor;

import dev.propulsionteam.computed.client.editor.EditorDetailLevel;
import dev.propulsionteam.computed.internal.node.api.WNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Draws component-free node bodies and crisp screen-space titles for compact editor levels. */
public final class NodeLodRenderer {
    public record VisualState(
            boolean hovered,
            boolean selected,
            boolean diagnosticError,
            boolean diagnosticWarning,
            boolean peripheralLocked) {}

    private record TitleKey(String title, int maximumWidth) {}

    private record PendingLabel(
            int x, int y, int width, int height, int drawOrder, String text, int color) {}

    private final List<PendingLabel> pendingLabels = new ArrayList<>();
    private final Map<TitleKey, String> fittedTitleCache = new HashMap<>();

    public void beginFrame() {
        pendingLabels.clear();
        if (fittedTitleCache.size() > 4096) {
            fittedTitleCache.clear();
        }
    }

    public void renderNode(
            GuiGraphics graphics,
            Font font,
            WNode node,
            VisualState state,
            EditorDetailLevel detailLevel,
            float contentScale,
            int screenLeft,
            int screenTop,
            int screenRight,
            int screenBottom,
            int drawOrder) {
        node.ensureLayoutUpToDate();
        renderBody(graphics, node, state, detailLevel, contentScale);
        queueLabel(
                font,
                node,
                state,
                screenLeft,
                screenTop,
                screenRight,
                screenBottom,
                drawOrder);
    }

    public void renderLabels(GuiGraphics graphics) {
        if (pendingLabels.isEmpty()) {
            return;
        }
        pendingLabels.sort(java.util.Comparator.comparingInt(PendingLabel::drawOrder));
        for (PendingLabel pending : pendingLabels) {
            graphics.fill(
                    pending.x() - 2,
                    pending.y() - 1,
                    pending.x() + pending.width() + 2,
                    pending.y() + pending.height() + 1,
                    0xE01A1A1A);
            graphics.drawString(
                    Minecraft.getInstance().font,
                    pending.text(),
                    pending.x(),
                    pending.y(),
                    pending.color(),
                    false);
        }
    }

    private static void renderBody(
            GuiGraphics graphics,
            WNode node,
            VisualState state,
            EditorDetailLevel detailLevel,
            float contentScale) {
        int x = node.getX();
        int y = node.getY();
        int width = Math.max(1, node.getWidth());
        int height = Math.max(1, node.getHeight());
        int fill = ComputedEditorTheme.nodeBody(state.hovered(), state.selected(), state.peripheralLocked());
        if (detailLevel == EditorDetailLevel.OVERVIEW && !state.hovered() && !state.selected()) {
            fill = ComputedEditorTheme.BACKGROUND_SECTION;
        }
        graphics.fill(x, y, x + width, y + height, fill);

        int stroke = Mth.clamp(
                Mth.ceil(1.0f / Math.max(0.1f, contentScale)),
                1,
                Math.max(1, Math.min(width, height) / 2));
        int accent = ComputedEditorTheme.nodeOutline(
                state.selected(),
                false,
                state.diagnosticError(),
                state.diagnosticWarning(),
                state.peripheralLocked());
        fillOutline(graphics, x, y, width, height, stroke, accent);
        graphics.fill(
                x + stroke,
                y + stroke,
                Math.max(x + stroke, x + width - stroke),
                Math.min(y + height, y + stroke * 2),
                accent);

        if (state.peripheralLocked() || state.diagnosticError() || state.diagnosticWarning()) {
            int badge = Math.min(Math.max(stroke * 3, 2), Math.max(2, Math.min(width, height) / 3));
            graphics.fill(x + width - badge, y, x + width, y + badge, accent);
        }
    }

    private void queueLabel(
            Font font,
            WNode node,
            VisualState state,
            int screenLeft,
            int screenTop,
            int screenRight,
            int screenBottom,
            int drawOrder) {
        int projectedWidth = Math.max(1, screenRight - screenLeft);
        int projectedHeight = Math.max(1, screenBottom - screenTop);
        int availableWidth = Math.max(projectedWidth - 6, 48);
        availableWidth = Math.min(180, availableWidth);
        String title = fitTitle(font, node.getTitle(), availableWidth);
        if (title.isEmpty()) {
            return;
        }
        int textWidth = font.width(title);
        int x = screenLeft + (projectedWidth - textWidth) / 2;
        int y = screenTop + (projectedHeight - font.lineHeight) / 2;
        int color = ComputedEditorTheme.nodeLabel(
                state.diagnosticError(), state.diagnosticWarning(), state.peripheralLocked());
        pendingLabels.add(new PendingLabel(
                x, y, textWidth, font.lineHeight, drawOrder, title, color));
    }

    private String fitTitle(Font font, String title, int maximumWidth) {
        String safeTitle = title == null ? "" : title;
        TitleKey key = new TitleKey(safeTitle, maximumWidth);
        return fittedTitleCache.computeIfAbsent(key, ignored -> {
            if (font.width(safeTitle) <= maximumWidth) {
                return safeTitle;
            }
            String ellipsis = "\u2026";
            int prefixWidth = maximumWidth - font.width(ellipsis);
            return prefixWidth <= 0 ? "" : font.plainSubstrByWidth(safeTitle, prefixWidth) + ellipsis;
        });
    }

    private static void fillOutline(
            GuiGraphics graphics, int x, int y, int width, int height, int stroke, int color) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, Math.min(bottom, y + stroke), color);
        graphics.fill(x, Math.max(y, bottom - stroke), right, bottom, color);
        graphics.fill(x, y + stroke, Math.min(right, x + stroke), Math.max(y + stroke, bottom - stroke), color);
        graphics.fill(
                Math.max(x, right - stroke),
                y + stroke,
                right,
                Math.max(y + stroke, bottom - stroke),
                color);
    }
}
