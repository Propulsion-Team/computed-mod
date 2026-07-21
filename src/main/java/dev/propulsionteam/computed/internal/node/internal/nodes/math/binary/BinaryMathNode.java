package dev.propulsionteam.computed.internal.node.internal.nodes.math.binary;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import net.minecraft.resources.ResourceLocation;

public abstract class BinaryMathNode extends WNode {

    @FunctionalInterface
    public interface BinaryOp {
        double apply(double a, double b);
    }

    protected BinaryMathNode(ResourceLocation typeId, String title, int x, int y, BinaryOp op) {
        super(typeId, title, x, y);
        addInput("A", 0xFF00FF88);
        addInput("B", 0xFF00FF88);
        addOutput("Result", 0xFFFF5555);
        addElement(new WLabel(title));
        setEvaluator(n -> {
            double a = n.getInputs().get(0).getValue();
            double b = n.getInputs().get(1).getValue();
            n.getOutputs().get(0).setValue(op.apply(a, b));
        });
    }
}
