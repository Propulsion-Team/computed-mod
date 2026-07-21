package dev.propulsionteam.computed.internal.node.api;

import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * On each rising edge of {@code In} (&gt; 0.5), counts toward {@code N}. On the Nth rise, {@code Out} is the
 * current {@code In} value for that one evaluation, then the count resets. Otherwise {@code Out} is 0.
 * {@code Reset} rising edge clears the count with no output.
 */
public final class PassOnNthRisingEdgeNode extends WNode {

    /** Kept for graph save compatibility. */
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("computed", "pass_every_n");

    private int edgesSeen;
    private boolean prevInHigh;
    private boolean prevResetHigh;

    private final WTextField nField;

    public PassOnNthRisingEdgeNode(int x, int y) {
        super(TYPE_ID, "Pass on Nth rise", x, y);
        addInput("In", 0xFF00FF88);
        addInput("Reset", 0xFFFF6666);
        addOutput("Out", 0xFFFFBB00);

        addElement(new WLabel("Out = In once on Nth rise (>0.5)"));
        addElement(new WLabel("N (integer ≥1)"));
        nField = new WTextField(72);
        nField.setValue("3");
        addElement(nField);

        setEvaluator(this::evaluateSelf);
    }

    private void evaluateSelf(WNode n) {
        int nTarget = parsePositiveInt(nField, 3, 1_000_000);

        n.getOutputs().get(0).setValue(0.0);

        boolean inHigh = n.getInputs().get(0).getValue() > 0.5;
        boolean resetHigh = n.getInputs().size() > 1 && n.getInputs().get(1).getValue() > 0.5;
        boolean resetRise = resetHigh && !prevResetHigh;
        boolean inRise = inHigh && !prevInHigh;
        prevResetHigh = resetHigh;
        prevInHigh = inHigh;

        if (resetRise) {
            edgesSeen = 0;
            return;
        }
        if (inRise) {
            edgesSeen++;
            if (edgesSeen >= nTarget) {
                n.getOutputs().get(0).setValue(n.getInputs().get(0).getValue());
                edgesSeen = 0;
            }
        }
    }

    private static int parsePositiveInt(WTextField field, int fallback, int max) {
        try {
            String s = field.getValue().trim();
            if (s.isEmpty()) {
                return fallback;
            }
            int v = Integer.parseInt(s.replace(",", ""));
            return Mth.clamp(v, 1, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("EdgesSeen", edgesSeen);
        tag.putBoolean("PrevInHigh", prevInHigh);
        tag.putBoolean("PrevResetHigh", prevResetHigh);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("EdgesSeen")) {
            edgesSeen = tag.getInt("EdgesSeen");
        }
        prevInHigh = tag.getBoolean("PrevInHigh");
        prevResetHigh = tag.getBoolean("PrevResetHigh");
    }

    public static final ResourceLocation MENU = BuiltinNodeCategories.SOURCES;
    public static final Component LABEL = Component.literal("Pass on Nth rise");

    public static void register() {
        NodeRegistry.register(TYPE_ID, PassOnNthRisingEdgeNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }

    @Override
    public boolean isStateBoundary() { return true; }
}
