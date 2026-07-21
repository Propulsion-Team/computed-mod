package dev.devce.websnodelib.internal.nodes.math.unary;

import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import net.minecraft.resources.ResourceLocation;

public abstract class UnaryMathNode extends WNode {

    @FunctionalInterface
    public interface UnaryOp {
        double apply(double a);
    }

    protected UnaryMathNode(ResourceLocation typeId, String title, int x, int y, UnaryOp op) {
        super(typeId, title, x, y);
        addInput("A", 0xFF00FF88);
        addOutput("Result", 0xFFFF5555);
        addElement(new WLabel(title));
        setEvaluator(n -> n.getOutputs().get(0).setValue(op.apply(n.getInputs().get(0).getValue())));
    }
}
