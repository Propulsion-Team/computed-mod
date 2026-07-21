package dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class XorNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("logic_xor");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("XOR");

    public XorNode(int x, int y) { super(TYPE_ID, "XOR", x, y, (a, b) -> a ^ b); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, XorNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
