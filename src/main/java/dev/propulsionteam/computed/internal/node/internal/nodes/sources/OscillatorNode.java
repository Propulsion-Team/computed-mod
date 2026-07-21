package dev.propulsionteam.computed.internal.node.internal.nodes.sources;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OscillatorNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("oscillator");
    public static final ResourceLocation MENU = BuiltinNodeCategories.SOURCES;
    public static final Component LABEL = Component.literal("Oscillator");

    public OscillatorNode(int x, int y) {
        super(TYPE_ID, "Oscillator", x, y);
        addOutput("Wave", 0xFF00FFFF);
        WSlider freqSlider = new WSlider("Freq", 0.1, 5.0, 80);
        WSlider ampSlider = new WSlider("Amp", 1.0, 100.0, 80);
        addElement(new WLabel("Sine Wave Generator"));
        addElement(freqSlider);
        addElement(ampSlider);
        setEvaluator(n -> {
            double time = System.currentTimeMillis() / 1000.0;
            double val = Math.sin(time * freqSlider.getValue() * Math.PI * 2) * ampSlider.getValue();
            n.getOutputs().get(0).setValue(val);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, OscillatorNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
