package dev.propulsionteam.computed.internal.node.internal.nodes.logic.binary;

import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import net.minecraft.resources.ResourceLocation;

public abstract class BinaryLogicNode extends WNode {

    @FunctionalInterface
    public interface LogicBinaryOp {
        boolean apply(boolean a, boolean b);
    }

    protected BinaryLogicNode(ResourceLocation typeId, String title, int x, int y, LogicBinaryOp op) {
        super(typeId, title, x, y);
        addInput("A", 0xFF00FF88);
        addInput("B", 0xFF88CCFF);
        addOutput("Out", 0xFFFF5555);
        addElement(new WLabel(title));
        setEvaluator(n -> {
            boolean a = n.getInputs().get(0).getValue() > 0.5;
            boolean b = n.getInputs().get(1).getValue() > 0.5;
            n.getOutputs().get(0).setValue(op.apply(a, b) ? 1.0 : 0.0);
        });
    }
}
