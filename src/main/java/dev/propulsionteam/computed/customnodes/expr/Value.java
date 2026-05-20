package dev.propulsionteam.computed.customnodes.expr;

public final class Value {
    public enum Type { NUMBER, STRING }

    public static final Value ZERO = new Value(0.0);
    public static final Value ONE = new Value(1.0);
    public static final Value EMPTY_STRING = new Value("");

    private final Type type;
    private final double number;
    private final String string;

    private Value(double number) {
        this.type = Type.NUMBER;
        this.number = number;
        this.string = null;
    }

    private Value(String string) {
        this.type = Type.STRING;
        this.number = 0.0;
        this.string = string;
    }

    public static Value of(double d) {
        if (d == 0.0) return ZERO;
        if (d == 1.0) return ONE;
        return new Value(d);
    }

    public static Value of(String s) {
        if (s == null || s.isEmpty()) return EMPTY_STRING;
        return new Value(s);
    }

    public static Value ofBool(boolean b) {
        return b ? ONE : ZERO;
    }

    public Type type() { return type; }
    public boolean isNumber() { return type == Type.NUMBER; }
    public boolean isString() { return type == Type.STRING; }

    public double asNumber() {
        if (type == Type.NUMBER) return number;
        try { return Double.parseDouble(string); } catch (NumberFormatException e) { return 0.0; }
    }

    public String asString() {
        if (type == Type.STRING) return string;
        if (number == Math.floor(number) && !Double.isInfinite(number)) {
            return String.valueOf((long) number);
        }
        return String.valueOf(number);
    }

    public boolean asBool() {
        return type == Type.NUMBER ? number > 0.5 : !string.isEmpty();
    }

    /** Add: string concat when either side is a string, else numeric add. */
    public Value add(Value other) {
        if (type == Type.STRING || other.type == Type.STRING) {
            return Value.of(this.asString() + other.asString());
        }
        return Value.of(this.number + other.number);
    }

    @Override
    public String toString() {
        return type == Type.NUMBER ? asString() : "\"" + string + "\"";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Value v)) return false;
        if (type != v.type) return false;
        return type == Type.NUMBER ? Double.compare(number, v.number) == 0 : string.equals(v.string);
    }

    @Override
    public int hashCode() {
        return type == Type.NUMBER ? Double.hashCode(number) : string.hashCode();
    }
}
