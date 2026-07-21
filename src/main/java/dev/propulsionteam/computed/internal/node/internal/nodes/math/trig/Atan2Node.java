package dev.propulsionteam.computed.internal.node.internal.nodes.math.trig;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import dev.propulsionteam.computed.internal.node.internal.nodes.math.binary.BinaryMathNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Atan2Node extends BinaryMathNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_atan2");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_TRIG;
    public static final Component LABEL = Component.literal("Atan2");

    public Atan2Node(int x, int y) { super(TYPE_ID, "Atan2", x, y, Math::atan2); }

    public static void register() {
        NodeRegistry.register(TYPE_ID, Atan2Node::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
