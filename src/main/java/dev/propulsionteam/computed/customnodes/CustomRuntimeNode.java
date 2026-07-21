package dev.propulsionteam.computed.customnodes;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.customnodes.expr.EvalContext;
import dev.propulsionteam.computed.customnodes.expr.FunctionRegistry;
import dev.propulsionteam.computed.customnodes.expr.Value;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

final class CustomRuntimeNode extends WNode {
    private final CustomNodeDefinition definition;
    /** Persistent state — keyed by state var name + call-site counters. Survives across ticks. */
    private final Map<String, Value> stateStore = new HashMap<>();

    CustomRuntimeNode(CustomNodeDefinition definition, int x, int y) {
        super(definition.id(), definition.label(), x, y);
        this.definition = definition;
        for (CustomNodeDefinition.PinSpec pin : definition.inputs()) {
            addInput(pin.name(), pin.dataType(), pin.color());
        }
        for (CustomNodeDefinition.OutputSpec out : definition.outputs()) {
            addOutput(out.name(), out.dataType(), out.color());
        }
        addElement(new WLabel(definition.label()));

        // Seed state store with initial values
        for (CustomNodeDefinition.StateSpec s : definition.state()) {
            stateStore.put(stateKey(s.name()), s.init());
        }

        setEvaluator(node -> evaluate((CustomRuntimeNode) node));
    }

    @Override
    public boolean isStateBoundary() {
        return !definition.state().isEmpty();
    }

    private static void evaluate(CustomRuntimeNode node) {
        CustomNodeDefinition def = node.definition;
        Map<String, Value> storeRef = node.stateStore;

        // Build vars: inputs + constants
        Map<String, Value> vars = new HashMap<>();
        for (int i = 0; i < def.inputs().size() && i < node.getInputs().size(); i++) {
            String key = def.inputs().get(i).name().toLowerCase(Locale.ROOT);
            WPin pin = node.getInputs().get(i);
            vars.put(key, pin.getDataType() == WPin.DataType.STRING
                    ? Value.of(pin.getStringValue())
                    : Value.of(pin.getValue()));
        }
        for (Map.Entry<String, Double> entry : def.constants().entrySet()) {
            vars.put(entry.getKey().toLowerCase(Locale.ROOT), Value.of(entry.getValue()));
        }
        // Expose current state vars as readable variables
        for (CustomNodeDefinition.StateSpec s : def.state()) {
            Value cur = storeRef.getOrDefault(stateKey(s.name()), s.init());
            vars.put(s.name().toLowerCase(Locale.ROOT), cur);
        }

        EvalContext ctx = new EvalContext(vars, storeRef, FunctionRegistry.get());

        // Update state vars (each update sees the *pre-tick* snapshot)
        if (!def.state().isEmpty()) {
            Map<String, Value> snapshot = ctx.snapshotVars();
            for (CustomNodeDefinition.StateSpec s : def.state()) {
                // Re-evaluate with snapshot so updates are independent of each other
                EvalContext snapCtx = new EvalContext(snapshot, storeRef, FunctionRegistry.get());
                Value updated = ExpressionEvaluator.eval(s.updateExpression(), snapCtx);
                storeRef.put(stateKey(s.name()), updated);
                // Update live var so outputs see new value
                ctx.setVar(s.name().toLowerCase(Locale.ROOT), updated);
            }
        }

        // Evaluate outputs
        ctx.resetCallSiteIndex();
        for (int i = 0; i < def.outputs().size() && i < node.getOutputs().size(); i++) {
            Value result = ExpressionEvaluator.eval(def.outputs().get(i).expression(), ctx);
            WPin out = node.getOutputs().get(i);
            if (out.getDataType() == WPin.DataType.STRING) {
                out.setStringValue(result.asString());
            } else {
                out.setValue(result.asNumber());
            }
        }
    }

    // --- NBT persistence ---

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        CompoundTag stateTag = new CompoundTag();
        for (Map.Entry<String, Value> entry : stateStore.entrySet()) {
            CompoundTag vTag = new CompoundTag();
            vTag.putString("t", entry.getValue().type().name());
            if (entry.getValue().isNumber()) {
                vTag.putDouble("v", entry.getValue().asNumber());
            } else {
                vTag.putString("v", entry.getValue().asString());
            }
            stateTag.put(entry.getKey(), vTag);
        }
        tag.put("computedState", stateTag);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("computedState")) {
            CompoundTag stateTag = tag.getCompound("computedState");
            for (String key : stateTag.getAllKeys()) {
                CompoundTag vTag = stateTag.getCompound(key);
                String typeStr = vTag.getString("t");
                Value v;
                if ("STRING".equals(typeStr)) {
                    v = Value.of(vTag.getString("v"));
                } else {
                    v = Value.of(vTag.getDouble("v"));
                }
                stateStore.put(key, v);
            }
        }
    }

    private static String stateKey(String name) {
        return "state_" + name.toLowerCase(Locale.ROOT);
    }
}
