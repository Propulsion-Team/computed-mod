package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ComputedProgramClipboardTest {
    @Test
    void copiesSelectedSubgraphAndPastesItsTopLeftAtTheCursor() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID omittedId = UUID.randomUUID();
        LuaDefinitionSource definition = LuaDefinitionSource.embedded(
                1,
                "example:clipboard",
                "local node = computed.node(1, \"example:clipboard\", \"Clipboard\")\nreturn node\n");
        GraphNode first = node(firstId, definition, 10, 20);
        GraphNode second = node(secondId, definition, 70, 80);
        GraphNode omitted = node(omittedId, definition, 200, 200);
        GraphConnection internal = new GraphConnection(
                UUID.randomUUID(),
                firstId,
                "out",
                secondId,
                "in",
                List.of(new GraphPoint(30, 40)));
        GraphConnection external = new GraphConnection(
                UUID.randomUUID(),
                secondId,
                "out",
                omittedId,
                "in",
                List.of());
        ComputedProgramV3 source = new ComputedProgramV3(
                1,
                new ComputedGraph(
                        UUID.randomUUID(),
                        List.of(first, second, omitted),
                        List.of(internal, external)),
                Map.of(definition.id(), definition),
                Map.of(firstId, new CompoundTag()),
                new CompoundTag());

        ComputedProgramV3 fragment =
                ComputedProgramPackage.copySelection(source, Set.of(firstId, secondId));
        ComputedProgramPackage.PasteResult pasted = ComputedProgramPackage.pasteSelection(
                ComputedProgramV3.empty(UUID.randomUUID()),
                fragment,
                300,
                400);

        assertEquals(2, fragment.rootGraph().nodes().size());
        assertEquals(1, fragment.rootGraph().connections().size());
        assertEquals(2, pasted.program().rootGraph().nodes().size());
        assertEquals(1, pasted.program().rootGraph().connections().size());
        assertEquals(300, pasted.program().rootGraph().nodes().stream().mapToInt(GraphNode::x).min().orElseThrow());
        assertEquals(400, pasted.program().rootGraph().nodes().stream().mapToInt(GraphNode::y).min().orElseThrow());
        assertTrue(pasted.program().rootGraph().connections().getFirst().waypoints()
                .contains(new GraphPoint(320, 420)));
        assertEquals(pasted.pastedNodeIds(), pasted.program().rootGraph().nodes().stream()
                .map(GraphNode::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertNotEquals(Set.of(firstId, secondId), pasted.pastedNodeIds());
        assertTrue(pasted.program().library().containsKey(definition.id()));
        assertEquals(1, pasted.program().persistentState().size());
    }

    private static GraphNode node(
            UUID id,
            LuaDefinitionSource definition,
            int x,
            int y) {
        return new GraphNode(
                id,
                definition.id(),
                definition.hash(),
                x,
                y,
                List.of(),
                Map.of());
    }
}
