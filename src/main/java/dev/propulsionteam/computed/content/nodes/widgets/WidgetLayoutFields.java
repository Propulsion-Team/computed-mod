package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WDropdown;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conditional layout editor: normal line controls by default, manual X/Y/W/H only in advanced mode.
 */
public final class WidgetLayoutFields {
    private static final String NBT_MODE = "WidgetLayoutMode";
    private static final String NBT_FIT = "WidgetLayoutFit";

    private LayoutManagedWidget.LayoutMode mode = LayoutManagedWidget.LayoutMode.LINE;
    private LayoutManagedWidget.Fit fit;
    private final WDropdown<LayoutManagedWidget.LayoutMode> modeDropdown;
    private final WDropdown<LayoutManagedWidget.Fit> fitDropdown;
    private final LayoutElement element;

    public final WTextField xField = new WTextField(60);
    public final WTextField yField = new WTextField(60);
    public final WTextField wField = new WTextField(60);
    public final WTextField hField = new WTextField(60);
    public final WTextField lineField = new WTextField(60);
    public final WTextField scaleField = new WTextField(60);

    public WidgetLayoutFields(int defaultX, int defaultY, int defaultW, int defaultH,
                              LayoutManagedWidget.Fit defaultFit) {
        fit = defaultFit == null ? LayoutManagedWidget.Fit.AUTO : defaultFit;
        xField.setValue(Integer.toString(defaultX));
        yField.setValue(Integer.toString(defaultY));
        wField.setValue(Integer.toString(defaultW));
        hField.setValue(Integer.toString(defaultH));
        lineField.setValue("1");
        scaleField.setValue("1");
        modeDropdown = new WDropdown<>(
                92,
                List.of(LayoutManagedWidget.LayoutMode.values()),
                m -> "Layout: " + title(m.name()),
                mode,
                m -> mode = m);
        fitDropdown = new WDropdown<>(
                92,
                List.of(LayoutManagedWidget.Fit.values()),
                f -> "Fit: " + title(f.name()),
                fit,
                f -> fit = f);
        element = new LayoutElement();
        element.refreshSize();
    }

    public void addTo(WNode node) {
        node.addElement(element);
    }

    public Widget wrap(Widget raw) {
        return new LayoutManagedWidget(raw, mode, line(), scale(), fit);
    }

    public int x()      { return parseIntOr(xField.getValue(), 0); }
    public int y()      { return parseIntOr(yField.getValue(), 0); }
    public int width()  { return Math.max(1, parseIntOr(wField.getValue(), 50)); }
    public int height() { return Math.max(1, parseIntOr(hField.getValue(), 12)); }
    public int line()   { return Math.max(1, parseIntOr(lineField.getValue(), 1)); }

    public double scale() {
        return Math.max(1, parseIntOr(scaleField.getValue(), 1));
    }

    public void saveTo(CompoundTag tag) {
        tag.putString(NBT_MODE, mode.name());
        tag.putString(NBT_FIT, fit.name());
    }

    public void loadFrom(CompoundTag tag) {
        migrateLegacyGeometry(tag);
        if (tag.contains(NBT_MODE)) {
            try {
                mode = LayoutManagedWidget.LayoutMode.valueOf(tag.getString(NBT_MODE));
                modeDropdown.setSelected(mode);
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains(NBT_FIT)) {
            try {
                fit = LayoutManagedWidget.Fit.valueOf(tag.getString(NBT_FIT));
                fitDropdown.setSelected(fit);
            } catch (IllegalArgumentException ignored) {}
        }
        element.refreshSize();
    }

    private void migrateLegacyGeometry(CompoundTag nodeTag) {
        if (nodeTag.contains(NBT_MODE) || !nodeTag.contains("elements")) {
            return;
        }
        ListTag elements = nodeTag.getList("elements", 10);
        if (elements.size() < 5) {
            return;
        }
        copyLegacyField(elements, 1, xField);
        copyLegacyField(elements, 2, yField);
        copyLegacyField(elements, 3, wField);
        copyLegacyField(elements, 4, hField);
        mode = LayoutManagedWidget.LayoutMode.MANUAL;
        modeDropdown.setSelected(mode);
    }

    private static void copyLegacyField(ListTag elements, int index, WTextField field) {
        CompoundTag fieldTag = elements.getCompound(index);
        if (fieldTag.contains("value")) {
            field.setValue(fieldTag.getString("value"));
        }
    }

    private List<WElement> visibleChildren() {
        List<WElement> children = new ArrayList<>();
        children.add(modeDropdown);
        if (mode == LayoutManagedWidget.LayoutMode.MANUAL) {
            children.add(new WLabel("X / Y / W / H", 0xFFAAAAAA));
            children.add(xField);
            children.add(yField);
            children.add(wField);
            children.add(hField);
        } else {
            children.add(new WLabel("Line / Span / Fit", 0xFFAAAAAA));
            children.add(lineField);
            children.add(scaleField);
            children.add(fitDropdown);
        }
        return children;
    }

    private static int parseIntOr(String s, int fallback) {
        return (int) Math.round(parseDoubleOr(s, fallback));
    }

    private static double parseDoubleOr(String s, double fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String title(String raw) {
        return raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT);
    }

    private final class LayoutElement extends WElement {
        private LayoutElement() {
            this.width = 96;
            refreshSize();
        }

        private void refreshSize() {
            int h = 0;
            for (WElement child : visibleChildren()) {
                h += child.getHeight();
            }
            this.height = Math.max(1, h - padding * 2 - margin * 2);
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
            refreshSize();
            int cy = y;
            for (WElement child : visibleChildren()) {
                child.render(graphics, x, cy, mouseX, mouseY, partialTick);
                cy += child.getHeight();
            }
        }

        @Override
        public boolean handleMouseClick(double localX, double localY, int button) {
            boolean handled = false;
            double cy = 0;
            for (WElement child : visibleChildren()) {
                if (child.handleMouseClick(localX, localY - cy, button)) {
                    handled = true;
                }
                cy += child.getHeight();
            }
            refreshSize();
            return handled;
        }

        @Override
        public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
            double cy = 0;
            for (WElement child : visibleChildren()) {
                child.handleMouseRelease(mouseX, mouseY - cy, button);
                cy += child.getHeight();
            }
            return false;
        }

        @Override
        public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
            for (WElement child : visibleChildren()) {
                if (child.handleKeyPress(keyCode, scanCode, modifiers)) return true;
            }
            return false;
        }

        @Override
        public boolean handleCharTyped(char codePoint, int modifiers) {
            for (WElement child : visibleChildren()) {
                if (child.handleCharTyped(codePoint, modifiers)) return true;
            }
            return false;
        }

        @Override
        public boolean isFocused() {
            for (WElement child : visibleChildren()) {
                if (child.isFocused()) return true;
            }
            return false;
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_MODE, mode.name());
            tag.putString(NBT_FIT, fit.name());
            tag.put("x", xField.save());
            tag.put("y", yField.save());
            tag.put("w", wField.save());
            tag.put("h", hField.save());
            tag.put("line", lineField.save());
            tag.put("scale", scaleField.save());
            return tag;
        }

        @Override
        public void load(CompoundTag tag) {
            if (tag.contains(NBT_MODE)) {
                try {
                    mode = LayoutManagedWidget.LayoutMode.valueOf(tag.getString(NBT_MODE));
                    modeDropdown.setSelected(mode);
                } catch (IllegalArgumentException ignored) {}
            }
            if (tag.contains(NBT_FIT)) {
                try {
                    fit = LayoutManagedWidget.Fit.valueOf(tag.getString(NBT_FIT));
                    fitDropdown.setSelected(fit);
                } catch (IllegalArgumentException ignored) {}
            }
            if (tag.contains("x")) xField.load(tag.getCompound("x"));
            if (tag.contains("y")) yField.load(tag.getCompound("y"));
            if (tag.contains("w")) wField.load(tag.getCompound("w"));
            if (tag.contains("h")) hField.load(tag.getCompound("h"));
            if (tag.contains("line")) lineField.load(tag.getCompound("line"));
            if (tag.contains("scale")) scaleField.load(tag.getCompound("scale"));
            refreshSize();
        }
    }
}
