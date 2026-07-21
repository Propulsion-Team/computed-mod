package dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class XnorNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("logic_xnor");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("XNOR");

    public XnorNode(int x, int y) { super(TYPE_ID, "XNOR", x, y, (a, b) -> !(a ^ b)); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, XnorNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
