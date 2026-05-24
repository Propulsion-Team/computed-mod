package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
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

        graphics.fill(x, y, x + width, y + HEADER_H, headerHovered ? 0xFF444444 : 0xFF252525);
        graphics.renderOutline(x, y, width, HEADER_H, headerHovered ? 0xFF00FF88 : 0xFF666666);
        String label = selected == null ? "—" : labelFn.apply(selected);
        graphics.drawString(font, label, x + 4, y + 3, headerHovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        String glyph = open ? "▲" : "▼";
        int gw = font.width(glyph);
        graphics.drawString(font, glyph, x + width - gw - 4, y + 3, 0xFFAAAAAA);

        if (!open) {
            return;
        }
        int listY = y + HEADER_H;
        graphics.fill(x, listY, x + width, listY + options.size() * ROW_H, 0xFF1A1A1A);
        graphics.renderOutline(x, listY, width, options.size() * ROW_H, 0xFF666666);
        for (int i = 0; i < options.size(); i++) {
            int ry = listY + i * ROW_H;
            boolean rowHovered = mouseX >= x && mouseX <= x + width && mouseY >= ry && mouseY <= ry + ROW_H;
            T opt = options.get(i);
            boolean isSelected = opt.equals(selected);
            int bg = rowHovered ? 0xFF3A3A3A : (isSelected ? 0xFF2A2A2A : 0xFF1A1A1A);
            graphics.fill(x + 1, ry, x + width - 1, ry + ROW_H, bg);
            graphics.drawString(font, labelFn.apply(opt), x + 6, ry + 2,
                    rowHovered ? 0xFFFFFFFF : (isSelected ? 0xFF00FF88 : 0xFFCCCCCC));
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
