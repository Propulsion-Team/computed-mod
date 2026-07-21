package dev.propulsionteam.computed.internal.node.internal.nodes.math.unary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AverageNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_average");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Average");

    private final WSlider window;
    private double[] buf = new double[1];
    private int head = 0;
    private int size = 0;
    private int cap = 1;
    private double sum = 0.0;

    public AverageNode(int x, int y) {
        super(TYPE_ID, "Average", x, y);
        window = new WSlider("Window (ticks)", 1, 100, 80);
        window.setValue(20);
        addInput("In", 0xFF88CCFF);
        addOutput("Mean", 0xFFFF5555);
        addElement(new WLabel("Windowed mean (per tick)"));
        addElement(window);
        setEvaluator(n -> {
            int w = Math.max(1, (int) window.getValue());
            if (w != cap) {
                buf = new double[w];
                head = 0;
                size = 0;
                sum = 0.0;
                cap = w;
            }
            WGraph g = n.evaluationGraph();
            if (g != null && g.isEvalTickPulseGate()) {
                double in = n.getInputs().get(0).getValue();
                if (size == cap) sum -= buf[head];
                else size++;
                buf[head] = in;
                sum += in;
                head = (head + 1) % cap;
            }
            double mean = size == 0 ? 0.0 : sum / size;
            n.getOutputs().get(0).setValue(mean);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, AverageNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
