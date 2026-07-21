package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MultiplyNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_multiply");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Multiply");

    public MultiplyNode(int x, int y) { super(TYPE_ID, "Multiply", x, y, (a, b) -> a * b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, MultiplyNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
