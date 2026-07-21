package dev.devce.websnodelib.internal.nodes.logic.comparison;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WSlider;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ApproxNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("cmp_approx");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_COMPARISON;
    public static final Component LABEL = Component.literal("~=");

    public ApproxNode(int x, int y) {
        super(TYPE_ID, "~=", x, y);
        addInput("A", 0xFF00FF88);
        addInput("B", 0xFF88CCFF);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("A ~= B"));
        WSlider tolerance = new WSlider("Tolerance", 0.0, 15.0, 80);
        tolerance.setValue(0.5);
        addElement(tolerance);
        setEvaluator(n -> {
            double a = n.getInputs().get(0).getValue();
            double b = n.getInputs().get(1).getValue();
            n.getOutputs().get(0).setValue(Math.abs(a - b) <= tolerance.getValue() ? 1.0 : 0.0);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, ApproxNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
