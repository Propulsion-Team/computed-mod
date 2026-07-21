package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.Computed;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class IfNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "if_branch");

    public IfNode(int x, int y) {
        super(TYPE_ID, "If", x, y);
        addInput("Condition", 0xFF00FF88);
        addOutput("true", 0xFF55FF55);
        addOutput("false", 0xFFFF5555);

        addElement(new WLabel(">0.5 -> true"));

        setEvaluator(n -> {
            boolean cond = n.getInputs().get(0).getValue() > 0.5;
            n.getOutputs().get(0).setValue(cond ? 1.0 : 0.0);
            n.getOutputs().get(1).setValue(cond ? 0.0 : 1.0);
        });
    }

    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_COMPARISON;
    public static final Component LABEL = Component.literal("If");

    public static void register() {
        NodeRegistry.register(TYPE_ID, IfNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
