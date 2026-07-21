package dev.devce.websnodelib.internal.nodes.sources;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WSlider;
import dev.devce.websnodelib.internal.MenuCategories;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TickNode extends WNode {
    public static final ResourceLocation TYPE_ID = WGraph.TICK_NODE_TYPE;
    public static final ResourceLocation MENU = MenuCategories.SOURCES;
    public static final Component LABEL = Component.literal("Tick");

    public TickNode(int x, int y) {
        super(TYPE_ID, "Tick", x, y);
        addOutput("Tick", 0xFF00FF88);
        addOutput("Delta time", 0xFF88CCFF);
        WSlider rate = new WSlider("Rate", 0, WGraph.MAX_TICK_RATE, 100);
        rate.setValue(WGraph.MAX_TICK_RATE);
        addElement(new WLabel("Graph clock"));
        addElement(rate);
        addElement(new WLabel("Rate: updates per second (0 = pause)"));
        setEvaluator(n -> {});
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, TickNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
