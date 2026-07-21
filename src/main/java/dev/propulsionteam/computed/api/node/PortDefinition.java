package dev.propulsionteam.computed.api.node;

import java.util.Objects;
import net.minecraft.network.chat.Component;

/** Display and direction metadata for a stable port key. */
public record PortDefinition<T>(PortKey<T> key, PortDirection direction, Component label) {
    public PortDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(label, "label");
    }

    public static <T> PortDefinition<T> input(PortKey<T> key, Component label) {
        return new PortDefinition<>(key, PortDirection.INPUT, label);
    }

    public static <T> PortDefinition<T> output(PortKey<T> key, Component label) {
        return new PortDefinition<>(key, PortDirection.OUTPUT, label);
    }
}
