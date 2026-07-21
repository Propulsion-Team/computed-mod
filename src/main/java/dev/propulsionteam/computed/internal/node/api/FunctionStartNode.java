package dev.propulsionteam.computed.internal.node.api;

import dev.propulsionteam.computed.internal.node.api.elements.WButton;
import dev.propulsionteam.computed.internal.node.api.elements.WCheckbox;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Fixed boundary inside a function body: optional tick outputs plus named argument outputs. Parent card
 * inputs mirror these in order. Tick pulses run only while the nested editor’s play control is active.
 */
public final class FunctionStartNode extends WNode {

    public static final ResourceLocation TYPE_FN_START =
            ResourceLocation.fromNamespaceAndPath("computed", "fn_start");

    /** Matches {@link dev.propulsionteam.computed.internal.node.internal.BuiltinNodes} tick node accent + delta tint (blue/cyan). */
    private static final int COLOR_TICK_PULSE = 0xFF5599FF;
    private static final int COLOR_DELTA_TIME = 0xFF88CCFF;

    private final WCheckbox tickCheckbox;
    private final List<String> argNames = new ArrayList<>();
    /** Parallel to {@link #argNames} while UI is built. */
    private final List<WTextField> argFields = new ArrayList<>();

    private boolean tickable;

    public FunctionStartNode(int x, int y) {
        super(TYPE_FN_START, "Start", x, y);
        tickCheckbox = new WCheckbox("Tickable");
        tickCheckbox.setOnToggle(
                () -> {
                    tickable = tickCheckbox.isChecked();
                    rebuildUiAndPins();
                });
        argNames.add("a");
        rebuildUiAndPins();
        setEvaluator(n -> {});
    }

    /**
     * Refresh argument pin labels from text fields and rebuild outputs only when structure changes. Call
     * before syncing the parent card.
     */
    public void syncPinsFromUiFields() {
        syncArgNamesFromFields();
        List<WPin> outs = getOutputs();
        int expected = (tickable ? 2 : 0) + argNames.size();
        if (outs.size() != expected) {
            rebuildOutputsFromState();
            updateLayout();
            return;
        }
        int idx = 0;
        if (tickable) {
            outs.get(0).setName("Tick");
            outs.get(1).setName("Delta time");
            idx = 2;
        }
        for (int j = 0; j < argNames.size(); j++) {
            String nm = argNames.get(j).trim();
            if (nm.isEmpty()) {
                nm = "arg" + (j + 1);
            }
            outs.get(idx + j).setName(nm);
        }
        updateLayout();
    }

    private void syncArgNamesFromFields() {
        for (int i = 0; i < argFields.size() && i < argNames.size(); i++) {
            String v = argFields.get(i).getValue().trim();
            argNames.set(i, v.isEmpty() ? ("arg" + (i + 1)) : v);
        }
    }

    private void rebuildUiAndPins() {
        tickable = tickCheckbox.isChecked();
        argFields.clear();
        getElements().clear();

        addElement(new WLabel("Start"));
        addElement(tickCheckbox);
        tickCheckbox.setChecked(tickable);

        for (int i = 0; i < argNames.size(); i++) {
            final int idx = i;
            String nm = argNames.get(i);
            WTextField tf = new WTextField(88);
            tf.setValue(nm);
            argFields.add(tf);
            addElement(tf);
            addElement(new WButton("-", 22, () -> removeArgAt(idx)));
        }
        addElement(new WButton("+ arg", 72, this::addArgPressed));

        rebuildOutputsFromState();
        updateLayout();
    }

    private void removeArgAt(int idx) {
        if (idx < 0 || idx >= argNames.size()) {
            return;
        }
        argNames.remove(idx);
        rebuildUiAndPins();
    }

    private void addArgPressed() {
        argNames.add("arg" + (argNames.size() + 1));
        rebuildUiAndPins();
    }

    private void rebuildOutputsFromState() {
        getOutputs().clear();
        markPinSchemaChanged();
        if (tickable) {
            addOutput("Tick", COLOR_TICK_PULSE);
            addOutput("Delta time", COLOR_DELTA_TIME);
        }
        for (String nm : argNames) {
            addOutput(nm.isEmpty() ? "arg" : nm, 0xFF88CCFF);
        }
    }

    public boolean isTickable() {
        return tickable;
    }

    @Override
    public boolean isDeletionLocked() {
        return true;
    }

    @Override
    public boolean isDuplicationLocked() {
        return true;
    }

    @Override
    public CompoundTag save() {
        syncPinsFromUiFields();
        CompoundTag tag = super.save();
        tag.putBoolean("fnTickable", tickable);
        ListTag list = new ListTag();
        for (String s : argNames) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("fnArgs", list);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tickable = tag.contains("fnTickable") && tag.getBoolean("fnTickable");
        argNames.clear();
        if (tag.contains("fnArgs")) {
            ListTag list = tag.getList("fnArgs", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                argNames.add(list.getString(i));
            }
        }
        tickCheckbox.setChecked(tickable);
        rebuildUiAndPins();
    }

    public static void register() {
        NodeRegistry.register(TYPE_FN_START, FunctionStartNode::new);
        NodeMenuRegistry.hideFromAddMenu(TYPE_FN_START);
    }
}
