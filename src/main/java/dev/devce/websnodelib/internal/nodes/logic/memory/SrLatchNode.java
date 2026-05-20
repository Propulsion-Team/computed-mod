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

public final class SrLatchNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("sr_latch");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_MEMORY;
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
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Q")) q = tag.getBoolean("Q");
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, SrLatchNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
