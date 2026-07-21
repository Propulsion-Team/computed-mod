package dev.propulsionteam.computed.internal.node.internal.nodes.math.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ClampNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_clamp");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Clamp");

    public ClampNode(int x, int y) {
        super(TYPE_ID, "Clamp", x, y);
        addInput("x", 0xFF88CCFF);
        addInput("Min", 0xFF00FF88);
        addInput("Max", 0xFFFF6666);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("clamp(x, min, max)"));
        setEvaluator(n -> {
            double v = n.getInputs().get(0).getValue();
            double a = n.getInputs().get(1).getValue();
            double b = n.getInputs().get(2).getValue();
            double lo = Math.min(a, b);
            double hi = Math.max(a, b);
            n.getOutputs().get(0).setValue(Mth.clamp(v, lo, hi));
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, ClampNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
