package dev.devce.websnodelib.api;

import dev.devce.websnodelib.api.elements.WButton;
import dev.devce.websnodelib.api.elements.WCheckbox;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WTextField;
import dev.devce.websnodelib.internal.MenuCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Counts on {@code Step} rising edges, optional {@code Reset} to min, optional {@code Auto} on each graph tick
 * pulse (same gating as the Tick node in world mode). Output is clamped between Min and Max from the number
 * fields.
 */
public final class CounterNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("websnodelib", "counter");

    private double count;
    private boolean prevStepHigh;
    private boolean prevResetHigh;

    private final WCheckbox autoOnTickPulse;
    private final WTextField minField;
    private final WTextField maxField;
    private final WLabel countLabel;

    public CounterNode(int x, int y) {
        super(TYPE_ID, "Counter", x, y);
        addInput("Step", 0xFF00FF88);
        addInput("Reset", 0xFFFF6666);
        addOutput("Count", 0xFFFFBB00);

        addElement(new WLabel("Step / Reset: rising >0.5"));
        autoOnTickPulse = new WCheckbox("Auto (+1 on tick pulse)");
        addElement(autoOnTickPulse);
        addElement(new WLabel("Min"));
        minField = new WTextField(72);
        minField.setValue("0");
        addElement(minField);
        addElement(new WLabel("Max"));
        maxField = new WTextField(72);
        maxField.setValue("1000000");
        addElement(maxField);
        countLabel = new WLabel("0", 0xFFFFFF00);
        addElement(countLabel);
        addElement(new WButton("+1", 56, () -> {
            bumpTowardMax();
            refreshCountLabel();
        }));

        setEvaluator(this::evaluateSelf);
        refreshCountLabel();
    }

    private void evaluateSelf(WNode n) {
        double lo = Math.min(parseDouble(minField, 0.0), parseDouble(maxField, 1_000_000.0));
        double hi = Math.max(parseDouble(minField, 0.0), parseDouble(maxField, 1_000_000.0));

        boolean stepHigh = n.getInputs().size() > 0 && n.getInputs().get(0).getValue() > 0.5;
        boolean resetHigh = n.getInputs().size() > 1 && n.getInputs().get(1).getValue() > 0.5;
        boolean stepRise = stepHigh && !prevStepHigh;
        boolean resetRise = resetHigh && !prevResetHigh;
        prevStepHigh = stepHigh;
        prevResetHigh = resetHigh;

        if (resetRise) {
            count = lo;
        } else if (stepRise) {
            count = Mth.clamp(count + 1.0, lo, hi);
        } else if (autoOnTickPulse.isChecked()) {
            WGraph g = n.evaluationGraph();
            if (g != null && g.isEvalTickPulseGate()) {
                count = Mth.clamp(count + 1.0, lo, hi);
            }
        }

        count = Mth.clamp(count, lo, hi);
        n.getOutputs().get(0).setValue(count);
        refreshCountLabel();
    }

    private void bumpTowardMax() {
        double lo = Math.min(parseDouble(minField, 0.0), parseDouble(maxField, 1_000_000.0));
        double hi = Math.max(parseDouble(minField, 0.0), parseDouble(maxField, 1_000_000.0));
        count = Mth.clamp(count + 1.0, lo, hi);
        getOutputs().get(0).setValue(count);
    }

    private static double parseDouble(WTextField field, double fallback) {
        try {
            String s = field.getValue().trim().replace(',', '.');
            if (s.isEmpty()) {
                return fallback;
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void refreshCountLabel() {
        if (Math.abs(count - Math.rint(count)) < 1e-9) {
            countLabel.setText(String.valueOf((long) Math.rint(count)));
        } else {
            countLabel.setText(String.format("%.4g", count));
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putDouble("Count", count);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Count")) {
            count = tag.getDouble("Count");
        }
        refreshCountLabel();
    }

    public static final ResourceLocation MENU = MenuCategories.SOURCES;
    public static final Component LABEL = Component.literal("Counter");

    public static void register() {
        NodeRegistry.register(TYPE_ID, CounterNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
