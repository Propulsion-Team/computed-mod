package dev.propulsionteam.computed.internal.node.api.elements;

import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Minimal dropdown / combobox. Closed: shows the currently selected option with a ▼ glyph; clicking
 * toggles the open state. Open: renders option rows below the header; clicking a row selects it and
 * closes. The element's height expands to cover the open list so click hit-testing still passes.
 */
public class WDropdown<T> extends WElement {
    private static final int HEADER_H = 14;
    private static final int ROW_H = 12;

    private final List<T> options;
    private final Function<T, String> labelFn;
    private final Consumer<T> onChange;
    private T selected;
    private boolean open;

    public WDropdown(int width, List<T> options, Function<T, String> labelFn, T initial, Consumer<T> onChange) {
        this.options = List.copyOf(options);
        this.labelFn = labelFn;
        this.onChange = onChange;
        this.selected = initial;
        this.onChange.accept(initial);
        this.width = width;
        this.height = HEADER_H;
    }

    public T getSelected() {
        return selected;
    }

    public void setSelected(T value) {
        this.selected = value;
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    private void setOpen(boolean v) {
        this.open = v;
        this.height = v ? HEADER_H + options.size() * ROW_H : HEADER_H;
        markLayoutDirty();
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean headerHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_H;

        ComputedEditorStyle.drawField(graphics, x, y, width, HEADER_H, open, headerHovered);
        String label = selected == null ? "—" : labelFn.apply(selected);
        graphics.drawString(
                font,
                label,
                x + 4,
                y + 3,
                headerHovered ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.TEXT_PRIMARY,
                false);
        String glyph = open ? "▲" : "▼";
        int gw = font.width(glyph);
        graphics.drawString(font, glyph, x + width - gw - 4, y + 3, ComputedEditorTheme.TEXT_SECONDARY, false);

        if (!open) {
            return;
        }
        int listY = y + HEADER_H;
        ComputedEditorStyle.drawMenuPanel(graphics, x, listY, width, options.size() * ROW_H);
        for (int i = 0; i < options.size(); i++) {
            int ry = listY + i * ROW_H;
            boolean rowHovered = mouseX >= x && mouseX <= x + width && mouseY >= ry && mouseY <= ry + ROW_H;
            T opt = options.get(i);
            boolean isSelected = opt.equals(selected);
            ComputedEditorStyle.drawMenuRow(graphics, x, ry, width, ROW_H, rowHovered, isSelected);
            graphics.drawString(font, labelFn.apply(opt), x + 6, ry + 2,
                    rowHovered
                            ? ComputedEditorTheme.TEXT_HEADER
                            : (isSelected ? ComputedEditorTheme.ACCENT : ComputedEditorTheme.TEXT_PRIMARY),
                    false);
        }
    }

    @Override
    public boolean handleMouseClick(double localX, double localY, int button) {
        if (button != 0 || localX < 0 || localX > width) {
            if (open) {
                setOpen(false);
                return true;
            }
            return false;
        }
        if (localY >= 0 && localY <= HEADER_H) {
            playClick();
            setOpen(!open);
            return true;
        }
        if (open) {
            double listLocal = localY - HEADER_H;
            int idx = (int) Math.floor(listLocal / ROW_H);
            if (idx >= 0 && idx < options.size()) {
                playClick();
                setSelected(options.get(idx));
                setOpen(false);
                return true;
            }
            setOpen(false);
            return true;
        }
        return false;
    }

    private static void playClick() {
        // No-op in shared code: keep dropdown server-loadable during graph deserialization.
    }
}
