package dev.devce.websnodelib.internal.nodes.math.trig;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import dev.devce.websnodelib.internal.nodes.math.binary.BinaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Atan2Node extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = WsId.of("math_atan2");
    public static final ResourceLocation MENU = MenuCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Atan2");

    public Atan2Node(int x, int y) { super(TYPE_ID, "Atan2", x, y, Math::atan2); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, Atan2Node::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
