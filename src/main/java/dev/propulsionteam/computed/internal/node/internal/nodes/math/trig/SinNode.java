package dev.propulsionteam.computed.internal.node.internal.nodes.math.trig;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.unary.UnaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SinNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_sin");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Sin");

    public SinNode(int x, int y) { super(TYPE_ID, "Sin", x, y, Math::sin); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, SinNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
