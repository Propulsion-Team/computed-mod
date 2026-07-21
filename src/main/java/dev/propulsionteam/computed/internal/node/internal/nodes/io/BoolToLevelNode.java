package dev.propulsionteam.computed.internal.node.internal.nodes.io;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BoolToLevelNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("bool_to_level");
    public static final ResourceLocation MENU = BuiltinNodeCategories.IO;
    public static final Component LABEL = Component.literal("Bool -> Level");

    public BoolToLevelNode(int x, int y) {
        super(TYPE_ID, "Bool -> Level", x, y);
        addInput("In", 0xFF00FF88);
        addOutput("Level", 0xFFFFBB00);
        addElement(new WLabel("> 0.5 -> 15, else 0"));
        setEvaluator(n -> n.getOutputs().get(0).setValue(n.getInputs().get(0).getValue() > 0.5 ? 15.0 : 0.0));
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, BoolToLevelNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
