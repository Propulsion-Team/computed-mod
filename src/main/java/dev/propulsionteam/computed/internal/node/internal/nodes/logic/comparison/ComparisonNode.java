package dev.propulsionteam.computed.internal.node.internal.nodes.logic.comparison;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import net.minecraft.resources.ResourceLocation;

public abstract class ComparisonNode extends WNode {

    @FunctionalInterface
    public interface CompareOp {
        boolean apply(double a, double b);
    }

    protected ComparisonNode(ResourceLocation typeId, String title, String label, int x, int y, CompareOp op) {
        super(typeId, title, x, y);
        addInput("A", 0xFF00FF88);
        addInput("B", 0xFF88CCFF);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel(label));
        setEvaluator(n -> {
            double a = n.getInputs().get(0).getValue();
            double b = n.getInputs().get(1).getValue();
            n.getOutputs().get(0).setValue(op.apply(a, b) ? 1.0 : 0.0);
        });
    }
}
