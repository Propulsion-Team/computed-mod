package dev.devce.websnodelib.internal.nodes.math.trig;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import dev.devce.websnodelib.internal.nodes.math.unary.UnaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CosNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_cos");
    public static final ResourceLocation MENU = MenuCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Cos");

    public CosNode(int x, int y) { super(TYPE_ID, "Cos", x, y, Math::cos); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, CosNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
