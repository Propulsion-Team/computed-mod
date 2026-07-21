package dev.propulsionteam.computed.internal.node.internal.nodes.math.trig;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.UnaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TanNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_tan");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Tan");

    public TanNode(int x, int y) { super(TYPE_ID, "Tan", x, y, Math::tan); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, TanNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
