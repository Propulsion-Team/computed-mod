package dev.devce.websnodelib.internal.nodes.sources;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WSlider;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OscillatorNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("oscillator");
    public static final ResourceLocation MENU = MenuCategories.SOURCES;
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
