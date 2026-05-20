package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MaxNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_max");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Max");

    public MaxNode(int x, int y) { super(TYPE_ID, "Max", x, y, Math::max); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, MaxNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
