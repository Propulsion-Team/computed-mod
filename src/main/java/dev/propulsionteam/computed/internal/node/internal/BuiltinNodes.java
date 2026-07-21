package dev.propulsionteam.computed.internal.node.internal;

import dev.propulsionteam.computed.internal.node.api.CounterNode;
import dev.propulsionteam.computed.internal.node.api.FunctionCardNode;
import dev.propulsionteam.computed.internal.node.api.FunctionEndNode;
import dev.propulsionteam.computed.internal.node.api.FunctionStartNode;
import dev.propulsionteam.computed.internal.node.api.MuxNode;
import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.PassOnNthRisingEdgeNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.io.BoolToLevelNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.io.DisplayNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.io.LevelToBoolNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.AndNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.EdgeFallNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.EdgeRiseNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.NandNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.NorNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.OrNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.SchmittNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.XnorNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary.XorNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison.ApproxNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison.EqualNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison.GreaterEqualNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison.GreaterThanNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison.LessEqualNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison.LessThanNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.memory.DFlipFlopNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.memory.SrLatchNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.logic.unary.NotNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.AddNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.ClampNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.DivideNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.LerpNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.MapNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.MaxNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.MinNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.ModuloNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.MultiplyNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.PowerNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.SubtractNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.trig.Atan2Node;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.trig.CosNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.trig.SinNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.trig.TanNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.AbsNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.AverageNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.CeilNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.ExpNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.FloorNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.Log10Node;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.LogNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.NegateNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.QuantizeRedstoneNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.RandomNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.RoundNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.SignNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.SqrtNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.sources.ConstantNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.sources.DelayNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.sources.OscillatorNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.sources.PulseNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.sources.SampleHoldNode;
import dev.propulsionteam.computed.internal.node.internal.nodes.sources.TickNode;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;

/** https://github.com/webyep-art/webs_node_lib (MIT, webyep). */
public final class BuiltinNodes {

    private BuiltinNodes() {}

    public static void register() {
        BuiltinNodeCategories.registerAll();

        // math/binary
        AddNode.register();
        SubtractNode.register();
        MultiplyNode.register();
        DivideNode.register();
        ModuloNode.register();
        MinNode.register();
        MaxNode.register();
        PowerNode.register();

        // math/unary
        AbsNode.register();
        SqrtNode.register();
        FloorNode.register();
        CeilNode.register();
        RoundNode.register();
        NegateNode.register();
        LogNode.register();
        Log10Node.register();
        ExpNode.register();
        SignNode.register();
        RandomNode.register();

        // math/trig
        SinNode.register();
        CosNode.register();
        TanNode.register();
        Atan2Node.register();

        // sources (first batch — preserves original menu order)
        ConstantNode.register();
        TickNode.register();
        PulseNode.register();
        OscillatorNode.register();
        CounterNode.register();
        PassOnNthRisingEdgeNode.register();

        // i/o
        DisplayNode.register();

        // visuals (client-only editor nodes; keep out of dedicated-server registry)
        registerClientVisualNodes();

        // organization (tool_section is editor-only — menu entry without NodeRegistry)
        NodeMenuRegistry.addNodeEntry(
                BuiltinNodeCategories.ORGANIZATION,
                BuiltinNodeIds.of("tool_section"),
                Component.literal("Section"));

        // logic/unary
        NotNode.register();

        // logic/binary
        AndNode.register();
        OrNode.register();
        XorNode.register();
        NandNode.register();
        NorNode.register();
        XnorNode.register();

        // logic/comparison
        EqualNode.register();
        GreaterThanNode.register();
        LessThanNode.register();
        GreaterEqualNode.register();
        LessEqualNode.register();
        ApproxNode.register();

        // logic/binary (extended)
        EdgeRiseNode.register();
        EdgeFallNode.register();
        SchmittNode.register();
        MuxNode.register();

        // logic/memory
        SrLatchNode.register();
        DFlipFlopNode.register();

        // math (late additions — original menu order)
        ClampNode.register();
        MapNode.register();
        LerpNode.register();
        AverageNode.register();
        QuantizeRedstoneNode.register();

        // sources (late additions)
        DelayNode.register();
        SampleHoldNode.register();

        // i/o (late additions)
        BoolToLevelNode.register();
        LevelToBoolNode.register();

        // hidden — function nodes are placed from the schematic picker, not the add menu
        FunctionStartNode.register();
        FunctionEndNode.register();
        FunctionCardNode.register();
    }

    private static void registerClientVisualNodes() {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return;
        }
        registerNodeClass("dev.propulsionteam.computed.internal.node.internal.nodes.visuals.Viewport3DNode");
        registerNodeClass("dev.propulsionteam.computed.internal.node.internal.nodes.visuals.RgbPreviewNode");
    }

    private static void registerNodeClass(String className) {
        try {
            Class<?> nodeClass = Class.forName(className);
            nodeClass.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register node class: " + className, e);
        }
    }
}
