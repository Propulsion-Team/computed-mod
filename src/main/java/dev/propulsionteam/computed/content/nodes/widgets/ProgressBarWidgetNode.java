package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ProgressBarWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ProgressBarWidgetNode extends WNode {
    private final WidgetLayoutFields layout =
            new WidgetLayoutFields(0, 0, 80, 8, LayoutManagedWidget.Fit.AUTO);
    private final WTextField segmentsField = new WTextField(60);

    public ProgressBarWidgetNode(int x, int y) {
        super(WidgetNodeIds.PROGRESS_BAR_WIDGET, "Progress Bar Widget", x, y);
        layout.addTo(this);
        addElement(new WLabel("Segments (0 = solid)", 0xFFAAAAAA));
        segmentsField.setValue("0");
        addElement(segmentsField);
        addInput("Value", WPin.DataType.NUMBER, 0xFFFFCC44);
        addInput("Max", WPin.DataType.NUMBER, 0xFFFFCC44);
        addInput("Color", WPin.DataType.NUMBER, 0xFFFF66AA);
        addOutput("Widget", WPin.DataType.WIDGET, WPin.COLOR_WIDGET_DEFAULT);
        setEvaluator(n -> {
            double v = n.getInputs().get(0).getValue();
            double m = n.getInputs().get(1).getValue();
            if (m <= 0) m = 1;
            int colorIn = (int) Math.round(n.getInputs().get(2).getValue());
            int color = colorIn == 0 ? 0xFF00FF88 : (colorIn | 0xFF000000);
            int segs = parseSegments();
            n.getOutputs().get(0).setWidgetValue(
                    layout.wrap(new ProgressBarWidget(n.getId(), layout.x(), layout.y(), layout.width(), layout.height(),
                            v, m, color, segs)));
        });
    }

    private int parseSegments() {
        String s = segmentsField.getValue();
        if (s == null || s.isEmpty()) return 0;
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(0, v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        layout.saveTo(tag);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        layout.loadFrom(tag);
    }

    public static final ResourceLocation TYPE_ID = WidgetNodeIds.PROGRESS_BAR_WIDGET;
    public static final ResourceLocation MENU = ComputedMenuCategories.WIDGETS;
    public static final Component LABEL = Component.literal("Progress Bar Widget");

    public static void register() {
        NodeRegistry.register(TYPE_ID, ProgressBarWidgetNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
