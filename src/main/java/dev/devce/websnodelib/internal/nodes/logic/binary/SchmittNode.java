package dev.devce.websnodelib.internal.nodes.logic.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WSlider;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SchmittNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("schmitt");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("Schmitt");

    private boolean on;
    private final WSlider onThresh;
    private final WSlider offThresh;

    public SchmittNode(int x, int y) {
        super(TYPE_ID, "Schmitt", x, y);
        onThresh = new WSlider("On threshold", 0.0, 15.0, 80);
        onThresh.setValue(10.0);
        offThresh = new WSlider("Off threshold", 0.0, 15.0, 80);
        offThresh.setValue(5.0);
        addInput("In", 0xFF88CCFF);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("Hysteresis (on >= on, off <= off)"));
        addElement(onThresh);
        addElement(offThresh);
        setEvaluator(n -> {
            double v = n.getInputs().get(0).getValue();
            double hi = onThresh.getValue();
            double lo = offThresh.getValue();
            if (on) { if (v <= lo) on = false; }
            else { if (v >= hi) on = true; }
            n.getOutputs().get(0).setValue(on ? 1.0 : 0.0);
        });
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putBoolean("On", on);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("On")) on = tag.getBoolean("On");
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, SchmittNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
