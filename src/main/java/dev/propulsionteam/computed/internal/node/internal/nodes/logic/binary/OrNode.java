package dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OrNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("logic_or");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("OR");

    public OrNode(int x, int y) { super(TYPE_ID, "OR", x, y, (a, b) -> a || b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, OrNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
