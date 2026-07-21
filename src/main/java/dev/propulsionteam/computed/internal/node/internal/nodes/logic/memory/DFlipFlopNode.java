package dev.devce.websnodelib.internal.nodes.logic.memory;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DFlipFlopNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("d_flipflop");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_MEMORY;
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
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Q")) q = tag.getBoolean("Q");
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, DFlipFlopNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
