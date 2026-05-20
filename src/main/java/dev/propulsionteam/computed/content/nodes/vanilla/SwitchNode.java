package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.WPin;
import dev.devce.websnodelib.api.elements.WButton;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WTextField;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.propulsionteam.computed.Computed;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class SwitchNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "switch");

    private static final int MIN_OUTPUTS = 2;
    private static final int MAX_OUTPUTS = 16;

    private int outputCount = 2;
    private final List<WTextField> caseFields = new ArrayList<>();

    public SwitchNode(int x, int y) {
        super(TYPE_ID, "Switch", x, y);
        rebuildUiAndPins();
        setEvaluator(n -> {
            String sel = n.getInputs().get(0).getStringValue();
            if (sel == null) sel = "";
            for (int i = 0; i < n.getOutputs().size(); i++) {
                String expected = i < caseFields.size() ? caseFields.get(i).getValue() : "";
                n.getOutputs().get(i).setValue(sel.equals(expected) ? 1.0 : 0.0);
            }
        });
    }

    private void rebuildUiAndPins() {
        getInputs().clear();
        getOutputs().clear();
        getElements().clear();
        caseFields.clear();

        addInput("Selector", WPin.DataType.STRING, 0xFF00FF88);
        for (int i = 0; i < outputCount; i++) {
            addOutput("Case " + i, 0xFFFF5555);
        }

        addElement(new WLabel("Match selector -> case"));
        for (int i = 0; i < outputCount; i++) {
            WTextField field = new WTextField(50);
            caseFields.add(field);
            addElement(field);
        }
        addElement(new WButton("+ case", 40, () -> {
            if (outputCount < MAX_OUTPUTS) {
                List<String> snapshot = snapshotCaseValues();
                outputCount++;
                rebuildUiAndPins();
                restoreCaseValues(snapshot);
            }
        }));
        addElement(new WButton("- case", 40, () -> {
            if (outputCount > MIN_OUTPUTS) {
                List<String> snapshot = snapshotCaseValues();
                outputCount--;
                rebuildUiAndPins();
                restoreCaseValues(snapshot);
            }
        }));
        updateLayout();
    }

    private List<String> snapshotCaseValues() {
        List<String> out = new ArrayList<>(caseFields.size());
        for (WTextField f : caseFields) out.add(f.getValue());
        return out;
    }

    private void restoreCaseValues(List<String> values) {
        for (int i = 0; i < Math.min(values.size(), caseFields.size()); i++) {
            caseFields.get(i).setValue(values.get(i));
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("outputCount", outputCount);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("outputCount")) {
            outputCount = Mth.clamp(tag.getInt("outputCount"), MIN_OUTPUTS, MAX_OUTPUTS);
            rebuildUiAndPins();
        }
        super.load(tag);
    }

    public static final ResourceLocation MENU = MenuCategories.LOGIC_COMPARISON;
    public static final Component LABEL = Component.literal("Switch");

    public static void register() {
        NodeRegistry.register(TYPE_ID, SwitchNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
