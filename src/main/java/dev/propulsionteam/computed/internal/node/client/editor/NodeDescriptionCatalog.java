package dev.propulsionteam.computed.internal.node.client.editor;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Computed-owned descriptions for the built-in node palette. Add-ons receive a useful fallback. */
public final class NodeDescriptionCatalog {
    private static final Map<String, String> BUILT_INS = Map.ofEntries(
            Map.entry("create_redstone_link_receiver", "Receives a value from a matching Create redstone link frequency."),
            Map.entry("create_redstone_link_sender", "Sends a value over a matching Create redstone link frequency."),
            Map.entry("block_location", "Provides the configured block position as X, Y, and Z values."),
            Map.entry("block_presence", "Tests whether a block matching the configured target is present."),
            Map.entry("block_rotation", "Reads the rotation or facing of the configured block."),
            Map.entry("command", "Runs a Minecraft command when its execution input is triggered."),
            Map.entry("comparator_read", "Reads the comparator output level from the configured block face."),
            Map.entry("concatenate_strings", "Joins two text values into one string."),
            Map.entry("if_branch", "Routes execution through the true or false branch of a condition."),
            Map.entry("redstone_input", "Reads the redstone level from a configured adjacent block face."),
            Map.entry("redstone_emitter", "Outputs a configurable redstone signal to an adjacent block face."),
            Map.entry("switch", "Selects one of two values using a boolean condition."),
            Map.entry("world_time", "Provides the current world day and time values."),
            Map.entry("button_widget", "Creates an interactive button in the computer's widget output."),
            Map.entry("clock_widget", "Displays the current time in the computer's widget output."),
            Map.entry("color_source", "Produces a configurable RGB color value."),
            Map.entry("progress_bar_widget", "Displays a value as a progress bar widget."),
            Map.entry("slider_widget", "Creates an interactive numeric slider widget."),
            Map.entry("text_source", "Produces a configurable text value."),
            Map.entry("text_widget", "Displays text in the computer's widget output."),
            Map.entry("peripheral", "Connects the graph to a supported hardware peripheral."),
            Map.entry("counter", "Counts trigger events and exposes the current count."),
            Map.entry("mux", "Selects one of several inputs using an index."),
            Map.entry("pass_every_n", "Passes every Nth rising-edge trigger."),
            Map.entry("bool_to_level", "Converts a boolean value to a redstone level."),
            Map.entry("display", "Displays an input value directly on the node."),
            Map.entry("level_to_bool", "Converts a redstone level to a boolean value."),
            Map.entry("logic_and", "Returns true only when both inputs are true."),
            Map.entry("edge_fall", "Emits a trigger when a boolean changes from true to false."),
            Map.entry("edge_rise", "Emits a trigger when a boolean changes from false to true."),
            Map.entry("logic_nand", "Returns false only when both inputs are true."),
            Map.entry("logic_nor", "Returns true only when both inputs are false."),
            Map.entry("logic_or", "Returns true when either input is true."),
            Map.entry("schmitt", "Applies separate rising and falling thresholds to a numeric input."),
            Map.entry("logic_xnor", "Returns true when both boolean inputs are equal."),
            Map.entry("logic_xor", "Returns true when exactly one input is true."),
            Map.entry("cmp_approx", "Tests whether two numbers are equal within a tolerance."),
            Map.entry("cmp_eq", "Tests whether two values are equal."),
            Map.entry("cmp_ge", "Tests whether the first number is greater than or equal to the second."),
            Map.entry("cmp_gt", "Tests whether the first number is greater than the second."),
            Map.entry("cmp_le", "Tests whether the first number is less than or equal to the second."),
            Map.entry("cmp_lt", "Tests whether the first number is less than the second."),
            Map.entry("d_flipflop", "Stores a boolean value on the rising edge of its clock input."),
            Map.entry("sr_latch", "Stores a boolean state controlled by set and reset inputs."),
            Map.entry("logic_not", "Inverts a boolean input."),
            Map.entry("math_add", "Adds two numbers."),
            Map.entry("math_clamp", "Limits a number to a configurable minimum and maximum."),
            Map.entry("math_divide", "Divides the first number by the second."),
            Map.entry("math_lerp", "Interpolates between two numbers by a configurable amount."),
            Map.entry("math_map", "Remaps a number from one range into another."),
            Map.entry("math_max", "Returns the larger of two numbers."),
            Map.entry("math_min", "Returns the smaller of two numbers."),
            Map.entry("math_mod", "Returns the remainder after division."),
            Map.entry("math_multiply", "Multiplies two numbers."),
            Map.entry("math_pow", "Raises the first number to the power of the second."),
            Map.entry("math_subtract", "Subtracts the second number from the first."),
            Map.entry("math_atan2", "Calculates the two-argument arctangent in radians."),
            Map.entry("math_cos", "Calculates the cosine of an angle in radians."),
            Map.entry("math_sin", "Calculates the sine of an angle in radians."),
            Map.entry("math_tan", "Calculates the tangent of an angle in radians."),
            Map.entry("math_abs", "Returns the absolute value of a number."),
            Map.entry("math_average", "Calculates the average of its numeric inputs."),
            Map.entry("math_ceil", "Rounds a number upward to the nearest integer."),
            Map.entry("math_exp", "Raises Euler's number to the input power."),
            Map.entry("math_floor", "Rounds a number downward to the nearest integer."),
            Map.entry("math_log10", "Calculates the base-10 logarithm of a number."),
            Map.entry("math_log", "Calculates the natural logarithm of a number."),
            Map.entry("math_negate", "Changes the sign of a number."),
            Map.entry("quantize_redstone", "Rounds and limits a number to a redstone level from 0 to 15."),
            Map.entry("math_random", "Produces a random number within the configured range."),
            Map.entry("math_round", "Rounds a number to the nearest integer."),
            Map.entry("math_sign", "Returns -1, 0, or 1 for the sign of a number."),
            Map.entry("math_sqrt", "Calculates the square root of a number."),
            Map.entry("constant", "Produces a configurable numeric constant."),
            Map.entry("delay", "Delays an incoming value or trigger by a configured duration."),
            Map.entry("oscillator", "Produces a repeating waveform at a configurable rate."),
            Map.entry("pulse", "Produces a timed pulse when triggered."),
            Map.entry("sample_hold", "Captures and holds an input value when triggered."),
            Map.entry("tick", "Emits an execution trigger every computer tick."),
            Map.entry("rgb_preview", "Previews an RGB color inside the node editor."),
            Map.entry("3d_preview", "Displays custom 3D viewport content inside the node."),
            Map.entry("tool_section", "Creates a labeled section for organizing nodes."),
            Map.entry("function_card", "Runs a reusable nested function graph."),
            Map.entry("fn_start", "Defines inputs at the beginning of a nested function."),
            Map.entry("fn_end", "Defines outputs at the end of a nested function."));

    private NodeDescriptionCatalog() {}

    public static String description(ResourceLocation nodeType, Component title) {
        String builtIn = BUILT_INS.get(nodeType.getPath());
        return builtIn != null ? builtIn : "Adds the " + title.getString() + " node.";
    }

    public static Component component(ResourceLocation nodeType, Component title) {
        String key = "node." + nodeType.getNamespace() + "." + nodeType.getPath() + ".description";
        return Component.translatableWithFallback(key, description(nodeType, title));
    }

    public static boolean hasBuiltInDescription(ResourceLocation nodeType) {
        return BUILT_INS.containsKey(nodeType.getPath());
    }

    public static int builtInDescriptionCount() {
        return BUILT_INS.size();
    }
}
