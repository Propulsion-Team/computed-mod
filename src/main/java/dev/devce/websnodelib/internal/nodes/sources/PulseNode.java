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

public final class PulseNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("pulse");
    public static final ResourceLocation MENU = MenuCategories.SOURCES;
    public static final Component LABEL = Component.literal("Pulse");

    private int phase = 0;

    public PulseNode(int x, int y) {
        super(TYPE_ID, "Pulse", x, y);
        addOutput("Tick", 0xFF00FF88);
        WSlider cooldown = new WSlider("Cooldown (ticks)", 1, 20, 100);
        cooldown.setValue(20);
        addElement(new WLabel("Pulses 1.0 every N ticks"));
        addElement(cooldown);
        setEvaluator(n -> {
            int cd = (int) cooldown.getValue();
            if (cd <= 0) {
                n.getOutputs().get(0).setValue(1.0);
                phase = 0;
                return;
            }
            WGraph g = n.evaluationGraph();
            if (g != null && g.isEvalTickPulseGate()) {
                phase = (phase + 1) % (2 * cd);
            }
            n.getOutputs().get(0).setValue(phase < cd ? 1.0 : 0.0);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, PulseNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
