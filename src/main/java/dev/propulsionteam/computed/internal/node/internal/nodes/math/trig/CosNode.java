package dev.propulsionteam.computed.internal.node.internal.nodes.math.trig;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.UnaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CosNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_cos");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Cos");

    public CosNode(int x, int y) { super(TYPE_ID, "Cos", x, y, Math::cos); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, CosNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
