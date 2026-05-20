package dev.devce.websnodelib.internal.nodes.logic.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class XorNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = WsId.of("logic_xor");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("XOR");

    public XorNode(int x, int y) { super(TYPE_ID, "XOR", x, y, (a, b) -> a ^ b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, XorNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
