package dev.propulsionteam.computed.customnodes.expr;

import java.util.List;

@FunctionalInterface
public interface ExprFunction {
    Value call(List<Value> args, EvalContext ctx);
}
