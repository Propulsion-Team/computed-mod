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

public final class SrLatchNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("sr_latch");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_MEMORY;
    public static final Component LABEL = Component.literal("SR Latch");

    private boolean q;
    private boolean prevSet;
    private boolean prevReset;

    public SrLatchNode(int x, int y) {
        super(TYPE_ID, "SR Latch", x, y);
        addInput("Set", 0xFF00FF88);
        addInput("Reset", 0xFFFF6666);
        addOutput("Q", 0xFFFF5555);
        addElement(new WLabel("SR Latch (rising edges)"));
        setEvaluator(n -> {
            boolean s = n.getInputs().get(0).getValue() > 0.5;
            boolean r = n.getInputs().get(1).getValue() > 0.5;
            if (r && !prevReset) q = false;
            else if (s && !prevSet) q = true;
            prevSet = s;
            prevReset = r;
            n.getOutputs().get(0).setValue(q ? 1.0 : 0.0);
        });
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putBoolean("Q", q);
        tag.putBoolean("PrevSet", prevSet);
        tag.putBoolean("PrevReset", prevReset);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Q")) q = tag.getBoolean("Q");
        prevSet = tag.getBoolean("PrevSet");
        prevReset = tag.getBoolean("PrevReset");
        getOutputs().get(0).setValue(q ? 1.0 : 0.0);
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, SrLatchNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }

    @Override
    public boolean isStateBoundary() { return true; }
}
