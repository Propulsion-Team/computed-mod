package dev.propulsionteam.computed.lua.node;

import java.util.List;
import java.util.Objects;
import org.luaj.vm2.LuaValue;

public record LuaFieldSchema(
        String id,
        FieldType type,
        LuaValue defaultValue,
        List<String> choices,
        Double minimum,
        Double maximum,
        String label,
        FieldControl control,
        Double step) {

    public LuaFieldSchema {
        id = LuaSchemaNames.requireStableId(id, "field");
        Objects.requireNonNull(type, "type");
        defaultValue = defaultValue == null ? LuaValue.NIL : defaultValue;
        choices = choices == null ? List.of() : List.copyOf(choices);
        label = label == null || label.isBlank() ? readableLabel(id) : label.strip();
        control = control == null ? FieldControl.VALUE : control;
        if (label.length() > 64) {
            throw new LuaDefinitionException("Field " + id + " label exceeds 64 characters");
        }
        if (minimum != null && !Double.isFinite(minimum)
                || maximum != null && !Double.isFinite(maximum)) {
            throw new LuaDefinitionException("Field " + id + " range must be finite");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new LuaDefinitionException("Field " + id + " has a minimum greater than its maximum");
        }
        if (step != null && (!Double.isFinite(step) || step <= 0)) {
            throw new LuaDefinitionException("Field " + id + " step must be positive and finite");
        }
        if (type == FieldType.CHOICE && choices.isEmpty()) {
            throw new LuaDefinitionException("Choice field " + id + " must declare at least one choice");
        }
        if (control == FieldControl.SLIDER
                && (type != FieldType.NUMBER
                        || minimum == null
                        || maximum == null
                        || maximum <= minimum)) {
            throw new LuaDefinitionException(
                    "Slider field " + id + " requires a numeric min smaller than max");
        }
    }

    private static String readableLabel(String id) {
        String[] words = id.replace('-', '_').split("_+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.isEmpty() ? id : label.toString();
    }
}
