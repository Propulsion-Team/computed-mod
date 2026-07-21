package dev.propulsionteam.computed.api.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class PortSchemaPropertyTest {
    private static final PortKey<Double> AMOUNT = PortKey.of("amount", PortType.NUMBER);
    private static final PortKey<String> LABEL = PortKey.of("label", PortType.STRING);
    private static final NodeProperty<Integer> PORT_COUNT = NodeProperty.builder(
                    "port_count", Component.literal("Port count"), Integer.class, Codec.INT)
            .defaultValue(2)
            .validator(value -> value >= 0 && value <= 8, "must be between 0 and 8")
            .build();

    @Test
    void builtInPortTypesHaveNeutralDefaultsAndRejectWrongValues() {
        assertEquals(0.0D, PortType.NUMBER.defaultValue());
        assertEquals("", PortType.STRING.defaultValue());
        assertNull(PortType.WIDGET.defaultValue());

        assertEquals(0.0D, PortType.NUMBER.castOrDefault("not a number"));
        assertEquals("", PortType.STRING.castOrDefault(null));
        assertTrue(PortType.WIDGET.accepts(new Object()));
        assertTrue(PortType.WIDGET.accepts(null));
        assertFalse(PortType.NUMBER.accepts(1));
    }

    @Test
    void portKeysAndSchemasAreStableTypedAndOrdered() {
        NodeSchema schema = NodeSchema.builder()
                .input(AMOUNT, Component.literal("Amount"))
                .output(LABEL, Component.literal("Label"))
                .build();

        assertEquals(List.of("amount", "label"), schema.ports().stream()
                .map(port -> port.key().id())
                .toList());
        assertEquals(PortDirection.INPUT, schema.port(AMOUNT).orElseThrow().direction());
        assertEquals(PortDirection.OUTPUT, schema.port(LABEL).orElseThrow().direction());
        assertTrue(schema.port(PortKey.of("amount", PortType.STRING)).isEmpty());
        assertEquals(AMOUNT, PortKey.of("amount", PortType.NUMBER));

        assertThrows(IllegalArgumentException.class, () -> PortKey.of("Display Name", PortType.STRING));
        assertThrows(IllegalArgumentException.class, () -> NodeSchema.builder()
                .input(AMOUNT, Component.literal("First"))
                .output(PortKey.of("amount", PortType.STRING), Component.literal("Duplicate"))
                .build());
    }

    @Test
    void propertyBagsAreTypedValidatedAndImmutable() {
        NodeProperty<Double> gain = NodeProperty.builder(
                        "gain", Component.literal("Gain"), Double.class, Codec.DOUBLE)
                .defaultValue(2.0D)
                .validator(value -> Double.isFinite(value) && value >= 0.0D, "must be finite and non-negative")
                .build();
        NodePropertyBag defaults = NodePropertyBag.defaults(List.of(gain));
        NodePropertyBag changed = defaults.with(gain, 3.5D);

        assertEquals(2.0D, defaults.get(gain));
        assertEquals(3.5D, changed.get(gain));
        assertNotSame(defaults, changed);
        assertThrows(IllegalArgumentException.class, () -> defaults.with(gain, Double.NaN));
        assertThrows(UnsupportedOperationException.class, () -> changed.values().put("gain", 4.0D));

        NodeProperty<Double> sameStableKey = NodeProperty.number("gain", Component.literal("Other"), 1.0D);
        assertThrows(IllegalArgumentException.class, () -> defaults.get(sameStableKey));
        assertThrows(
                IllegalArgumentException.class,
                () -> NodePropertyBag.defaults(List.of(gain, sameStableKey)));
    }

    @Test
    void schemaFactoryCanDeriveStableDynamicPortsFromProperties() {
        NodeSchemaFactory factory = properties -> {
            NodeSchema.Builder schema = NodeSchema.builder();
            for (int index = 0; index < properties.get(PORT_COUNT); index++) {
                schema.input(
                        PortKey.of("widget_" + index, PortType.WIDGET),
                        Component.literal("Widget " + (index + 1)));
            }
            return schema.build();
        };

        NodePropertyBag defaults = NodePropertyBag.defaults(List.of(PORT_COUNT));
        NodeSchema twoPorts = factory.create(defaults);
        NodeSchema threePorts = factory.create(defaults.with(PORT_COUNT, 3));

        assertEquals(List.of("widget_0", "widget_1"), twoPorts.inputs().stream()
                .map(port -> port.key().id())
                .toList());
        assertEquals(List.of("widget_0", "widget_1", "widget_2"), threePorts.inputs().stream()
                .map(port -> port.key().id())
                .toList());
    }
}
