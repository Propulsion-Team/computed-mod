package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MinNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_min");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Min");

    public MinNode(int x, int y) { super(TYPE_ID, "Min", x, y, Math::min); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, MinNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
