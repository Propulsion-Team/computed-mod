package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WDropdown;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.monitors.widgets.ClockWidget;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextAlignment;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ClockWidgetNode extends WNode {
    public enum Format { HH_MM, HH_MM_SS;
        @Override public String toString() { return this == HH_MM ? "HH:MM" : "HH:MM:SS"; }
    }

    private Format format = Format.HH_MM;
    private TextAlignment alignment = TextAlignment.CENTER;
    private final WDropdown<Format> formatDropdown;
    private final WDropdown<TextAlignment> alignmentDropdown;
    private final WidgetLayoutFields layout =
            new WidgetLayoutFields(0, 0, 60, 12, LayoutManagedWidget.Fit.AUTO);

    public ClockWidgetNode(int x, int y) {
        super(WidgetNodeIds.CLOCK_WIDGET, "Clock Widget", x, y);
        layout.addTo(this);
        formatDropdown = new WDropdown<>(
                90,
                List.of(Format.values()),
                f -> "Format: " + f,
                format,
                f -> format = f);
        addElement(formatDropdown);
        alignmentDropdown = new WDropdown<>(
                92,
                List.of(TextAlignment.values()),
                a -> "Align: " + title(a.name()),
                alignment,
                a -> alignment = a);
        addElement(alignmentDropdown);
        addInput("Color", WPin.DataType.NUMBER, 0xFFFF66AA);
        addOutput("Widget", WPin.DataType.WIDGET, WPin.COLOR_WIDGET_DEFAULT);
        setEvaluator(n -> {
            int colorIn = (int) Math.round(n.getInputs().get(0).getValue());
            int color = colorIn == 0 ? 0xFF00FF88 : (colorIn | 0xFF000000);
            n.getOutputs().get(0).setWidgetValue(
                    layout.wrap(new ClockWidget(n.getId(), layout.x(), layout.y(), layout.width(), layout.height(),
                            color, format == Format.HH_MM_SS, alignment)));
        });
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("ClockFormat", format.name());
        tag.putString("ClockAlignment", alignment.name());
        layout.saveTo(tag);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("ClockFormat")) {
            try {
                format = Format.valueOf(tag.getString("ClockFormat"));
                formatDropdown.setSelected(format);
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("ClockAlignment")) {
            try {
                alignment = TextAlignment.valueOf(tag.getString("ClockAlignment"));
                alignmentDropdown.setSelected(alignment);
            } catch (IllegalArgumentException ignored) {}
        }
        layout.loadFrom(tag);
    }

    private static String title(String raw) {
        return raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT);
    }

    public static final ResourceLocation TYPE_ID = WidgetNodeIds.CLOCK_WIDGET;
    public static final ResourceLocation MENU = ComputedMenuCategories.WIDGETS;
    public static final Component LABEL = Component.literal("Clock Widget");

    public static void register() {
        NodeRegistry.register(TYPE_ID, ClockWidgetNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
