package dev.propulsionteam.computed.customnodes;

import dev.devce.websnodelib.api.WPin;
import dev.propulsionteam.computed.customnodes.expr.Value;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CustomNodeDefinition(
        ResourceLocation id,
        String label,
        List<String> menuPath,
        List<PinSpec> inputs,
        List<OutputSpec> outputs,
        Map<String, Double> constants,
        List<StateSpec> state,
        Path sourceFile) {

    public record PinSpec(String name, int color, WPin.DataType dataType) {
        public PinSpec(String name, int color) {
            this(name, color, WPin.DataType.NUMBER);
        }
    }

    public record OutputSpec(String name, int color, String expression, WPin.DataType dataType) {
        public OutputSpec(String name, int color, String expression) {
            this(name, color, expression, WPin.DataType.NUMBER);
        }
    }

    /** A persistent state variable: holds its value across ticks, updated each tick. */
    public record StateSpec(String name, Value init, String updateExpression) {}
}
