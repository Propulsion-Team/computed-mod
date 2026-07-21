package dev.propulsionteam.computed.internal.node.internal.nodes.logic.memory;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DFlipFlopNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("d_flipflop");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_MEMORY;
    public static final Component LABEL = Component.literal("D Flip-flop");

    private boolean q;
    private boolean prevClock;

    public DFlipFlopNode(int x, int y) {
        super(TYPE_ID, "D Flip-flop", x, y);
        addInput("Data", 0xFF88CCFF);
        addInput("Clock", 0xFF00FF88);
        addOutput("Q", 0xFFFF5555);
        addElement(new WLabel("Latches Data on Clock rise"));
        setEvaluator(n -> {
            boolean d = n.getInputs().get(0).getValue() > 0.5;
            boolean c = n.getInputs().get(1).getValue() > 0.5;
            if (c && !prevClock) q = d;
            prevClock = c;
            n.getOutputs().get(0).setValue(q ? 1.0 : 0.0);
        });
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putBoolean("Q", q);
        tag.putBoolean("PrevClock", prevClock);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Q")) q = tag.getBoolean("Q");
        prevClock = tag.getBoolean("PrevClock");
        getOutputs().get(0).setValue(q ? 1.0 : 0.0);
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, DFlipFlopNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }

    @Override
    public boolean isStateBoundary() { return true; }
}
