package dev.devce.websnodelib.internal.nodes.sources;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WTextField;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ConstantNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("constant");
    public static final ResourceLocation MENU = MenuCategories.SOURCES;
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
