package dev.propulsionteam.computed.internal.node.internal.nodes.math.unary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CeilNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_ceil");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Ceil");

    public CeilNode(int x, int y) { super(TYPE_ID, "Ceil", x, y, Math::ceil); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, CeilNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
