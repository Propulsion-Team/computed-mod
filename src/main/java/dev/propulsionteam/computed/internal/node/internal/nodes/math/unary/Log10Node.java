package dev.propulsionteam.computed.internal.node.internal.nodes.math.unary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Log10Node extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_log10");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Log10");

    public Log10Node(int x, int y) { super(TYPE_ID, "Log10", x, y, a -> a > 0 ? Math.log10(a) : 0); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, Log10Node::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
