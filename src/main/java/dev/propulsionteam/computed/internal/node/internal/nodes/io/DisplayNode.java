package dev.propulsionteam.computed.internal.node.internal.nodes.io;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DisplayNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("display");
    public static final ResourceLocation MENU = BuiltinNodeCategories.IO;
    public static final Component LABEL = Component.literal("Display");

    public DisplayNode(int x, int y) {
        super(TYPE_ID, "Display", x, y);
        addInput("Value", 0xFF5555FF);
        WLabel valLabel = new WLabel("0.00", 0xFF00FF88);
        addElement(new WLabel("Current value:"));
        addElement(valLabel);
        setEvaluator(n -> {
            double val = n.getInputs().get(0).getValue();
            valLabel.setText(String.format("%.2f", val));
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, DisplayNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
