package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WDropdown;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextAlignment;
import dev.propulsionteam.computed.content.monitors.widgets.TextWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class TextWidgetNode extends WNode {
    private TextAlignment alignment = TextAlignment.CENTER;
    private final WDropdown<TextAlignment> alignmentDropdown;
    private final WidgetLayoutFields layout =
            new WidgetLayoutFields(0, 0, 64, 12, LayoutManagedWidget.Fit.AUTO);

    public TextWidgetNode(int x, int y) {
        super(WidgetNodeIds.TEXT_WIDGET, "Text Widget", x, y);
        layout.addTo(this);
        alignmentDropdown = new WDropdown<>(
                92,
                List.of(TextAlignment.values()),
                a -> "Align: " + title(a.name()),
                alignment,
                a -> alignment = a);
        addElement(alignmentDropdown);
        addInput("Text", WPin.DataType.STRING, WPin.COLOR_STRING_DEFAULT);
        addInput("Color", WPin.DataType.NUMBER, 0xFFFF66AA);
        addOutput("Widget", WPin.DataType.WIDGET, WPin.COLOR_WIDGET_DEFAULT);
        setEvaluator(n -> {
            String text = n.getInputs().get(0).getStringValue();
            int colorIn = (int) Math.round(n.getInputs().get(1).getValue());
            int color = colorIn == 0 ? 0xFF00FF88 : (colorIn | 0xFF000000);
            n.getOutputs().get(0).setWidgetValue(
                    layout.wrap(new TextWidget(n.getId(), layout.x(), layout.y(), layout.width(), layout.height(),
                            text == null ? "" : text, color, alignment)));
        });
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("TextAlignment", alignment.name());
        layout.saveTo(tag);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TextAlignment")) {
            try {
                alignment = TextAlignment.valueOf(tag.getString("TextAlignment"));
                alignmentDropdown.setSelected(alignment);
            } catch (IllegalArgumentException ignored) {}
        }
        layout.loadFrom(tag);
    }

    private static String title(String raw) {
        return raw.charAt(0) + raw.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    public static final ResourceLocation TYPE_ID = WidgetNodeIds.TEXT_WIDGET;
    public static final ResourceLocation MENU = ComputedMenuCategories.WIDGETS;
    public static final Component LABEL = Component.literal("Text Widget");

    public static void register() {
        NodeRegistry.register(TYPE_ID, TextWidgetNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
