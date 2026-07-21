package dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LessEqualNode extends ComparisonNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("cmp_le");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_COMPARISON;
    public static final Component LABEL = Component.literal("<=");

    public LessEqualNode(int x, int y) { super(TYPE_ID, "<=", "A <= B", x, y, (a, b) -> a <= b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, LessEqualNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
