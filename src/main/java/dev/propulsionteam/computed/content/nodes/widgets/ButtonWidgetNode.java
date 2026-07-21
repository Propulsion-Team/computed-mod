package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.monitors.widgets.ButtonWidget;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ButtonWidgetNode extends WNode implements InteractiveWidgetNode {
    private final AtomicBoolean pendingPulse = new AtomicBoolean(false);
    private final WidgetLayoutFields layout =
            new WidgetLayoutFields(0, 0, 60, 20, LayoutManagedWidget.Fit.AUTO);

    public ButtonWidgetNode(int x, int y) {
        super(WidgetNodeIds.BUTTON_WIDGET, "Button Widget", x, y);
        layout.addTo(this);
        addInput("Label", WPin.DataType.STRING, WPin.COLOR_STRING_DEFAULT);
        addInput("Color", WPin.DataType.NUMBER, 0xFFFF66AA);
        addOutput("Widget", WPin.DataType.WIDGET, WPin.COLOR_WIDGET_DEFAULT);
        addOutput("Clicked", WPin.DataType.NUMBER, 0xFF00FF88);
        setEvaluator(n -> {
            String label = n.getInputs().get(0).getStringValue();
            int colorIn = (int) Math.round(n.getInputs().get(1).getValue());
            int color = colorIn == 0 ? 0xFF00FF88 : (colorIn | 0xFF000000);
            n.getOutputs().get(0).setWidgetValue(
                    layout.wrap(new ButtonWidget(n.getId(), layout.x(), layout.y(), layout.width(), layout.height(),
                            label == null ? "" : label, color)));
            n.getOutputs().get(1).setValue(pendingPulse.getAndSet(false) ? 1.0 : 0.0);
        });
    }

    @Override
    public void onWidgetInput(double value) {
        pendingPulse.set(true);
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

    public static final ResourceLocation TYPE_ID = WidgetNodeIds.BUTTON_WIDGET;
    public static final ResourceLocation MENU = ComputedMenuCategories.WIDGETS;
    public static final Component LABEL = Component.literal("Button Widget");

    public static void register() {
        NodeRegistry.register(TYPE_ID, ButtonWidgetNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
