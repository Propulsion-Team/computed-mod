package dev.propulsionteam.computed.internal.node.internal.nodes.math.unary;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RandomNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("math_random");
    public static final ResourceLocation MENU = BuiltinNodeCategories.MATH_UNARY;
    public static final Component LABEL = Component.literal("Random");

    public RandomNode(int x, int y) {
        super(TYPE_ID, "Random", x, y);
        addOutput("Result", 0xFFFFAA00);
        WTextField minF = new WTextField(88);
        WTextField maxF = new WTextField(88);
        minF.setValue("0");
        maxF.setValue("1");
        addElement(new WLabel("Uniform in [min, max]"));
        addElement(new WLabel("Min"));
        addElement(minF);
        addElement(new WLabel("Max"));
        addElement(maxF);
        setEvaluator(n -> {
            double a = parseLooseDouble(minF, 0.0);
            double b = parseLooseDouble(maxF, 1.0);
            double lo = Math.min(a, b);
            double hi = Math.max(a, b);
            double span = hi - lo;
            double r = lo + (span > 0
                    ? java.util.concurrent.ThreadLocalRandom.current().nextDouble() * span
                    : 0.0);
            n.getOutputs().get(0).setValue(r);
        });
    }

    private static double parseLooseDouble(WTextField field, double fallback) {
        try {
            String s = field.getValue().trim().replace(',', '.');
            if (s.isEmpty()) return fallback;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, RandomNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
