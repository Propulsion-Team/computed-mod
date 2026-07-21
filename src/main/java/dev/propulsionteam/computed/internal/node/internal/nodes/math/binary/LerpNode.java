package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LerpNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_lerp");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Lerp");

    public LerpNode(int x, int y) {
        super(TYPE_ID, "Lerp", x, y);
        addInput("A", 0xFF88CCFF);
        addInput("B", 0xFF88CCFF);
        addInput("T", 0xFF00FF88);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("A + (B - A) * T"));
        setEvaluator(n -> {
            double a = n.getInputs().get(0).getValue();
            double b = n.getInputs().get(1).getValue();
            double t = n.getInputs().get(2).getValue();
            n.getOutputs().get(0).setValue(a + (b - a) * t);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, LerpNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
