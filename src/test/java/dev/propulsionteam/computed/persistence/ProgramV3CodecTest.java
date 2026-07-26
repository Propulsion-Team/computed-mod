package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

class ProgramV3CodecTest {
    @Test
    void roundTripsFormatThreeGraphLibraryPortSnapshotsAndState() {
        UUID graphId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        LuaDefinitionSource definition =
                LuaDefinitionSource.embedded(1, "example:source", source("example:source"));
        GraphNode source = new GraphNode(
                sourceId,
                definition.id(),
                definition.hash(),
                -40,
                12,
                List.of(new PortSnapshot("value", PortDirection.OUTPUT, ConnectionType.NUMBER, "Value")),
                Map.of());
        GraphNode target = new GraphNode(
                targetId,
                "computed:display",
                "bundled-hash",
                80,
                12,
                List.of(new PortSnapshot("value", PortDirection.INPUT, ConnectionType.NUMBER, "Value")),
                Map.of());
        GraphConnection connection = new GraphConnection(
                UUID.randomUUID(),
                sourceId,
                "value",
                targetId,
                "value",
                List.of(new GraphPoint(2.5, 9.0)));
        LuaTable stateTable = new LuaTable();
        stateTable.set("count", LuaValue.valueOf(7));
        CompoundTag state = new LuaStateCodec().encode(stateTable);
        ComputedProgramV3 original = new ComputedProgramV3(
                14,
                new ComputedGraph(graphId, List.of(source, target), List.of(connection)),
                Map.of(definition.id(), definition),
                Map.of(sourceId, state),
                tagged("name", "fixture"));

        CompoundTag encoded = ProgramV3Codec.encode(original);
        ProgramV3Codec.LoadResult result = ProgramV3Codec.decode(encoded, "1,2,3", null);

        assertFalse(result.discardedLegacy());
        assertEquals(3, encoded.getInt("formatVersion"));
        assertEquals(14, result.program().revision());
        assertEquals(graphId, result.program().rootGraph().id());
        assertEquals(definition.hash(), result.program().library().get(definition.id()).hash());
        assertEquals("value", result.program().rootGraph().nodes().getFirst().ports().getFirst().id());
        assertEquals(
                7,
                new LuaStateCodec()
                        .decode(result.program().persistentState().get(sourceId))
                        .get("count")
                        .toint());
        assertEquals(List.of(new GraphPoint(2.5, 9.0)), result.program().rootGraph().connections().getFirst().waypoints());
        assertEquals("fixture", result.program().metadata().getString("name"));
    }

    @Test
    void discardsLegacyProgramsWithoutBackupAndReportsPosition() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("formatVersion", 2);
        legacy.putString("legacyPayload", "discard me");
        List<String> warnings = new ArrayList<>();

        ProgramV3Codec.LoadResult result =
                ProgramV3Codec.decode(legacy, "BlockPos{x=4,y=70,z=-8}", warnings::add);

        assertTrue(result.discardedLegacy());
        assertEquals(2, result.sourceVersion());
        assertTrue(result.program().rootGraph().nodes().isEmpty());
        assertTrue(result.program().library().isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("BlockPos{x=4,y=70,z=-8}"));
        assertFalse(ProgramV3Codec.encode(result.program()).contains("legacyPayload"));
    }

    @Test
    void rejectsFutureFormatsHashTamperingAndLegacyClipboardPayloads() {
        CompoundTag future = new CompoundTag();
        future.putInt("formatVersion", 4);

        assertThrows(IllegalArgumentException.class, () -> ProgramV3Codec.decode(future, "origin", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LuaDefinitionSource(1, "example:bad", source("example:bad"), "wrong", null));
        assertThrows(IllegalArgumentException.class, () -> LuaDefinitionClipboard.importSource("CMP2payload"));
    }

    private static String source(String id) {
        return "local node = computed.node(1, \""
                + id
                + "\", \"Fixture\")\n"
                + "node:on_run(function(ctx) end)\n"
                + "return node\n";
    }

    private static CompoundTag tagged(String key, String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        return tag;
    }
}
