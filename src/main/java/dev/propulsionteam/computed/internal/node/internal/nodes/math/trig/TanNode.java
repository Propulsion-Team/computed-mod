package dev.devce.websnodelib.internal.nodes.math.trig;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import dev.devce.websnodelib.internal.nodes.math.unary.UnaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TanNode extends UnaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_tan");
    public static final ResourceLocation MENU = MenuCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Tan");

    public TanNode(int x, int y) { super(TYPE_ID, "Tan", x, y, Math::tan); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, TanNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
