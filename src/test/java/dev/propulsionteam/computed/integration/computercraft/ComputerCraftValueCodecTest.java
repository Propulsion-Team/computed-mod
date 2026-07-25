package dev.propulsionteam.computed.integration.computercraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dan200.computercraft.api.lua.LuaException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

class ComputerCraftValueCodecTest {
    @Test
    void convertsSupportedValuesWithoutExposingJavaObjects() throws LuaException {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("enabled", true);
        source.put(2, List.of("a", 4.5));

        LuaValue encoded = ComputerCraftValueCodec.toLua(source);
        Object decoded = ComputerCraftValueCodec.toJava(encoded);

        assertTrue(encoded.istable());
        assertEquals(
                Map.of("enabled", true, 2, Map.of(1, "a", 2, 4.5)),
                decoded);
    }

    @Test
    void rejectsCyclesAndUnsupportedObjects() {
        Map<Object, Object> cycle = new LinkedHashMap<>();
        cycle.put("self", cycle);

        assertThrows(LuaException.class, () -> ComputerCraftValueCodec.toLua(cycle));
        assertThrows(LuaException.class, () -> ComputerCraftValueCodec.toLua(new Object()));
    }

    @Test
    void rejectsCyclicLuaTables() {
        LuaTable cycle = new LuaTable();
        cycle.set("self", cycle);

        assertThrows(LuaException.class, () -> ComputerCraftValueCodec.toJava(cycle));
    }
}
