package dev.propulsionteam.computed.customnodes.expr;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FunctionRegistry {
    private static final FunctionRegistry INSTANCE = new FunctionRegistry();

    private final Map<String, ExprFunction> functions = new HashMap<>();

    private FunctionRegistry() {
        registerBuiltins();
    }

    public static FunctionRegistry get() {
        return INSTANCE;
    }

    public void register(String name, ExprFunction fn) {
        functions.put(name.toLowerCase(Locale.ROOT), fn);
    }

    public boolean has(String name) {
        return functions.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Value call(String name, List<Value> args, EvalContext ctx) {
        ExprFunction fn = functions.get(name.toLowerCase(Locale.ROOT));
        if (fn == null) throw new IllegalArgumentException("Unknown function: " + name);
        return fn.call(args, ctx);
    }

    private void registerBuiltins() {
        // Math — 2-arg
        register("min",   (a, c) -> Value.of(Math.min(num(a,0,"min"), num(a,1,"min"))));
        register("max",   (a, c) -> Value.of(Math.max(num(a,0,"max"), num(a,1,"max"))));
        register("pow",   (a, c) -> Value.of(Math.pow(num(a,0,"pow"), num(a,1,"pow"))));
        register("atan2", (a, c) -> Value.of(Math.atan2(num(a,0,"atan2"), num(a,1,"atan2"))));
        register("log",   (a, c) -> {
            double x = num(a, 0, "log");
            if (a.size() == 2) {
                double base = num(a, 1, "log");
                return Value.of(base <= 0 ? 0.0 : Math.log(Math.max(x, 1e-300)) / Math.log(base));
            }
            return Value.of(Math.log(Math.max(x, 1e-300)));
        });
        register("hypot", (a, c) -> Value.of(Math.hypot(num(a,0,"hypot"), num(a,1,"hypot"))));
        register("clamp", (a, c) -> {
            double v = num(a, 0, "clamp"), lo = num(a, 1, "clamp"), hi = num(a, 2, "clamp");
            return Value.of(Math.max(lo, Math.min(hi, v)));
        });
        register("lerp",  (a, c) -> {
            double lo = num(a, 0, "lerp"), hi = num(a, 1, "lerp"), t = num(a, 2, "lerp");
            return Value.of(lo + (hi - lo) * t);
        });

        // Math — 1-arg
        register("abs",   (a, c) -> Value.of(Math.abs(num(a, 0, "abs"))));
        register("sqrt",  (a, c) -> Value.of(Math.sqrt(Math.max(0, num(a, 0, "sqrt")))));
        register("floor", (a, c) -> Value.of(Math.floor(num(a, 0, "floor"))));
        register("ceil",  (a, c) -> Value.of(Math.ceil(num(a, 0, "ceil"))));
        register("round", (a, c) -> Value.of(Math.rint(num(a, 0, "round"))));
        register("sign",  (a, c) -> Value.of(Math.signum(num(a, 0, "sign"))));
        register("sin",   (a, c) -> Value.of(Math.sin(num(a, 0, "sin"))));
        register("cos",   (a, c) -> Value.of(Math.cos(num(a, 0, "cos"))));
        register("tan",   (a, c) -> Value.of(Math.tan(num(a, 0, "tan"))));
        register("asin",  (a, c) -> Value.of(Math.asin(num(a, 0, "asin"))));
        register("acos",  (a, c) -> Value.of(Math.acos(num(a, 0, "acos"))));
        register("atan",  (a, c) -> Value.of(Math.atan(num(a, 0, "atan"))));
        register("exp",   (a, c) -> Value.of(Math.exp(num(a, 0, "exp"))));
        register("rad",   (a, c) -> Value.of(Math.toRadians(num(a, 0, "rad"))));
        register("deg",   (a, c) -> Value.of(Math.toDegrees(num(a, 0, "deg"))));

        // Control
        register("if", (a, c) -> {
            requireArity("if", a, 3);
            return a.get(0).asBool() ? a.get(1) : a.get(2);
        });

        // String functions
        register("str", (a, c) -> {
            requireArity("str", a, 1);
            return Value.of(a.get(0).asString());
        });
        register("num", (a, c) -> {
            requireArity("num", a, 1);
            return Value.of(a.get(0).asNumber());
        });
        register("concat", (a, c) -> {
            if (a.isEmpty()) throw new IllegalArgumentException("concat requires at least 1 arg");
            StringBuilder sb = new StringBuilder();
            for (Value v : a) sb.append(v.asString());
            return Value.of(sb.toString());
        });
        register("len", (a, c) -> {
            requireArity("len", a, 1);
            return Value.of(a.get(0).asString().length());
        });
        register("substr", (a, c) -> {
            if (a.size() < 2) throw new IllegalArgumentException("substr(str, start[, end])");
            String s = a.get(0).asString();
            int start = (int) a.get(1).asNumber();
            int end = a.size() >= 3 ? (int) a.get(2).asNumber() : s.length();
            start = Math.max(0, Math.min(start, s.length()));
            end   = Math.max(start, Math.min(end, s.length()));
            return Value.of(s.substring(start, end));
        });
        register("upper", (a, c) -> {
            requireArity("upper", a, 1);
            return Value.of(a.get(0).asString().toUpperCase(Locale.ROOT));
        });
        register("lower", (a, c) -> {
            requireArity("lower", a, 1);
            return Value.of(a.get(0).asString().toLowerCase(Locale.ROOT));
        });
        register("contains", (a, c) -> {
            requireArity("contains", a, 2);
            return Value.ofBool(a.get(0).asString().contains(a.get(1).asString()));
        });
        register("starts_with", (a, c) -> {
            requireArity("starts_with", a, 2);
            return Value.ofBool(a.get(0).asString().startsWith(a.get(1).asString()));
        });
        register("ends_with", (a, c) -> {
            requireArity("ends_with", a, 2);
            return Value.ofBool(a.get(0).asString().endsWith(a.get(1).asString()));
        });
        register("replace", (a, c) -> {
            requireArity("replace", a, 3);
            return Value.of(a.get(0).asString().replace(a.get(1).asString(), a.get(2).asString()));
        });
        register("format", (a, c) -> {
            if (a.size() < 2) throw new IllegalArgumentException("format(fmt, args...)");
            Object[] fmtArgs = new Object[a.size() - 1];
            for (int i = 1; i < a.size(); i++) {
                Value v = a.get(i);
                fmtArgs[i - 1] = v.isNumber() ? v.asNumber() : v.asString();
            }
            return Value.of(String.format(a.get(0).asString(), fmtArgs));
        });

        // Stateful helpers
        register("prev", (a, c) -> {
            if (a.isEmpty()) throw new IllegalArgumentException("prev(x[, default])");
            Value current = a.get(0);
            Value def = a.size() >= 2 ? a.get(1) : Value.ZERO;
            String key = "__prev_" + c.nextCallSiteIndex();
            Value old = c.getState(key, def);
            c.setState(key, current);
            return old;
        });
        register("rising", (a, c) -> {
            requireArity("rising", a, 1);
            boolean cur = a.get(0).asBool();
            String key = "__rising_" + c.nextCallSiteIndex();
            boolean prev = c.getState(key, Value.ZERO).asBool();
            c.setState(key, Value.ofBool(cur));
            return Value.ofBool(cur && !prev);
        });
        register("falling", (a, c) -> {
            requireArity("falling", a, 1);
            boolean cur = a.get(0).asBool();
            String key = "__falling_" + c.nextCallSiteIndex();
            boolean prev = c.getState(key, Value.ZERO).asBool();
            c.setState(key, Value.ofBool(cur));
            return Value.ofBool(!cur && prev);
        });
        register("changed", (a, c) -> {
            requireArity("changed", a, 1);
            Value cur = a.get(0);
            String key = "__changed_" + c.nextCallSiteIndex();
            Value prev = c.getState(key, cur);
            c.setState(key, cur);
            return Value.ofBool(!cur.equals(prev));
        });
    }

    private static double num(List<Value> args, int idx, String name) {
        if (idx >= args.size()) throw new IllegalArgumentException(name + ": missing arg " + idx);
        return args.get(idx).asNumber();
    }

    private static void requireArity(String name, List<Value> args, int expected) {
        if (args.size() != expected)
            throw new IllegalArgumentException(name + " expects " + expected + " arg(s)");
    }
}
