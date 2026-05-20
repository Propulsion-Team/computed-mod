package dev.propulsionteam.computed.customnodes.expr;

import java.util.HashMap;
import java.util.Map;

public final class EvalContext {
    private final Map<String, Value> vars;
    private final Map<String, Value> state;
    private final FunctionRegistry functions;
    private int callSiteIndex = 0;

    public EvalContext(Map<String, Value> vars, Map<String, Value> state, FunctionRegistry functions) {
        this.vars = new HashMap<>(vars);
        this.state = state;
        this.functions = functions;
    }

    // --- Variable access ---

    public Value getVar(String name) {
        return vars.get(name);
    }

    public boolean hasVar(String name) {
        return vars.containsKey(name);
    }

    /** Set or shadow a local variable (from assignment statements). */
    public void setVar(String name, Value value) {
        vars.put(name, value);
    }

    // --- Persistent state ---

    public Value getState(String key, Value defaultValue) {
        return state.getOrDefault(key, defaultValue);
    }

    public void setState(String key, Value value) {
        state.put(key, value);
    }

    // --- Call-site counter (resets each tick via resetCallSiteIndex) ---

    public int nextCallSiteIndex() {
        return callSiteIndex++;
    }

    public void resetCallSiteIndex() {
        callSiteIndex = 0;
    }

    // --- Function dispatch ---

    public boolean hasFunction(String name) {
        return functions.has(name);
    }

    public Value callFunction(String name, java.util.List<Value> args) {
        return functions.call(name, args, this);
    }

    /** Snapshot current vars (used before running state update expressions). */
    public Map<String, Value> snapshotVars() {
        return new HashMap<>(vars);
    }
}
