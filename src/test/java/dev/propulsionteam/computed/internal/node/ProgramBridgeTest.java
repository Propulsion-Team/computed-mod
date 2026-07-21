package dev.propulsionteam.computed.internal.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.node.program.ComputedProgram;
import dev.propulsionteam.computed.node.program.ConnectionModel;
import dev.propulsionteam.computed.node.program.FunctionModel;
import dev.propulsionteam.computed.node.program.GraphModel;
import dev.propulsionteam.computed.node.program.NodeModel;
import dev.propulsionteam.computed.node.program.NodeModel.PlaceholderStatus;
import dev.propulsionteam.computed.node.program.PortId;
import dev.propulsionteam.computed.node.program.PortModel;
import dev.propulsionteam.computed.node.program.PortModel.Direction;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ProgramBridgeTest {
    @Test
    void editorStructureKeepsServerAuthoritativeRuntimeState() {
        UUID nodeId = new UUID(0L, 1L);
        CompoundTag liveState = new CompoundTag();
        liveState.putInt("count", 41);
        CompoundTag staleEditorState = new CompoundTag();
        staleEditorState.putInt("count", 3);
        CompoundTag editedInnerGraph = new CompoundTag();
        editedInnerGraph.putInt("editMarker", 7);
        staleEditorState.put("innerGraph", editedInnerGraph);

        ComputedProgram live = program(node(nodeId, "computed:counter", 10, liveState));
        ComputedProgram incoming = program(node(nodeId, "computed:counter", 99, staleEditorState));

        ComputedProgram merged = ProgramBridge.preserveRuntimeState(incoming, live);
        NodeModel result = merged.rootGraph().nodes().getFirst();

        assertEquals(99, result.x(), "editor-owned position must be retained");
        assertEquals(41, result.state().getInt("count"), "live state must not rewind to the editor snapshot");
        assertEquals(7, result.state().getCompound("innerGraph").getInt("editMarker"));
    }

    @Test
    void replacedNodeTypeKeepsItsIncomingDefaultState() {
        UUID nodeId = new UUID(0L, 2L);
        CompoundTag liveState = new CompoundTag();
        liveState.putInt("count", 41);
        CompoundTag replacementState = new CompoundTag();
        replacementState.putInt("value", 8);

        ComputedProgram live = program(node(nodeId, "computed:counter", 0, liveState));
        ComputedProgram incoming = program(node(nodeId, "addon:replacement", 0, replacementState));

        NodeModel result = ProgramBridge.preserveRuntimeState(incoming, live).rootGraph().nodes().getFirst();
        assertEquals(8, result.state().getInt("value"));
    }

    @Test
    void analyzesRootAndEveryFunctionGraphIndependently() {
        NodeModel rootNode = wiredNode(new UUID(0L, 10L));
        GraphModel root = new GraphModel(new UUID(0L, 100L), List.of(rootNode), List.of(), List.of());

        NodeModel first = wiredNode(new UUID(0L, 11L));
        NodeModel second = wiredNode(new UUID(0L, 12L));
        GraphModel cyclicFunction = new GraphModel(
                new UUID(0L, 101L),
                List.of(first, second),
                List.of(connection(20L, first, second), connection(21L, second, first)),
                List.of());
        FunctionModel function = new FunctionModel(
                new UUID(0L, 200L), "Cycle", cyclicFunction, new CompoundTag(), new CompoundTag());

        List<ProgramBridge.AnalyzedGraph> analyses = ProgramBridge.analyzeAll(
                new ComputedProgram(0L, root, List.of(function), List.of(), new CompoundTag()));

        assertEquals(List.of(root.id(), cyclicFunction.id()), analyses.stream()
                .map(ProgramBridge.AnalyzedGraph::graphId)
                .toList());
        assertEquals(0, analyses.getFirst().analysis().combinationalCycles().size());
        assertEquals(1, analyses.get(1).analysis().combinationalCycles().size());
        assertEquals(java.util.Set.of(first.id(), second.id()), analyses.get(1).analysis().disabledNodes());
    }

    private static ComputedProgram program(NodeModel node) {
        return new ComputedProgram(new GraphModel(new UUID(0L, 100L), List.of(node), List.of(), List.of()), List.of());
    }

    private static NodeModel node(UUID id, String type, int x, CompoundTag state) {
        return new NodeModel(
                id,
                type,
                type,
                type,
                x,
                0,
                new CompoundTag(),
                state,
                List.of(),
                PlaceholderStatus.RESOLVED,
                new CompoundTag());
    }

    private static NodeModel wiredNode(UUID id) {
        return new NodeModel(
                id,
                "computed:pure",
                "computed:pure",
                "Pure",
                0,
                0,
                new CompoundTag(),
                new CompoundTag(),
                List.of(
                        new PortModel(new PortId("input"), Direction.INPUT, "number", "Input", new CompoundTag()),
                        new PortModel(new PortId("output"), Direction.OUTPUT, "number", "Output", new CompoundTag())),
                PlaceholderStatus.RESOLVED,
                new CompoundTag());
    }

    private static ConnectionModel connection(long id, NodeModel source, NodeModel target) {
        return new ConnectionModel(
                new UUID(0L, id),
                source.id(),
                new PortId("output"),
                target.id(),
                new PortId("input"),
                List.of(),
                new CompoundTag());
    }
}
