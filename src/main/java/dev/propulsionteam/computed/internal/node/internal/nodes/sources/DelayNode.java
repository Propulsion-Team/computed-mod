package dev.propulsionteam.computed.internal.node.internal.nodes.sources;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class DelayNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("delay");
    public static final ResourceLocation MENU = BuiltinNodeCategories.SOURCES;
    public static final Component LABEL = Component.literal("Delay");

    private final WSlider delay;
    private double[] buf = new double[1];
    private int head = 0;
    private int cap = 1;

    public DelayNode(int x, int y) {
        super(TYPE_ID, "Delay", x, y);
        delay = new WSlider("Delay (ticks)", 0, 200, 80);
        delay.setValue(1);
        addInput("In", 0xFF88CCFF);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel("Delays input N ticks"));
        addElement(delay);
        setEvaluator(n -> {
            int d = Math.max(0, (int) delay.getValue());
            int needed = Math.max(1, d + 1);
            if (needed != cap) {
                buf = new double[needed];
                head = 0;
                cap = needed;
            }
            WGraph g = n.evaluationGraph();
            if (g != null && g.isEvalTickPulseGate()) {
                buf[head] = n.getInputs().get(0).getValue();
                head = (head + 1) % cap;
            }
            n.getOutputs().get(0).setValue(buf[head]);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, DelayNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("BufferHead", head);
        ListTag values = new ListTag();
        for (double value : buf) values.add(DoubleTag.valueOf(value));
        tag.put("Buffer", values);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag values = tag.getList("Buffer", Tag.TAG_DOUBLE);
        if (!values.isEmpty() && values.size() <= 201) {
            buf = new double[values.size()];
            for (int i = 0; i < values.size(); i++) buf[i] = values.getDouble(i);
            cap = buf.length;
            head = Math.floorMod(tag.getInt("BufferHead"), cap);
        }
        getOutputs().get(0).setValue(buf[head]);
    }

    @Override
    public boolean isStateBoundary() { return true; }
}
