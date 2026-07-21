package dev.devce.websnodelib.internal.nodes.sources;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WSlider;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DelayNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("delay");
    public static final ResourceLocation MENU = MenuCategories.SOURCES;
    public static final Component LABEL = Component.literal("Delay");

    private final WSlider delay;
    private double[] buf = new double[1];
    private int head = 0;
    private int cap = 1;

    public DelayNode(int x, int y) {
        super(TYPE_ID, "Delay", x, y);
        delay = new WSlider("Delay (ticks)", 0, 200, 80);
        delay.setValue(1);
        addInput("In", 0xFF88CCFF);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("Delays input N ticks"));
        addElement(delay);
        setEvaluator(n -> {
            int d = Math.max(0, (int) delay.getValue());
            int needed = Math.max(1, d + 1);
            if (needed != cap) {
                buf = new double[needed];
                head = 0;
                cap = needed;
            }
            WGraph g = n.evaluationGraph();
            if (g != null && g.isEvalTickPulseGate()) {
                buf[head] = n.getInputs().get(0).getValue();
                head = (head + 1) % cap;
            }
            n.getOutputs().get(0).setValue(buf[head]);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, DelayNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
