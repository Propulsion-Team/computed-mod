package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ConcatenateTextNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "concatenate_strings");

    public ConcatenateTextNode(int x, int y) {
        super(TYPE_ID, "Concatenate", x, y);
        addInput("A", WPin.DataType.STRING, 0xFFFF0000);
        addInput("B", WPin.DataType.STRING, 0xFF0000FF);
        addOutput("text",WPin.DataType.STRING ,0xFF00FF00);

        addElement(new WLabel("A + B = AB"));

        setEvaluator(n -> {
            String concatedString = n.getInputs().get(0).getStringValue() + n.getInputs().get(1).getStringValue();
            n.getOutputs().get(0).setStringValue(concatedString);
        });
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.VANILLA;
    public static final Component LABEL = Component.literal("Concatenate Strings");

    public static void register() {
        NodeRegistry.register(TYPE_ID, ConcatenateTextNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}