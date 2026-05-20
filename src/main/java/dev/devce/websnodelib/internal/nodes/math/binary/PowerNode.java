package dev.devce.websnodelib.internal.nodes.math.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PowerNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_pow");
    public static final ResourceLocation MENU = MenuCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Power");

    public PowerNode(int x, int y) { super(TYPE_ID, "Power", x, y, Math::pow); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, PowerNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
