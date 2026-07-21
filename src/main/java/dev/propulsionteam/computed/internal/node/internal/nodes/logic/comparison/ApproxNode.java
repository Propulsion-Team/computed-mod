package dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ApproxNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("cmp_approx");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_COMPARISON;
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
