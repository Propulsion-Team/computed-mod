package dev.devce.websnodelib.internal.nodes.io;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WSlider;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LevelToBoolNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("level_to_bool");
    public static final ResourceLocation MENU = MenuCategories.IO;
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
