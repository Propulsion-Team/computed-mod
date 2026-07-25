package dev.propulsionteam.computed.integration.computercraft;

import dan200.computercraft.api.lua.LuaException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class ComputerCraftValueCodec {
    private static final int MAX_DEPTH = 16;

    private ComputerCraftValueCodec() {}

    static Object toJava(LuaValue value) throws LuaException {
        return toJava(value, 0, new IdentityHashMap<>());
    }

    static LuaValue toLua(Object value) throws LuaException {
        return toLua(value, 0, new IdentityHashMap<>());
    }

    static LuaTable results(Object[] values) throws LuaException {
        LuaTable result = new LuaTable();
        if (values != null) {
            for (int index = 0; index < values.length; index++) {
                result.set(index + 1, toLua(values[index]));
            }
        }
        return result;
    }

    static Object normalize(Object value) throws LuaException {
        return toJava(toLua(value));
    }

    private static Object toJava(
            LuaValue value,
            int depth,
            IdentityHashMap<LuaValue, Boolean> active) throws LuaException {
        if (value == null || value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new LuaException("Non-finite numbers are not supported");
            }
            return number;
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        if (!value.istable()) {
            throw new LuaException("Unsupported Lua value: " + value.typename());
        }
        if (depth >= MAX_DEPTH) {
            throw new LuaException("Table nesting exceeds 16 levels");
        }
        LuaTable table = value.checktable();
        if (active.put(table, Boolean.TRUE) != null) {
            throw new LuaException("Cyclic tables are not supported");
        }
        Map<Object, Object> result = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            Object convertedKey;
            if (key.isint()) {
                convertedKey = key.toint();
            } else if (key.isstring()) {
                convertedKey = key.tojstring();
            } else {
                throw new LuaException("Table keys must be strings or integers");
            }
            result.put(convertedKey, toJava(next.arg(2), depth + 1, active));
        }
        active.remove(table);
        return result;
    }

    private static LuaValue toLua(
            Object value,
            int depth,
            IdentityHashMap<Object, Boolean> active) throws LuaException {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof LuaValue lua) {
            return toLua(toJava(lua), depth, active);
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Number number) {
            double converted = number.doubleValue();
            if (!Double.isFinite(converted)) {
                throw new LuaException("Non-finite numbers are not supported");
            }
            return LuaValue.valueOf(converted);
        }
        if (value instanceof Character character) {
            return LuaValue.valueOf(character.toString());
        }
        if (value instanceof String string) {
            return LuaValue.valueOf(string);
        }
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copied = buffer.slice();
            byte[] bytes = new byte[copied.remaining()];
            copied.get(bytes);
            return LuaValue.valueOf(bytes);
        }
        if (depth >= MAX_DEPTH) {
            throw new LuaException("Table nesting exceeds 16 levels");
        }
        if (active.put(value, Boolean.TRUE) != null) {
            throw new LuaException("Cyclic tables are not supported");
        }
        LuaTable table = new LuaTable();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                LuaValue key = switch (entry.getKey()) {
                    case String string -> LuaValue.valueOf(string);
                    case Byte byteValue -> LuaValue.valueOf(byteValue.intValue());
                    case Short shortValue -> LuaValue.valueOf(shortValue.intValue());
                    case Integer integer -> LuaValue.valueOf(integer);
                    case Long longValue when longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE ->
                            LuaValue.valueOf(longValue.intValue());
                    default -> throw new LuaException("Table keys must be strings or integers");
                };
                table.set(key, toLua(entry.getValue(), depth + 1, active));
            }
        } else if (value instanceof Iterable<?> iterable) {
            int index = 1;
            for (Object item : iterable) {
                table.set(index++, toLua(item, depth + 1, active));
            }
        } else if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                table.set(index + 1, toLua(Array.get(value, index), depth + 1, active));
            }
        } else {
            active.remove(value);
            throw new LuaException("Unsupported ComputerCraft value: " + value.getClass().getName());
        }
        active.remove(value);
        return table;
    }
}
