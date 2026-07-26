package dev.propulsionteam.computed.lua.runtime;

import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class LuaValueCopies {
    private LuaValueCopies() {}

    public static LuaValue copy(LuaValue value) {
        return new Copier().copy(value == null ? LuaValue.NIL : value, 0);
    }

    public static Map<String, LuaValue> copyMap(Map<String, LuaValue> source) {
        Map<String, LuaValue> copied = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return copied;
        }
        Copier copier = new Copier();
        source.forEach((id, value) -> copied.put(id, copier.copy(value, 0)));
        return copied;
    }

    public static boolean equivalent(Map<String, LuaValue> left, Map<String, LuaValue> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, LuaValue> entry : right.entrySet()) {
            LuaValue previous = left.get(entry.getKey());
            if (previous == null || !equivalent(previous, entry.getValue(), 0)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equivalent(LuaValue left, LuaValue right, int depth) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.type() != right.type() || depth > LuaStateCodec.MAX_DEPTH) {
            return false;
        }
        if (left.isnil()) {
            return true;
        }
        if (left.isboolean()) {
            return left.toboolean() == right.toboolean();
        }
        if (left.isnumber()) {
            return Double.doubleToLongBits(left.todouble()) == Double.doubleToLongBits(right.todouble());
        }
        if (left.isstring()) {
            return left.raweq(right);
        }
        if (!left.istable()) {
            return false;
        }
        LuaTable leftTable = left.checktable();
        LuaTable rightTable = right.checktable();
        if (leftTable.keyCount() != rightTable.keyCount()) {
            return false;
        }
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = leftTable.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return true;
            }
            LuaValue rightValue = rightTable.get(key);
            if (rightValue.isnil() && !next.arg(2).isnil()) {
                return false;
            }
            if (!equivalent(next.arg(2), rightValue, depth + 1)) {
                return false;
            }
        }
    }

    private static final class Copier {
        private final Map<LuaTable, Boolean> activeTables = new IdentityHashMap<>();
        private int bytes;

        private LuaValue copy(LuaValue value, int depth) {
            if (depth > LuaStateCodec.MAX_DEPTH) {
                throw new IllegalArgumentException(
                        "Lua state exceeds the maximum table depth of " + LuaStateCodec.MAX_DEPTH);
            }
            if (value.isnil()) {
                addBytes(4);
                return LuaValue.NIL;
            }
            if (value.isboolean()) {
                addBytes(10);
                return value;
            }
            if (value.isnumber()) {
                if (!Double.isFinite(value.todouble())) {
                    throw new IllegalArgumentException("Lua state contains a non-finite number");
                }
                addBytes(14);
                return value;
            }
            if (value.isstring()) {
                addBytes(8 + value.tojstring().getBytes(StandardCharsets.UTF_8).length);
                return value;
            }
            if (!value.istable()) {
                throw new IllegalArgumentException("Lua state cannot persist " + value.typename());
            }
            LuaTable source = value.checktable();
            if (activeTables.put(source, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Lua state contains a cyclic table");
            }
            LuaTable target = new LuaTable();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = source.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                target.set(copyKey(key), copy(next.arg(2), depth + 1));
            }
            activeTables.remove(source);
            addBytes(8);
            return target;
        }

        private LuaValue copyKey(LuaValue key) {
            if (key.isnumber()) {
                double number = key.todouble();
                long integer = (long) number;
                if (!Double.isFinite(number) || number != integer) {
                    throw new IllegalArgumentException("Lua table keys must be strings or integers");
                }
                addBytes(16);
                return key;
            }
            if (key.isstring()) {
                addBytes(8 + key.tojstring().getBytes(StandardCharsets.UTF_8).length);
                return key;
            }
            throw new IllegalArgumentException("Lua table keys must be strings or integers");
        }

        private void addBytes(int amount) {
            bytes = Math.addExact(bytes, amount);
            if (bytes > LuaStateCodec.MAX_BYTES) {
                throw new IllegalArgumentException("Lua state exceeds the four-megabyte program limit");
            }
        }
    }
}
