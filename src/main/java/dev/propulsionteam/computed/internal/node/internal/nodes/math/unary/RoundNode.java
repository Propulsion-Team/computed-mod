package dev.propulsionteam.computed.internal.node.internal.nodes.math.unary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RoundNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_round");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Round");

    public RoundNode(int x, int y) { super(TYPE_ID, "Round", x, y, Math::rint); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, RoundNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
