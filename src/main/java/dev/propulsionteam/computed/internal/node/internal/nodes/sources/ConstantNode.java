package dev.propulsionteam.computed.internal.node.internal.nodes.sources;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ConstantNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("constant");
    public static final ResourceLocation MENU = BuiltinNodeCategories.SOURCES;
    public static final Component LABEL = Component.literal("Constant");

    public ConstantNode(int x, int y) {
        super(TYPE_ID, "Constant", x, y);
        addOutput("Value", 0xFFFFBB00);
        WTextField valField = new WTextField(60);
        valField.setValue("10.0");
        addElement(new WLabel("Value:"));
        addElement(valField);
        setEvaluator(n -> {
            try {
                n.getOutputs().get(0).setValue(Double.parseDouble(valField.getValue()));
            } catch (Exception ignored) {}
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, ConstantNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
