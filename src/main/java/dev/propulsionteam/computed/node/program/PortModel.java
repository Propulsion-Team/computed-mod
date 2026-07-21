package dev.propulsionteam.computed.node.program;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Persisted description and raw value data for one node port. */
public record PortModel(
        PortId id,
        Direction direction,
        String valueType,
        String label,
        CompoundTag data) {

    public static final String UNKNOWN_VALUE_TYPE = "unknown";

    public PortModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        valueType = valueType == null || valueType.isBlank() ? UNKNOWN_VALUE_TYPE : valueType;
        label = label == null ? "" : label;
        data = data == null ? new CompoundTag() : data.copy();
    }

    @Override
    public CompoundTag data() {
        return data.copy();
    }

    public enum Direction {
        INPUT,
        OUTPUT
    }
}
