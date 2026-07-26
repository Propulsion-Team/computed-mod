package dev.propulsionteam.computed.lua.node;

import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaValue;

public final class LuaFieldValues {
    private static final Set<String> DIRECTIONS = Set.of(
            "front",
            "back",
            "left",
            "right",
            "up",
            "down",
            "north",
            "south",
            "east",
            "west");

    private LuaFieldValues() {}

    public static LuaValue normalize(LuaFieldSchema schema, LuaValue value) {
        LuaValue candidate = value == null || value.isnil() ? schema.defaultValue() : value;
        if (candidate.isnil()) {
            return candidate;
        }
        return switch (schema.type()) {
            case NUMBER -> normalizeNumber(schema, candidate);
            case TEXT -> LuaValue.valueOf(candidate.checkjstring());
            case BOOLEAN -> LuaValue.valueOf(candidate.checkboolean());
            case CHOICE -> normalizeChoice(schema, candidate);
            case COLOR -> LuaValue.valueOf((double) normalizeColor(candidate));
            case DIRECTION -> normalizeDirection(candidate);
            case ITEM -> normalizeItem(candidate);
        };
    }

    public static String validationError(LuaFieldSchema schema, LuaValue value) {
        try {
            LuaValue normalized = normalize(schema, value);
            if (!value.isnil() && !equivalent(value, normalized)) {
                return "field " + schema.id() + " is outside its declared constraints";
            }
            return null;
        } catch (RuntimeException exception) {
            return "field " + schema.id() + " is invalid: " + exception.getMessage();
        }
    }

    private static LuaValue normalizeNumber(LuaFieldSchema schema, LuaValue value) {
        double number = value.checkdouble();
        if (!Double.isFinite(number)) {
            throw new LuaDefinitionException("number must be finite");
        }
        if (schema.minimum() != null) {
            number = Math.max(schema.minimum(), number);
        }
        if (schema.maximum() != null) {
            number = Math.min(schema.maximum(), number);
        }
        if (schema.step() != null) {
            double origin = schema.minimum() == null ? 0 : schema.minimum();
            number = origin + Math.round((number - origin) / schema.step()) * schema.step();
            if (schema.minimum() != null) {
                number = Math.max(schema.minimum(), number);
            }
            if (schema.maximum() != null) {
                number = Math.min(schema.maximum(), number);
            }
        }
        return LuaValue.valueOf(number);
    }

    private static LuaValue normalizeChoice(LuaFieldSchema schema, LuaValue value) {
        String choice = value.checkjstring();
        if (!schema.choices().contains(choice)) {
            throw new LuaDefinitionException("choice is not declared");
        }
        return LuaValue.valueOf(choice);
    }

    private static long normalizeColor(LuaValue value) {
        double number = value.checkdouble();
        if (!Double.isFinite(number) || number < 0 || number > 0xffffffffL) {
            throw new LuaDefinitionException("color must be an unsigned 32-bit ARGB value");
        }
        return (long) number;
    }

    private static LuaValue normalizeDirection(LuaValue value) {
        String direction = value.checkjstring().toLowerCase(Locale.ROOT);
        if (!DIRECTIONS.contains(direction)) {
            throw new LuaDefinitionException("unknown direction " + direction);
        }
        return LuaValue.valueOf(direction);
    }

    private static LuaValue normalizeItem(LuaValue value) {
        String item = value.checkjstring();
        ResourceLocation.parse(item);
        return LuaValue.valueOf(item);
    }

    private static boolean equivalent(LuaValue first, LuaValue second) {
        if (first.isnumber() && second.isnumber()) {
            return Double.compare(first.todouble(), second.todouble()) == 0;
        }
        return first.raweq(second);
    }
}
