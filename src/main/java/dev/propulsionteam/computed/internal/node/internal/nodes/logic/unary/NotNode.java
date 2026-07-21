package dev.devce.websnodelib.internal.nodes.logic.unary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NotNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("logic_not");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_UNARY;
    public static final Component LABEL = Component.literal("NOT");

    public NotNode(int x, int y) {
        super(TYPE_ID, "NOT", x, y);
        addInput("A", 0xFF00FF88);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("NOT"));
        setEvaluator(n -> n.getOutputs().get(0).setValue(n.getInputs().get(0).getValue() > 0.5 ? 0.0 : 1.0));
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, NotNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
