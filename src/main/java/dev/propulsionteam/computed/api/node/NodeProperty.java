package dev.propulsionteam.computed.api.node;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** A typed, persistable property definition used by node schemas and generic editor controls. */
public final class NodeProperty<T> {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z][a-z0-9_.-]*");

    private final String key;
    private final Component title;
    private final Class<T> valueClass;
    private final Codec<T> codec;
    private final Supplier<? extends T> defaultFactory;
    private final Predicate<? super T> validator;
    private final String validationMessage;

    private NodeProperty(Builder<T> builder) {
        key = builder.key;
        title = builder.title;
        valueClass = builder.valueClass;
        codec = builder.codec;
        defaultFactory = builder.defaultFactory;
        validator = builder.validator;
        validationMessage = builder.validationMessage;
        validate(defaultValue());
    }

    public static <T> Builder<T> builder(
            String key, Component title, Class<T> valueClass, Codec<T> codec) {
        return new Builder<>(key, title, valueClass, codec);
    }

    public static NodeProperty<String> string(String key, Component title, String defaultValue) {
        return builder(key, title, String.class, Codec.STRING).defaultValue(defaultValue).build();
    }

    public static NodeProperty<Double> number(String key, Component title, double defaultValue) {
        return builder(key, title, Double.class, Codec.DOUBLE).defaultValue(defaultValue).build();
    }

    public static NodeProperty<Integer> integer(String key, Component title, int defaultValue) {
        return builder(key, title, Integer.class, Codec.INT).defaultValue(defaultValue).build();
    }

    public static NodeProperty<Boolean> bool(String key, Component title, boolean defaultValue) {
        return builder(key, title, Boolean.class, Codec.BOOL).defaultValue(defaultValue).build();
    }

    public String key() {
        return key;
    }

    public Component title() {
        return title;
    }

    public Class<T> valueClass() {
        return valueClass;
    }

    public Codec<T> codec() {
        return codec;
    }

    /** Returns a fresh default when the definition supplied a factory. */
    public T defaultValue() {
        return Objects.requireNonNull(defaultFactory.get(), "Default value for property '" + key + "'");
    }

    public boolean isValid(T value) {
        return value != null && valueClass.isInstance(value) && validator.test(value);
    }

    public void validate(T value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid value for property '" + key + "': " + validationMessage);
        }
    }

    T castAndValidate(Object value) {
        if (!valueClass.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Property '" + key + "' requires " + valueClass.getSimpleName() + ", got "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        T typed = valueClass.cast(value);
        validate(typed);
        return typed;
    }

    @Override
    public String toString() {
        return key;
    }

    public static final class Builder<T> {
        private final String key;
        private final Component title;
        private final Class<T> valueClass;
        private final Codec<T> codec;
        private Supplier<? extends T> defaultFactory;
        private Predicate<? super T> validator = ignored -> true;
        private String validationMessage = "value failed validation";

        private Builder(String key, Component title, Class<T> valueClass, Codec<T> codec) {
            if (key == null || !VALID_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "Property key must match " + VALID_KEY.pattern() + ", got: " + key);
            }
            this.key = key;
            this.title = Objects.requireNonNull(title, "title");
            this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
            this.codec = Objects.requireNonNull(codec, "codec");
        }

        public Builder<T> defaultValue(T value) {
            Objects.requireNonNull(value, "value");
            defaultFactory = () -> value;
            return this;
        }

        public Builder<T> defaultFactory(Supplier<? extends T> factory) {
            defaultFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        public Builder<T> validator(Predicate<? super T> validator, String message) {
            this.validator = Objects.requireNonNull(validator, "validator");
            validationMessage = Objects.requireNonNull(message, "message");
            return this;
        }

        public NodeProperty<T> build() {
            if (defaultFactory == null) {
                throw new IllegalStateException("Property '" + key + "' has no default value");
            }
            return new NodeProperty<>(this);
        }
    }
}
