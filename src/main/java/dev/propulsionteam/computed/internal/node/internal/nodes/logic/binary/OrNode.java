package dev.devce.websnodelib.internal.nodes.logic.binary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OrNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = WsId.of("logic_or");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("OR");

    public OrNode(int x, int y) { super(TYPE_ID, "OR", x, y, (a, b) -> a || b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, OrNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
