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
 * Fixed boundary for the function return: named value inputs wired from the subgraph (mirrored as parent
 * outputs). Use {@linkplain #emptyCheckbox void} for no returns, or remove all return rows (same effect).
 */
public final class FunctionEndNode extends WNode {

    public static final ResourceLocation TYPE_FN_END =
            ResourceLocation.fromNamespaceAndPath("computed", "fn_end");

    private static final int COLOR_RETURN = 0xFFFFAA66;

    private final WCheckbox emptyCheckbox;
    private boolean emptyReturn;

    private final List<String> returnNames = new ArrayList<>();
    private final List<WTextField> returnFields = new ArrayList<>();

    public FunctionEndNode(int x, int y) {
        super(TYPE_FN_END, "End", x, y);
        emptyCheckbox = new WCheckbox("No output (void)");
        emptyCheckbox.setOnToggle(
                () -> {
                    emptyReturn = emptyCheckbox.isChecked();
                    if (emptyReturn) {
                        returnNames.clear();
                    } else if (returnNames.isEmpty()) {
                        returnNames.add("Out");
                    }
                    rebuildUiAndPins();
                });
        returnNames.add("Out");
        rebuildUiAndPins();
        setEvaluator(n -> {});
    }

    /** When true, there are no return pins on the parent card and no input pins here. */
    public boolean isEmptyReturn() {
        return emptyReturn;
    }

    /**
     * Refresh return pin labels from text fields and rebuild inputs only when structure changes. Call
     * before syncing the parent {@link FunctionCardNode}.
     */
    public void syncPinsFromUiFields() {
        syncReturnNamesFromFields();
        if (emptyReturn || returnNames.isEmpty()) {
            rebuildInputsFromState();
            updateLayout();
            return;
        }
        List<WPin> ins = getInputs();
        int expected = returnNames.size();
        if (ins.size() != expected) {
            rebuildInputsFromState();
            updateLayout();
            return;
        }
        for (int j = 0; j < returnNames.size(); j++) {
            String nm = returnNames.get(j).trim();
            if (nm.isEmpty()) {
                nm = "out" + (j + 1);
            }
            ins.get(j).setName(nm);
        }
        updateLayout();
    }

    private void syncReturnNamesFromFields() {
        for (int i = 0; i < returnFields.size() && i < returnNames.size(); i++) {
            String v = returnFields.get(i).getValue().trim();
            returnNames.set(i, v.isEmpty() ? ("out" + (i + 1)) : v);
        }
    }

    private void rebuildUiAndPins() {
        emptyReturn = emptyCheckbox.isChecked();
        returnFields.clear();
        getElements().clear();

        addElement(new WLabel("Return"));
        addElement(emptyCheckbox);
        emptyCheckbox.setChecked(emptyReturn);

        if (!emptyReturn) {
            for (int i = 0; i < returnNames.size(); i++) {
                final int idx = i;
                String nm = returnNames.get(i);
                WTextField tf = new WTextField(88);
                tf.setValue(nm);
                returnFields.add(tf);
                addElement(tf);
                addElement(new WButton("-", 22, () -> removeReturnAt(idx)));
            }
            addElement(new WButton("+ return", 72, this::addReturnPressed));
        }

        rebuildInputsFromState();
        updateLayout();
    }

    private void removeReturnAt(int idx) {
        if (idx < 0 || idx >= returnNames.size()) {
            return;
        }
        returnNames.remove(idx);
        if (returnNames.isEmpty()) {
            emptyReturn = true;
            emptyCheckbox.setChecked(true);
        }
        rebuildUiAndPins();
    }

    private void addReturnPressed() {
        returnNames.add("out" + (returnNames.size() + 1));
        rebuildUiAndPins();
    }

    private void rebuildInputsFromState() {
        getInputs().clear();
        markPinSchemaChanged();
        if (!emptyReturn) {
            for (String nm : returnNames) {
                addInput(nm.isEmpty() ? "out" : nm, COLOR_RETURN);
            }
        }
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
        tag.putBoolean("fnEmptyReturn", emptyReturn);
        ListTag list = new ListTag();
        for (String s : returnNames) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("fnReturns", list);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        emptyReturn = tag.contains("fnEmptyReturn") && tag.getBoolean("fnEmptyReturn");
        returnNames.clear();
        if (tag.contains("fnReturns")) {
            ListTag list = tag.getList("fnReturns", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                returnNames.add(list.getString(i));
            }
        }
        if (!emptyReturn && returnNames.isEmpty()) {
            returnNames.add("Out");
        }
        emptyCheckbox.setChecked(emptyReturn);
        rebuildUiAndPins();
    }

    public static void register() {
        NodeRegistry.register(TYPE_FN_END, FunctionEndNode::new);
        NodeMenuRegistry.hideFromAddMenu(TYPE_FN_END);
    }
}
