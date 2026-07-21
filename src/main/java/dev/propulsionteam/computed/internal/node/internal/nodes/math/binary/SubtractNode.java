package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SubtractNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_subtract");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Subtract");

    public SubtractNode(int x, int y) { super(TYPE_ID, "Subtract", x, y, (a, b) -> a - b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, SubtractNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
