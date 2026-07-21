package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import dev.propulsionteam.computed.content.monitors.widgets.SliderWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class SliderWidgetNode extends WNode implements InteractiveWidgetNode {
    private volatile double normalized = 0.0;
    private final WidgetLayoutFields layout =
            new WidgetLayoutFields(0, 0, 80, 12, LayoutManagedWidget.Fit.AUTO);
    private final WTextField stepField = new WTextField(60);

    public SliderWidgetNode(int x, int y) {
        super(WidgetNodeIds.SLIDER_WIDGET, "Slider Widget", x, y);
        layout.addTo(this);
        addElement(new WLabel("Step (0 = none)", 0xFFAAAAAA));
        stepField.setValue("0");
        addElement(stepField);
        addInput("Min", WPin.DataType.NUMBER, 0xFFFFCC44);
        addInput("Max", WPin.DataType.NUMBER, 0xFFFFCC44);
        addInput("Color", WPin.DataType.NUMBER, 0xFFFF66AA);
        addOutput("Widget", WPin.DataType.WIDGET, WPin.COLOR_WIDGET_DEFAULT);
        addOutput("Value", WPin.DataType.NUMBER, 0xFFFFCC44);
        setEvaluator(n -> {
            double min = n.getInputs().get(0).getValue();
            double max = n.getInputs().get(1).getValue();
            if (max < min) { double t = max; max = min; min = t; }
            int colorIn = (int) Math.round(n.getInputs().get(2).getValue());
            int color = colorIn == 0 ? 0xFF00FF88 : (colorIn | 0xFF000000);
            double v = min + Mth.clamp(normalized, 0.0, 1.0) * (max - min);
            double step = parseStep();
            if (step > 0) {
                v = min + Math.round((v - min) / step) * step;
                v = Mth.clamp(v, min, max);
            }
            n.getOutputs().get(0).setWidgetValue(
                    layout.wrap(new SliderWidget(n.getId(), layout.x(), layout.y(), layout.width(), layout.height(),
                            v, min, max, color, step)));
            n.getOutputs().get(1).setValue(v);
        });
    }

    private double parseStep() {
        String s = stepField.getValue();
        if (s == null || s.isEmpty()) return 0.0;
        try {
            double v = Double.parseDouble(s.trim());
            return v > 0 ? v : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Override
    public void onWidgetInput(double value) {
        normalized = Mth.clamp(value, 0.0, 1.0);
    }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putDouble("Normalized", normalized);
        layout.saveTo(tag);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Normalized")) normalized = tag.getDouble("Normalized");
        layout.loadFrom(tag);
    }

    public static final ResourceLocation TYPE_ID = WidgetNodeIds.SLIDER_WIDGET;
    public static final ResourceLocation MENU = ComputedMenuCategories.WIDGETS;
    public static final Component LABEL = Component.literal("Slider Widget");

    public static void register() {
        NodeRegistry.register(TYPE_ID, SliderWidgetNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
