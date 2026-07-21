package dev.devce.websnodelib.internal;

import dev.devce.websnodelib.api.CounterNode;
import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.FunctionEndNode;
import dev.devce.websnodelib.api.FunctionStartNode;
import dev.devce.websnodelib.api.MuxNode;
import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.PassOnNthRisingEdgeNode;
import dev.devce.websnodelib.internal.nodes.io.BoolToLevelNode;
import dev.devce.websnodelib.internal.nodes.io.DisplayNode;
import dev.devce.websnodelib.internal.nodes.io.LevelToBoolNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.AndNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.EdgeFallNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.EdgeRiseNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.NandNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.NorNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.OrNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.SchmittNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.XnorNode;
import dev.devce.websnodelib.internal.nodes.logic.binary.XorNode;
import dev.devce.websnodelib.internal.nodes.logic.comparison.ApproxNode;
import dev.devce.websnodelib.internal.nodes.logic.comparison.EqualNode;
import dev.devce.websnodelib.internal.nodes.logic.comparison.GreaterEqualNode;
import dev.devce.websnodelib.internal.nodes.logic.comparison.GreaterThanNode;
import dev.devce.websnodelib.internal.nodes.logic.comparison.LessEqualNode;
import dev.devce.websnodelib.internal.nodes.logic.comparison.LessThanNode;
import dev.devce.websnodelib.internal.nodes.logic.memory.DFlipFlopNode;
import dev.devce.websnodelib.internal.nodes.logic.memory.SrLatchNode;
import dev.devce.websnodelib.internal.nodes.logic.unary.NotNode;
import dev.devce.websnodelib.internal.nodes.math.binary.AddNode;
import dev.devce.websnodelib.internal.nodes.math.binary.ClampNode;
import dev.devce.websnodelib.internal.nodes.math.binary.DivideNode;
import dev.devce.websnodelib.internal.nodes.math.binary.LerpNode;
import dev.devce.websnodelib.internal.nodes.math.binary.MapNode;
import dev.devce.websnodelib.internal.nodes.math.binary.MaxNode;
import dev.devce.websnodelib.internal.nodes.math.binary.MinNode;
import dev.devce.websnodelib.internal.nodes.math.binary.ModuloNode;
import dev.devce.websnodelib.internal.nodes.math.binary.MultiplyNode;
import dev.devce.websnodelib.internal.nodes.math.binary.PowerNode;
import dev.devce.websnodelib.internal.nodes.math.binary.SubtractNode;
import dev.devce.websnodelib.internal.nodes.math.trig.Atan2Node;
import dev.devce.websnodelib.internal.nodes.math.trig.CosNode;
import dev.devce.websnodelib.internal.nodes.math.trig.SinNode;
import dev.devce.websnodelib.internal.nodes.math.trig.TanNode;
import dev.devce.websnodelib.internal.nodes.math.unary.AbsNode;
import dev.devce.websnodelib.internal.nodes.math.unary.AverageNode;
import dev.devce.websnodelib.internal.nodes.math.unary.CeilNode;
import dev.devce.websnodelib.internal.nodes.math.unary.ExpNode;
import dev.devce.websnodelib.internal.nodes.math.unary.FloorNode;
import dev.devce.websnodelib.internal.nodes.math.unary.Log10Node;
import dev.devce.websnodelib.internal.nodes.math.unary.LogNode;
import dev.devce.websnodelib.internal.nodes.math.unary.NegateNode;
import dev.devce.websnodelib.internal.nodes.math.unary.QuantizeRedstoneNode;
import dev.devce.websnodelib.internal.nodes.math.unary.RandomNode;
import dev.devce.websnodelib.internal.nodes.math.unary.RoundNode;
import dev.devce.websnodelib.internal.nodes.math.unary.SignNode;
import dev.devce.websnodelib.internal.nodes.math.unary.SqrtNode;
import dev.devce.websnodelib.internal.nodes.sources.ConstantNode;
import dev.devce.websnodelib.internal.nodes.sources.DelayNode;
import dev.devce.websnodelib.internal.nodes.sources.OscillatorNode;
import dev.devce.websnodelib.internal.nodes.sources.PulseNode;
import dev.devce.websnodelib.internal.nodes.sources.SampleHoldNode;
import dev.devce.websnodelib.internal.nodes.sources.TickNode;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;

/** https://github.com/webyep-art/webs_node_lib (MIT, webyep). */
public final class InternalNodes {

    private InternalNodes() {}

    public static void register() {
        MenuCategories.registerAll();

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
                MenuCategories.ORGANIZATION,
                WsId.of("tool_section"),
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
        registerNodeClass("dev.devce.websnodelib.internal.nodes.visuals.Viewport3DNode");
        registerNodeClass("dev.devce.websnodelib.internal.nodes.visuals.RgbPreviewNode");
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
