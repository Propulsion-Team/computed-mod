package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;

/**
 * Four inline numeric text fields (x, y, w, h) for a widget node, plus convenience parsing.
 * Each field is its own {@code WElement} so values persist through {@link WNode#save()}/{@link WNode#load(net.minecraft.nbt.CompoundTag)}.
 */
public final class WidgetGeometryFields {
    public final WTextField xField = new WTextField(60);
    public final WTextField yField = new WTextField(60);
    public final WTextField wField = new WTextField(60);
    public final WTextField hField = new WTextField(60);

    public WidgetGeometryFields(int defaultX, int defaultY, int defaultW, int defaultH) {
        xField.setValue(Integer.toString(defaultX));
        yField.setValue(Integer.toString(defaultY));
        wField.setValue(Integer.toString(defaultW));
        hField.setValue(Integer.toString(defaultH));
    }

    /** Adds the four fields (with a small label) to the node, in order. */
    public void addTo(WNode node) {
        node.addElement(new WLabel("X / Y / W / H", 0xFFAAAAAA));
        node.addElement(xField);
        node.addElement(yField);
        node.addElement(wField);
        node.addElement(hField);
    }

    public int x()      { return parseOr(xField.getValue(), 0); }
    public int y()      { return parseOr(yField.getValue(), 0); }
    public int width()  { return Math.max(1, parseOr(wField.getValue(), 50)); }
    public int height() { return Math.max(1, parseOr(hField.getValue(), 12)); }

    private static int parseOr(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
