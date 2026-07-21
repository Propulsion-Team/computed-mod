package dev.propulsionteam.computed.node.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.node.program.ConnectionModel.Waypoint;
import dev.propulsionteam.computed.node.program.NodeModel.PlaceholderStatus;
import dev.propulsionteam.computed.node.program.PortModel.Direction;
import dev.propulsionteam.computed.node.program.ProgramDiagnostic.Severity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ProgramCodecTest {
    private static final UUID GRAPH_ID = uuid(100);
    private static final UUID SOURCE_ID = uuid(101);
    private static final UUID TARGET_ID = uuid(102);
    private static final UUID CONNECTION_ID = uuid(103);

    @Test
    void migratesLegacyIdsStablePinsAndMissingNodesWithoutDiscardingRawData() {
        CompoundTag legacy = legacyBundle();

        ProgramCodec.DecodeResult result =
                ProgramCodec.decode(legacy, type -> type.equals("computed:constant"));
        ComputedProgram program = result.program();

        assertTrue(result.migrated());
        assertEquals(0, result.sourceVersion());
        assertEquals(GRAPH_ID, program.rootGraph().id());
        assertEquals(2, program.rootGraph().nodes().size());

        NodeModel source = program.rootGraph().node(SOURCE_ID).orElseThrow();
        assertEquals("computed:constant", source.typeId());
        assertEquals("websnodelib:constant", source.originalTypeId());
        assertEquals(PlaceholderStatus.RESOLVED, source.placeholderStatus());
        assertEquals(new PortId("value"), source.ports().getFirst().id());
        assertEquals("number", source.ports().getFirst().valueType());

        NodeModel unavailable = program.rootGraph().node(TARGET_ID).orElseThrow();
        assertEquals("addon_that_is_gone:receiver", unavailable.typeId());
        assertEquals(PlaceholderStatus.MISSING_TYPE, unavailable.placeholderStatus());
        assertEquals("preserve me", unavailable.rawTag().getString("privateAddonPayload"));
        assertEquals(17, unavailable.state().getInt("privateCounter"));
        assertEquals(new PortId("payload"), unavailable.ports().getFirst().id());

        ConnectionModel connection = program.rootGraph().connections().getFirst();
        assertEquals(CONNECTION_ID, connection.id());
        assertEquals(new PortId("value"), connection.sourcePort());
        assertEquals(new PortId("payload"), connection.targetPort());
        assertEquals(List.of(new Waypoint(12.0D, -4.0D)), connection.waypoints());
        assertEquals("wire-private", connection.rawTag().getString("addonWireData"));

        assertEquals("root-private", program.metadata()
                .getCompound("legacyRootExtras")
                .getString("unrecognizedRootData"));
        assertEquals(1, program.rootGraph().sections().size());
        assertEquals(96, program.rootGraph().sections().getFirst().width());
        assertTrue(program.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("legacy_type_renamed")));
        assertTrue(program.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("missing_node_type")));

        ProgramCodec.DecodeResult roundTrip =
                ProgramCodec.decode(ProgramCodec.encode(program), type -> type.equals("computed:constant"));
        assertFalse(roundTrip.migrated());
        assertEquals(ComputedProgram.FORMAT_VERSION, roundTrip.sourceVersion());
        NodeModel restoredMissing = roundTrip.program().rootGraph().node(TARGET_ID).orElseThrow();
        assertEquals(PlaceholderStatus.MISSING_TYPE, restoredMissing.placeholderStatus());
        assertEquals("preserve me", restoredMissing.rawTag().getString("privateAddonPayload"));
        assertEquals(new PortId("payload"), restoredMissing.ports().getFirst().id());
        assertEquals(new PortId("value"), roundTrip.program()
                .rootGraph()
                .connections()
                .getFirst()
                .sourcePort());
    }

    @Test
    void v2RoundTripPreservesVersionedModelFunctionsStateAndStablePortKeys() {
        CompoundTag properties = new CompoundTag();
        properties.putString("mode", "latched");
        CompoundTag state = new CompoundTag();
        state.putLong("lastTick", 90210L);
        CompoundTag portData = new CompoundTag();
        portData.putDouble("default", 2.5D);
        NodeModel source = new NodeModel(
                SOURCE_ID,
                "computed:source",
                "computed:source",
                "Source",
                -20,
                40,
                properties,
                state,
                List.of(new PortModel(new PortId("signal.current"), Direction.OUTPUT, "number", "Signal", portData)),
                PlaceholderStatus.RESOLVED,
                tagged("nodeRaw", "source-private"));
        NodeModel target = new NodeModel(
                TARGET_ID,
                "computed:sink",
                "computed:sink",
                "Sink",
                80,
                40,
                new CompoundTag(),
                new CompoundTag(),
                List.of(new PortModel(new PortId("signal.input"), Direction.INPUT, "number", "Signal", new CompoundTag())),
                PlaceholderStatus.RESOLVED,
                new CompoundTag());
        ConnectionModel connection = new ConnectionModel(
                CONNECTION_ID,
                SOURCE_ID,
                new PortId("signal.current"),
                TARGET_ID,
                new PortId("signal.input"),
                List.of(new Waypoint(1.25D, 2.5D), new Waypoint(4.0D, 8.0D)),
                tagged("wireRaw", "curve-a"));
        SectionModel section = new SectionModel(
                uuid(104), "Control", -40, 10, 180, 90, 0x44332211, 3, tagged("sectionRaw", "locked"));
        GraphModel graph = new GraphModel(
                GRAPH_ID,
                List.of(target, source),
                List.of(connection),
                List.of(section),
                tagged("graphMeta", "root"),
                tagged("graphRaw", "raw-root"));
        GraphModel functionGraph = new GraphModel(uuid(105), List.of(source), List.of(), List.of());
        FunctionModel function = new FunctionModel(
                uuid(106),
                "Normalize",
                functionGraph,
                tagged("functionMeta", "library"),
                tagged("functionRaw", "keep"));
        ProgramDiagnostic diagnostic = new ProgramDiagnostic(
                Severity.WARNING,
                "fixture_warning",
                "fixture",
                GRAPH_ID,
                SOURCE_ID,
                null,
                tagged("detail", "attached"));
        ComputedProgram original = new ComputedProgram(
                44L, graph, List.of(function), List.of(diagnostic), tagged("programMeta", "v2"));

        CompoundTag encoded = ProgramCodec.write(original);
        ProgramCodec.DecodeResult result = ProgramCodec.decode(
                encoded, Set.of("computed:source", "computed:sink")::contains);
        ComputedProgram restored = result.program();

        assertFalse(result.migrated());
        assertEquals(2, encoded.getInt("formatVersion"));
        assertEquals(44L, restored.revision());
        assertEquals(GRAPH_ID, restored.rootGraph().id());
        assertEquals(List.of(TARGET_ID, SOURCE_ID), restored.rootGraph().nodes().stream()
                .map(NodeModel::id)
                .toList());
        NodeModel restoredSource = restored.rootGraph().node(SOURCE_ID).orElseThrow();
        assertEquals("latched", restoredSource.properties().getString("mode"));
        assertEquals(90210L, restoredSource.state().getLong("lastTick"));
        assertEquals("signal.current", restoredSource.ports().getFirst().id().value());
        assertEquals(2.5D, restoredSource.ports().getFirst().data().getDouble("default"));
        assertEquals(connection.waypoints(), restored.rootGraph().connections().getFirst().waypoints());
        assertEquals("locked", restored.rootGraph().sections().getFirst().rawTag().getString("sectionRaw"));
        assertEquals("Normalize", restored.functions().getFirst().name());
        assertEquals(uuid(105), restored.functions().getFirst().graph().id());
        assertEquals("library", restored.functions().getFirst().metadata().getString("functionMeta"));
        assertEquals("fixture_warning", restored.diagnostics().getFirst().code());
        assertEquals("attached", restored.diagnostics().getFirst().details().getString("detail"));
        assertEquals("v2", restored.metadata().getString("programMeta"));
    }

    @Test
    void migratesLegacyFunctionBodiesAndPreservesFunctionSpecificData() {
        UUID functionId = uuid(200);
        UUID functionGraphId = uuid(201);
        UUID functionNodeId = uuid(202);

        CompoundTag functionNode = legacyNode(functionNodeId, "websnodelib:constant", "Function source");
        ListTag outputs = new ListTag();
        outputs.add(legacyPort("result", "number", "Result"));
        functionNode.put("outputs", outputs);
        ListTag functionNodes = new ListTag();
        functionNodes.add(functionNode);

        CompoundTag functionBody = new CompoundTag();
        functionBody.putUUID("id", functionGraphId);
        functionBody.put("nodes", functionNodes);
        functionBody.put("conns", new ListTag());
        functionBody.put("sections", new ListTag());

        CompoundTag legacyFunction = new CompoundTag();
        legacyFunction.putUUID("Id", functionId);
        legacyFunction.putString("Name", "Legacy normalize");
        legacyFunction.put("Body", functionBody);
        legacyFunction.putString("addonFunctionPayload", "keep-function-data");
        ListTag functions = new ListTag();
        functions.add(legacyFunction);

        CompoundTag legacy = legacyBundle();
        legacy.put("ComputerFunctions", functions);
        ComputedProgram program = ProgramCodec.decode(
                        legacy, type -> type.equals("computed:constant"))
                .program();

        assertEquals(1, program.functions().size());
        FunctionModel function = program.functions().getFirst();
        assertEquals(functionId, function.id());
        assertEquals("Legacy normalize", function.name());
        assertEquals(functionGraphId, function.graph().id());
        assertEquals("computed:constant", function.graph().nodes().getFirst().typeId());
        assertEquals("websnodelib:constant", function.graph().nodes().getFirst().originalTypeId());
        assertEquals("keep-function-data", function.metadata().getString("addonFunctionPayload"));

        CompoundTag bridged = ProgramCodec.toLegacyBundleTag(program);
        CompoundTag bridgedFunction = bridged.getList("ComputerFunctions", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(functionId, bridgedFunction.getUUID("Id"));
        assertEquals("Legacy normalize", bridgedFunction.getString("Name"));
        assertEquals("keep-function-data", bridgedFunction.getString("addonFunctionPayload"));
        assertEquals("computed:constant", bridgedFunction
                .getCompound("Body")
                .getList("nodes", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getString("typeId"));
    }

    private static CompoundTag legacyBundle() {
        CompoundTag source = legacyNode(SOURCE_ID, "websnodelib:constant", "Source");
        ListTag sourceOutputs = new ListTag();
        sourceOutputs.add(legacyPort("value", "number", "Value"));
        source.put("outputs", sourceOutputs);

        CompoundTag target = legacyNode(TARGET_ID, "addon_that_is_gone:receiver", "Missing receiver");
        target.putString("privateAddonPayload", "preserve me");
        target.putInt("privateCounter", 17);
        ListTag targetInputs = new ListTag();
        targetInputs.add(legacyPort("payload", "number", "Payload"));
        target.put("inputs", targetInputs);

        ListTag nodes = new ListTag();
        nodes.add(source);
        nodes.add(target);

        CompoundTag wire = new CompoundTag();
        wire.putUUID("id", CONNECTION_ID);
        wire.putUUID("src", SOURCE_ID);
        wire.putInt("srcP", 0);
        wire.putString("sourcePort", "value");
        wire.putUUID("tgt", TARGET_ID);
        wire.putInt("tgtP", 0);
        wire.putString("targetPort", "payload");
        wire.putString("addonWireData", "wire-private");
        CompoundTag waypoint = new CompoundTag();
        waypoint.putDouble("x", 12.0D);
        waypoint.putDouble("y", -4.0D);
        ListTag waypoints = new ListTag();
        waypoints.add(waypoint);
        wire.put("wps", waypoints);
        ListTag wires = new ListTag();
        wires.add(wire);

        CompoundTag section = new CompoundTag();
        section.putUUID("id", uuid(107));
        section.putString("name", "Legacy section");
        section.putInt("x", -10);
        section.putInt("y", -20);
        section.putInt("w", 96);
        section.putInt("h", 64);
        ListTag sections = new ListTag();
        sections.add(section);

        CompoundTag graph = new CompoundTag();
        graph.putUUID("id", GRAPH_ID);
        graph.put("nodes", nodes);
        graph.put("conns", wires);
        graph.put("sections", sections);

        CompoundTag bundle = new CompoundTag();
        bundle.put("ComputerGraph", graph);
        bundle.put("ComputerFunctions", new ListTag());
        bundle.putString("unrecognizedRootData", "root-private");
        return bundle;
    }

    private static CompoundTag legacyNode(UUID id, String type, String title) {
        CompoundTag node = new CompoundTag();
        node.putUUID("id", id);
        node.putString("typeId", type);
        node.putString("title", title);
        node.putInt("x", 10);
        node.putInt("y", 20);
        node.put("inputs", new ListTag());
        node.put("outputs", new ListTag());
        return node;
    }

    private static CompoundTag legacyPort(String key, String valueType, String label) {
        CompoundTag port = new CompoundTag();
        port.putString("portKey", key);
        port.putString("dataType", valueType);
        port.putString("name", label);
        return port;
    }

    private static CompoundTag tagged(String key, String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        return tag;
    }

    private static UUID uuid(long suffix) {
        return new UUID(0L, suffix);
    }
}
