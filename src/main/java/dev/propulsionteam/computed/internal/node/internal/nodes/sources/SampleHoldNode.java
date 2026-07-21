package dev.propulsionteam.computed.internal.node.internal.nodes.sources;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SampleHoldNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("sample_hold");
    public static final ResourceLocation MENU = BuiltinNodeCategories.SOURCES;
    public static final Component LABEL = Component.literal("Sample & Hold");

    private double held = 0.0;
    private boolean prevClock;

    public SampleHoldNode(int x, int y) {
        super(TYPE_ID, "Sample & Hold", x, y);
        addInput("In", 0xFF88CCFF);
        addInput("Clock", 0xFF00FF88);
        addOutput("Held", 0xFFFF5555);
        addElement(new WLabel("Captures In on Clock rise"));
        setEvaluator(n -> {
            boolean c = n.getInputs().get(1).getValue() > 0.5;
            if (c && !prevClock) held = n.getInputs().get(0).getValue();
            prevClock = c;
            n.getOutputs().get(0).setValue(held);
        });
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putDouble("Held", held);
        tag.putBoolean("PrevClock", prevClock);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Held")) held = tag.getDouble("Held");
        prevClock = tag.getBoolean("PrevClock");
        getOutputs().get(0).setValue(held);
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, SampleHoldNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }

    @Override
    public boolean isStateBoundary() { return true; }
}
