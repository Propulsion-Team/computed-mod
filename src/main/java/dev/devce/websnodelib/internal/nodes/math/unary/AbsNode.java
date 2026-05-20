package dev.devce.websnodelib.internal.nodes.math.unary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AbsNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_abs");
    public static final ResourceLocation MENU = MenuCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Abs");

    public AbsNode(int x, int y) { super(TYPE_ID, "Abs", x, y, Math::abs); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, AbsNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
