package dev.propulsionteam.computed.lua.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

class LuaStateCodecTest {
    private final LuaStateCodec codec = new LuaStateCodec();

    @Test
    void roundTripsSupportedValuesAndTableKeys() {
        LuaTable nested = new LuaTable();
        nested.set("enabled", LuaValue.TRUE);
        LuaTable root = new LuaTable();
        root.set("name", "counter");
        root.set(1, LuaValue.valueOf(4.5));
        root.set(-2, nested);

        LuaTable restored = codec.decode(codec.encode(root)).checktable();

        assertEquals("counter", restored.get("name").tojstring());
        assertEquals(4.5, restored.get(1).todouble());
        assertTrue(restored.get(-2).get("enabled").toboolean());
    }

    @Test
    void rejectsCyclesUnsupportedKeysNonFiniteNumbersAndExcessDepth() {
        LuaTable cyclic = new LuaTable();
        cyclic.set("self", cyclic);
        LuaTable unsupportedKey = new LuaTable();
        unsupportedKey.set(new LuaTable(), LuaValue.TRUE);

        assertThrows(IllegalArgumentException.class, () -> codec.encode(cyclic));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(unsupportedKey));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(LuaValue.valueOf(Double.NaN)));

        LuaTable root = new LuaTable();
        LuaTable current = root;
        for (int index = 0; index < LuaStateCodec.MAX_DEPTH + 1; index++) {
            LuaTable child = new LuaTable();
            current.set("next", child);
            current = child;
        }
        assertThrows(IllegalArgumentException.class, () -> codec.encode(root));
    }
}
