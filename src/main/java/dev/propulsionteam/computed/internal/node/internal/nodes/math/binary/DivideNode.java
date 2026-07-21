package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DivideNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_divide");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Divide");

    public DivideNode(int x, int y) { super(TYPE_ID, "Divide", x, y, (a, b) -> b != 0 ? a / b : 0); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, DivideNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
