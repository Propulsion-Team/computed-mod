package dev.devce.websnodelib.internal.nodes.logic.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EdgeRiseNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("edge_rise");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("Edge Rise");

    private boolean prev;

    public EdgeRiseNode(int x, int y) {
        super(TYPE_ID, "Edge Rise", x, y);
        addInput("In", 0xFF88CCFF);
        addOutput("Pulse", 0xFF00FF88);
        addElement(new WLabel("1.0 for one eval on rising edge"));
        setEvaluator(n -> {
            boolean now = n.getInputs().get(0).getValue() > 0.5;
            boolean rise = now && !prev;
            prev = now;
            n.getOutputs().get(0).setValue(rise ? 1.0 : 0.0);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, EdgeRiseNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
