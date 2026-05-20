package dev.propulsionteam.computed.customnodes;

import dev.propulsionteam.computed.customnodes.expr.EvalContext;
import dev.propulsionteam.computed.customnodes.expr.FunctionRegistry;
import dev.propulsionteam.computed.customnodes.expr.Lexer;
import dev.propulsionteam.computed.customnodes.expr.Parser;
import dev.propulsionteam.computed.customnodes.expr.Value;
import java.util.HashMap;
import java.util.Map;

public final class ExpressionEvaluator {
    private ExpressionEvaluator() {}

    /** Full-featured entry point: typed Value result, stateful context, source functions. */
    public static Value eval(String program, EvalContext ctx) {
        Parser parser = new Parser(Lexer.tokenize(program), ctx);
        return parser.parseProgram();
    }

    /** Legacy numeric-only entry point used during load-time validation. */
    public static double eval(String expression, Map<String, Double> vars) {
        Map<String, Value> valueVars = new HashMap<>();
        for (Map.Entry<String, Double> e : vars.entrySet()) {
            valueVars.put(e.getKey(), Value.of(e.getValue()));
        }
        Map<String, Value> state = new HashMap<>();
        EvalContext ctx = new EvalContext(valueVars, state, FunctionRegistry.get());
        return eval(expression, ctx).asNumber();
    }
}
