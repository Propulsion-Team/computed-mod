package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MapNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_map");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Map");

    public MapNode(int x, int y) {
        super(TYPE_ID, "Map", x, y);
        addInput("x", 0xFF88CCFF);
        addInput("In min", 0xFF00FF88);
        addInput("In max", 0xFF00FF88);
        addInput("Out min", 0xFFFFBB00);
        addInput("Out max", 0xFFFFBB00);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("Linear remap"));
        setEvaluator(n -> {
            double x_ = n.getInputs().get(0).getValue();
            double i0 = n.getInputs().get(1).getValue();
            double i1 = n.getInputs().get(2).getValue();
            double o0 = n.getInputs().get(3).getValue();
            double o1 = n.getInputs().get(4).getValue();
            if (i1 == i0) n.getOutputs().get(0).setValue(o0);
            else n.getOutputs().get(0).setValue(o0 + (x_ - i0) * (o1 - o0) / (i1 - i0));
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, MapNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
