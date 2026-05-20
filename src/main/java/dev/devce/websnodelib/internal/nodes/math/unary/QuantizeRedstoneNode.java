package dev.devce.websnodelib.internal.nodes.math.unary;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class QuantizeRedstoneNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("quantize_redstone");
    public static final ResourceLocation MENU = MenuCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Quantize 0-15");

    public QuantizeRedstoneNode(int x, int y) {
        super(TYPE_ID, "Quantize 0-15", x, y);
        addInput("x", 0xFF88CCFF);
        addOutput("Level", 0xFFFFBB00);
        addElement(new WLabel("round + clamp to 0-15"));
        setEvaluator(n -> {
            double v = n.getInputs().get(0).getValue();
            int q = Mth.clamp((int) Math.round(v), 0, 15);
            n.getOutputs().get(0).setValue(q);
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, QuantizeRedstoneNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
