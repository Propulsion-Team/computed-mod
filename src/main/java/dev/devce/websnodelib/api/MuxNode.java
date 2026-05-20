package dev.devce.websnodelib.api;

import dev.devce.websnodelib.api.elements.WButton;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Multiplexer with a configurable input count (2-16). {@code Select} input is floored and clamped to an
 * index, routing the corresponding {@code In i} pin's value to {@code Out}. Use the +/- buttons to grow
 * or shrink the data-input set.
 */
public final class MuxNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("websnodelib", "mux");

    private static final int MIN_INPUTS = 2;
    private static final int MAX_INPUTS = 16;

    private int inputCount = 2;

    public MuxNode(int x, int y) {
        super(TYPE_ID, "MUX", x, y);
        rebuildUiAndPins();
        setEvaluator(n -> {
            int count = n.getInputs().size() - 1;
            if (count <= 0) {
                n.getOutputs().get(0).setValue(0.0);
                return;
            }
            double sel = n.getInputs().get(0).getValue();
            int idx = Mth.clamp((int) Math.floor(sel), 0, count - 1);
            n.getOutputs().get(0).setValue(n.getInputs().get(1 + idx).getValue());
        });
    }

    private void rebuildUiAndPins() {
        getInputs().clear();
        getOutputs().clear();
        getElements().clear();

        addInput("Select", 0xFF00FF88);
        for (int i = 0; i < inputCount; i++) {
            addInput("In " + i, 0xFF88CCFF);
        }
        addOutput("Out", 0xFFFF5555);

        addElement(new WLabel("Select picks In 0.." + (inputCount - 1)));
        addElement(new WButton("+ in", 40, () -> {
            if (inputCount < MAX_INPUTS) {
                inputCount++;
                rebuildUiAndPins();
            }
        }));
        addElement(new WButton("- in", 40, () -> {
            if (inputCount > MIN_INPUTS) {
                inputCount--;
                rebuildUiAndPins();
            }
        }));
        updateLayout();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("inputCount", inputCount);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("inputCount")) {
            int requested = tag.getInt("inputCount");
            inputCount = Mth.clamp(requested, MIN_INPUTS, MAX_INPUTS);
            rebuildUiAndPins();
        }
        super.load(tag);
    }

    public static final ResourceLocation MENU = MenuCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("MUX");

    public static void register() {
        NodeRegistry.register(TYPE_ID, MuxNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
