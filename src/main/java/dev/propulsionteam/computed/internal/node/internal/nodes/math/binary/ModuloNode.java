package dev.propulsionteam.computed.internal.node.internal.nodes.math.binary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ModuloNode extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_mod");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_BINARY;
    public static final Component LABEL = Component.literal("Modulo");

    public ModuloNode(int x, int y) { super(TYPE_ID, "Modulo", x, y, (a, b) -> b != 0 ? a % b : 0); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, ModuloNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
