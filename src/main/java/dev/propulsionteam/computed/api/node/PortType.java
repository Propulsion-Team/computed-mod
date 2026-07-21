package dev.propulsionteam.computed.api.node;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The value type carried by a node port.
 *
 * <p>Computed deliberately exposes a closed set of types so graph validation and persistence do not
 * depend on Java implementation classes supplied by addons.</p>
 */
public final class PortType<T> {
    public static final PortType<Double> NUMBER =
            new PortType<>("number", Double.class, () -> 0.0D, 0xFFFFFFFF);
    public static final PortType<String> STRING =
            new PortType<>("string", String.class, () -> "", 0xFFFFC830);
    /**
     * Opaque widget payload. Widget implementations are deliberately not part of this low-level API;
     * a {@code null} value means that no widget is present.
     */
    public static final PortType<Object> WIDGET =
            new PortType<>("widget", Object.class, () -> null, 0xFF40D0FF);

    private final String id;
    private final Class<T> valueClass;
    private final Supplier<? extends T> defaultFactory;
    private final int defaultColor;

    private PortType(String id, Class<T> valueClass, Supplier<? extends T> defaultFactory, int defaultColor) {
        this.id = Objects.requireNonNull(id, "id");
        this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
        this.defaultFactory = Objects.requireNonNull(defaultFactory, "defaultFactory");
        this.defaultColor = defaultColor;
    }

    public String id() {
        return id;
    }

    public Class<T> valueClass() {
        return valueClass;
    }

    /**
     * Returns the neutral value emitted by an unconnected or disabled port. Widget ports return
     * {@code null}, which is the explicit no-widget value.
     */
    public T defaultValue() {
        return defaultFactory.get();
    }

    public int defaultColor() {
        return defaultColor;
    }

    public boolean accepts(Object value) {
        return value == null ? "widget".equals(id) : valueClass.isInstance(value);
    }

    /** Returns {@code value} when it has this type, otherwise this type's neutral value. */
    public T castOrDefault(Object value) {
        return accepts(value) ? valueClass.cast(value) : defaultValue();
    }

    @Override
    public String toString() {
        return id;
    }
}
