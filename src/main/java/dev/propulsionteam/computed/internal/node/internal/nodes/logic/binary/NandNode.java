package dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NandNode extends BinaryLogicNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("logic_nand");
    public static final ResourceLocation MENU = BuiltinNodeCategories.LOGIC_BINARY;
    public static final Component LABEL = Component.literal("NAND");

    public NandNode(int x, int y) { super(TYPE_ID, "NAND", x, y, (a, b) -> !(a && b)); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, NandNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
