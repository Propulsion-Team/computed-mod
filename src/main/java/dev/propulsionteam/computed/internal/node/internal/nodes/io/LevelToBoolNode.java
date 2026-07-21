package dev.propulsionteam.computed.internal.node.internal.nodes.io;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LevelToBoolNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("level_to_bool");
    public static final ResourceLocation MENU = BuiltinNodeCategories.IO;
    public static final Component LABEL = Component.literal("Level -> Bool");

    public LevelToBoolNode(int x, int y) {
        super(TYPE_ID, "Level -> Bool", x, y);
        WSlider thresh = new WSlider("Threshold", 0, 15, 80);
        thresh.setValue(8);
        addInput("Level", 0xFFFFBB00);
        addOutput("Bool", 0xFF00FF88);
        addElement(new WLabel("1.0 if Level >= threshold"));
        addElement(thresh);
        setEvaluator(n -> n.getOutputs().get(0).setValue(
                n.getInputs().get(0).getValue() >= thresh.getValue() ? 1.0 : 0.0));
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, LevelToBoolNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
