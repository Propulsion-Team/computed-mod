package dev.propulsionteam.computed.content.nodes.widgets;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.WPin;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WTextField;
import dev.devce.websnodelib.internal.MenuCategories;
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
    public static final ResourceLocation MENU = MenuCategories.SOURCES;
    public static final Component LABEL = Component.literal("Text");

    public static void register() {
        NodeRegistry.register(TYPE_ID, TextSourceNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
