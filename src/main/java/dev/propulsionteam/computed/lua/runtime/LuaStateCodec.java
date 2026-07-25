package dev.propulsionteam.computed.lua.runtime;

import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class LuaStateCodec {
    public static final int MAX_DEPTH = 16;
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    public CompoundTag encode(LuaValue value) {
        Counter counter = new Counter();
        CompoundTag encoded = encode(value == null ? LuaValue.NIL : value, 0, new IdentityHashMap<>(), counter);
        if (counter.bytes > MAX_BYTES) {
            throw new IllegalArgumentException("Lua state exceeds the four-megabyte program limit");
        }
        return encoded;
    }

    public LuaValue decode(CompoundTag tag) {
        return decodeValue(tag, 0);
    }

    private CompoundTag encode(
            LuaValue value,
            int depth,
            Map<LuaTable, Boolean> activeTables,
            Counter counter) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Lua state exceeds the maximum table depth of " + MAX_DEPTH);
        }
        CompoundTag tag = new CompoundTag();
        if (value.isnil()) {
            tag.putString("type", "nil");
            counter.add(4);
            return tag;
        }
        if (value.isboolean()) {
            tag.putString("type", "boolean");
            tag.putBoolean("value", value.toboolean());
            counter.add(10);
            return tag;
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("Lua state contains a non-finite number");
            }
            tag.putString("type", "number");
            tag.putDouble("value", number);
            counter.add(14);
            return tag;
        }
        if (value.isstring()) {
            String string = value.tojstring();
            tag.putString("type", "string");
            tag.putString("value", string);
            counter.add(8 + string.getBytes(StandardCharsets.UTF_8).length);
            return tag;
        }
        if (!value.istable()) {
            throw new IllegalArgumentException("Lua state cannot persist " + value.typename());
        }
        LuaTable table = value.checktable();
        if (activeTables.put(table, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Lua state contains a cyclic table");
        }
        tag.putString("type", "table");
        ListTag entries = new ListTag();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            LuaValue entryValue = next.arg(2);
            CompoundTag entry = new CompoundTag();
            entry.put("key", encodeKey(key, counter));
            entry.put("value", encode(entryValue, depth + 1, activeTables, counter));
            entries.add(entry);
        }
        activeTables.remove(table);
        tag.put("entries", entries);
        counter.add(8);
        return tag;
    }

    private CompoundTag encodeKey(LuaValue key, Counter counter) {
        CompoundTag tag = new CompoundTag();
        if (key.isnumber()) {
            double number = key.todouble();
            long integer = (long) number;
            if (!Double.isFinite(number) || number != integer) {
                throw new IllegalArgumentException("Lua table keys must be strings or integers");
            }
            tag.putString("type", "integer");
            tag.putLong("value", integer);
            counter.add(16);
            return tag;
        }
        if (key.isstring()) {
            String string = key.tojstring();
            tag.putString("type", "string");
            tag.putString("value", string);
            counter.add(8 + string.getBytes(StandardCharsets.UTF_8).length);
            return tag;
        }
        throw new IllegalArgumentException("Lua table keys must be strings or integers");
    }

    private LuaValue decodeValue(CompoundTag tag, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Encoded Lua state exceeds the maximum table depth");
        }
        return switch (tag.getString("type")) {
            case "nil" -> LuaValue.NIL;
            case "boolean" -> LuaValue.valueOf(tag.getBoolean("value"));
            case "number" -> decodeNumber(tag);
            case "string" -> LuaValue.valueOf(tag.getString("value"));
            case "table" -> decodeTable(tag, depth);
            default -> throw new IllegalArgumentException("Unknown encoded Lua value type: " + tag.getString("type"));
        };
    }

    private LuaValue decodeNumber(CompoundTag tag) {
        double number = tag.getDouble("value");
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Encoded Lua state contains a non-finite number");
        }
        return LuaValue.valueOf(number);
    }

    private LuaTable decodeTable(CompoundTag tag, int depth) {
        LuaTable table = new LuaTable();
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            table.set(decodeKey(entry.getCompound("key")), decodeValue(entry.getCompound("value"), depth + 1));
        }
        return table;
    }

    private LuaValue decodeKey(CompoundTag tag) {
        return switch (tag.getString("type")) {
            case "string" -> LuaValue.valueOf(tag.getString("value"));
            case "integer" -> LuaValue.valueOf(tag.getLong("value"));
            default -> throw new IllegalArgumentException("Unknown encoded Lua table key type");
        };
    }

    private static final class Counter {
        private int bytes;

        private void add(int amount) {
            bytes = Math.addExact(bytes, amount);
        }
    }
}
