package dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class EdgeFallNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("edge_fall");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("Edge Fall");

    private boolean prev;

    public EdgeFallNode(int x, int y) {
        super(TYPE_ID, "Edge Fall", x, y);
        addInput("In", 0xFF88CCFF);
        addOutput("Pulse", 0xFF00FF88);
        addElement(new WLabel("1.0 for one eval on falling edge"));
        setEvaluator(n -> {
            boolean now = n.getInputs().get(0).getValue() > 0.5;
            boolean fall = !now && prev;
            prev = now;
            n.getOutputs().get(0).setValue(fall ? 1.0 : 0.0);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, EdgeFallNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putBoolean("Previous", prev);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        prev = tag.getBoolean("Previous");
        getOutputs().get(0).setValue(0.0);
    }

    @Override
    public boolean isStateBoundary() { return true; }
}
