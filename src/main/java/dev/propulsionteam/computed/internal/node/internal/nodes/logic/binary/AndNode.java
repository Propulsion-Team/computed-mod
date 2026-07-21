package dev.devce.websnodelib.internal.nodes.logic.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AndNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = WsId.of("logic_and");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("AND");

    public AndNode(int x, int y) { super(TYPE_ID, "AND", x, y, (a, b) -> a && b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, AndNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
