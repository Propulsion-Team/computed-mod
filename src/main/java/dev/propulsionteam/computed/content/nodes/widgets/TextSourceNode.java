package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TextSourceNode extends WNode {
    private final WTextField textField;

    public TextSourceNode(int x, int y) {
        super(WidgetNodeIds.TEXT_SOURCE, "Text", x, y);
        addOutput("Text", WPin.DataType.STRING, WPin.COLOR_STRING_DEFAULT);
        textField = new WTextField(160);
        addElement(new WLabel("Text", 0xFFAAAAAA));
        addElement(textField);
        setEvaluator(n -> n.getOutputs().get(0).setStringValue(textField.getValue()));
    }

    public static final ResourceLocation TYPE_ID = WidgetNodeIds.TEXT_SOURCE;
    public static final ResourceLocation MENU = BuiltinNodeCategories.SOURCES;
    public static final Component LABEL = Component.literal("Text");

    public static void register() {
        NodeRegistry.register(TYPE_ID, TextSourceNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
