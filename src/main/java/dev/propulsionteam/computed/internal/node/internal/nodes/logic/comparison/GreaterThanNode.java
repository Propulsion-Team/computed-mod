package dev.devce.websnodelib.internal.nodes.logic.comparison;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class GreaterThanNode extends ComparisonNode {
    public static final ResourceLocation TYPE_ID = WsId.of("cmp_gt");
    public static final ResourceLocation MENU = MenuCategories.LOGIC_COMPARISON;
    public static final Component LABEL = Component.literal(">");

    public GreaterThanNode(int x, int y) { super(TYPE_ID, ">", "A > B", x, y, (a, b) -> a > b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, GreaterThanNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
