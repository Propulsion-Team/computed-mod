package dev.propulsionteam.computed.customnodes;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.devce.websnodelib.api.WPin;
import dev.propulsionteam.computed.customnodes.expr.EvalContext;
import dev.propulsionteam.computed.customnodes.expr.FunctionRegistry;
import dev.propulsionteam.computed.customnodes.expr.Value;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public final class CustomNodeLoader {
    private static final Gson GSON = new Gson();
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_\\-./]+");

    public record LoadResult(List<CustomNodeDefinition> definitions, CustomNodeDiagnostics diagnostics) {}

    public LoadResult load(Path root) {
        CustomNodeDiagnostics diagnostics = new CustomNodeDiagnostics();
        List<CustomNodeDefinition> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return new LoadResult(out, diagnostics);
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .forEach(file -> parseFile(file, out, diagnostics));
        } catch (IOException e) {
            diagnostics.error("Failed to walk " + root + ": " + e.getMessage());
        }
        return new LoadResult(out, diagnostics);
    }

    private static void parseFile(Path file, List<CustomNodeDefinition> out, CustomNodeDiagnostics diagnostics) {
        try {
            String raw = Files.readString(file);
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            CustomNodeDefinition def = parseDefinition(obj, file);
            out.add(def);
        } catch (Exception e) {
            diagnostics.error(file + ": " + e.getMessage());
        }
    }

    private static CustomNodeDefinition parseDefinition(JsonObject obj, Path file) {
        ResourceLocation id = ResourceLocation.parse(requiredString(obj, "id"));
        String label = requiredString(obj, "label");
        List<String> menuPath = parseMenuPath(obj.getAsJsonArray("menuPath"));
        List<CustomNodeDefinition.PinSpec> inputs = parseInputs(obj.getAsJsonArray("inputs"));
        List<CustomNodeDefinition.OutputSpec> outputs = parseOutputs(obj.getAsJsonArray("outputs"));
        Map<String, Double> constants = parseConstants(obj.getAsJsonObject("constants"));
        List<CustomNodeDefinition.StateSpec> state = parseState(obj.getAsJsonArray("state"));

        if (outputs.isEmpty()) throw new IllegalArgumentException("outputs must not be empty");
        ensureUniquePinNames(inputs, outputs);
        validateExpressions(outputs, inputs, constants, state);
        return new CustomNodeDefinition(id, label, menuPath, inputs, outputs, constants, state, file);
    }

    private static void validateExpressions(
            List<CustomNodeDefinition.OutputSpec> outputs,
            List<CustomNodeDefinition.PinSpec> inputs,
            Map<String, Double> constants,
            List<CustomNodeDefinition.StateSpec> state) {
        Map<String, Value> vars = new HashMap<>();
        for (CustomNodeDefinition.PinSpec pin : inputs) {
            String key = pin.name().toLowerCase(Locale.ROOT);
            vars.put(key, pin.dataType() == WPin.DataType.STRING ? Value.EMPTY_STRING : Value.ZERO);
        }
        for (Map.Entry<String, Double> e : constants.entrySet()) {
            vars.put(e.getKey().toLowerCase(Locale.ROOT), Value.of(e.getValue()));
        }
        for (CustomNodeDefinition.StateSpec s : state) {
            vars.put(s.name().toLowerCase(Locale.ROOT), s.init());
        }
        Map<String, Value> stateStore = new HashMap<>();
        EvalContext ctx = new EvalContext(vars, stateStore, FunctionRegistry.get());

        for (CustomNodeDefinition.StateSpec s : state) {
            try {
                ExpressionEvaluator.eval(s.updateExpression(), ctx);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid state update for '" + s.name() + "': " + e.getMessage());
            }
        }
        for (CustomNodeDefinition.OutputSpec out : outputs) {
            try {
                ExpressionEvaluator.eval(out.expression(), ctx);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid expression for output '" + out.name() + "': " + e.getMessage());
            }
        }
    }

    private static void ensureUniquePinNames(
            List<CustomNodeDefinition.PinSpec> inputs, List<CustomNodeDefinition.OutputSpec> outputs) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CustomNodeDefinition.PinSpec pin : inputs) {
            if (!seen.add(pin.name().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate input name: " + pin.name());
        }
        for (CustomNodeDefinition.OutputSpec pin : outputs) {
            if (!seen.add(pin.name().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate output name: " + pin.name());
        }
    }

    private static List<CustomNodeDefinition.PinSpec> parseInputs(JsonArray arr) {
        List<CustomNodeDefinition.PinSpec> list = new ArrayList<>();
        if (arr == null) return list;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            WPin.DataType dt = parsePinType(o, WPin.DataType.NUMBER);
            int defaultColor = dt == WPin.DataType.STRING ? 0xFFFFC830 : 0xFF00FF88;
            list.add(new CustomNodeDefinition.PinSpec(requiredString(o, "name"), parseColor(o, defaultColor), dt));
        }
        return list;
    }

    private static List<CustomNodeDefinition.OutputSpec> parseOutputs(JsonArray arr) {
        List<CustomNodeDefinition.OutputSpec> list = new ArrayList<>();
        if (arr == null) return list;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            WPin.DataType dt = parsePinType(o, WPin.DataType.NUMBER);
            int defaultColor = dt == WPin.DataType.STRING ? 0xFFFFC830 : 0xFFFF5555;
            list.add(new CustomNodeDefinition.OutputSpec(
                    requiredString(o, "name"),
                    parseColor(o, defaultColor),
                    requiredString(o, "expression"),
                    dt));
        }
        return list;
    }

    private static List<CustomNodeDefinition.StateSpec> parseState(JsonArray arr) {
        List<CustomNodeDefinition.StateSpec> list = new ArrayList<>();
        if (arr == null) return list;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String name = requiredString(o, "name");
            // init can be number or string
            Value init;
            if (o.has("init")) {
                JsonElement initEl = o.get("init");
                if (initEl.isJsonPrimitive() && initEl.getAsJsonPrimitive().isString()) {
                    init = Value.of(initEl.getAsString());
                } else {
                    init = Value.of(initEl.getAsDouble());
                }
            } else {
                init = Value.ZERO;
            }
            String update = o.has("update") ? o.get("update").getAsString() : name;
            list.add(new CustomNodeDefinition.StateSpec(name, init, update));
        }
        return list;
    }

    private static WPin.DataType parsePinType(JsonObject obj, WPin.DataType fallback) {
        if (!obj.has("type")) return fallback;
        return switch (obj.get("type").getAsString().toLowerCase(Locale.ROOT)) {
            case "string", "str", "text" -> WPin.DataType.STRING;
            default                       -> WPin.DataType.NUMBER;
        };
    }

    private static Map<String, Double> parseConstants(JsonObject obj) {
        Map<String, Double> constants = new LinkedHashMap<>();
        if (obj == null) return constants;
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) throw new IllegalArgumentException("constants key is blank");
            constants.put(key, entry.getValue().getAsDouble());
        }
        return constants;
    }

    private static int parseColor(JsonObject obj, int fallback) {
        if (!obj.has("color")) return fallback;
        String s = obj.get("color").getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) return (0xFF << 24) | Integer.parseUnsignedInt(s, 16);
        if (s.length() == 8) return (int) Long.parseLong(s, 16);
        throw new IllegalArgumentException("Invalid color hex: " + obj.get("color").getAsString());
    }

    private static List<String> parseMenuPath(JsonArray arr) {
        List<String> segments = new ArrayList<>();
        if (arr == null || arr.isEmpty()) {
            segments.add("Custom");
            return segments;
        }
        for (JsonElement el : arr) {
            String segment = el.getAsString().trim();
            if (segment.isEmpty()) throw new IllegalArgumentException("menuPath contains blank segment");
            String normalized = normalizeSegment(segment);
            if (!SEGMENT.matcher(normalized).matches())
                throw new IllegalArgumentException("menuPath segment contains unsupported chars: " + segment);
            segments.add(segment);
        }
        return segments;
    }

    static String normalizeSegment(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String requiredString(JsonObject obj, String key) {
        if (!obj.has(key)) throw new IllegalArgumentException("Missing required field: " + key);
        String value = GSON.fromJson(obj.get(key), String.class);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Field " + key + " is blank");
        return value.trim();
    }
}
