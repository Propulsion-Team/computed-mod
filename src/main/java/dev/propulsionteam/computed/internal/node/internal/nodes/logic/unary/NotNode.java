package dev.propulsionteam.computed.internal.node.internal.nodes.logic.unary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NotNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("logic_not");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_UNARY;
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
