package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.client.editor.preview.LuaLivePreview;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.runtime.LuaInvocationResult;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class BundledNodeFieldParityTest {
    @Test
    void restoresLegacySliderRangesAndWidgetConfiguration() {
        Map<String, dev.propulsionteam.computed.graph.LuaDefinitionSource> library =
                BundledLuaLibrary.load();
        LuaNodeDefinition level = load(library, "computed:level_to_bool");
        LuaNodeDefinition schmitt = load(library, "computed:schmitt");
        LuaNodeDefinition approximate = load(library, "computed:cmp_approx");
        LuaNodeDefinition delay = load(library, "computed:delay");
        LuaNodeDefinition oscillator = load(library, "computed:oscillator");
        LuaNodeDefinition pulse = load(library, "computed:pulse");
        LuaNodeDefinition tick = load(library, "computed:tick");
        LuaNodeDefinition clock = load(library, "computed:clock_widget");
        LuaNodeDefinition text = load(library, "computed:text_widget");

        assertSlider(level, "threshold", 0, 15, 8);
        assertSlider(schmitt, "low", 0, 15, 5);
        assertSlider(schmitt, "high", 0, 15, 10);
        assertSlider(approximate, "epsilon", 0, 15, 0.5);
        assertSlider(delay, "delay", 0, 200, 1);
        assertSlider(oscillator, "period", 1, 200, 20);
        assertSlider(oscillator, "amplitude", 1, 100, 1);
        assertSlider(pulse, "period", 1, 20, 20);
        assertSlider(tick, "rate", 0, 20, 20);
        assertNotNull(field(clock, "width"));
        assertNotNull(field(clock, "alignment"));
        assertNotNull(field(text, "width"));
        assertEquals(FieldType.CHOICE, field(text, "alignment").type());
        library.values().forEach(source -> {
            LuaNodeDefinition definition = load(source);
            definition.fields().forEach(field ->
                    assertNull(LuaFieldValues.validationError(field, field.defaultValue())));
        });
    }

    @Test
    void restoredThresholdAndDelayAffectRuntimeBehavior() {
        Map<String, dev.propulsionteam.computed.graph.LuaDefinitionSource> library =
                BundledLuaLibrary.load();
        LuaLivePreview threshold = new LuaLivePreview(library.get("computed:level_to_bool").source());
        threshold.setInput("level", LuaValue.valueOf(7));
        threshold.setField("threshold", LuaValue.valueOf(8));
        assertTrue(!threshold.run().outputs().get("value").toboolean());
        threshold.setField("threshold", LuaValue.valueOf(6));
        assertTrue(threshold.run().outputs().get("value").toboolean());

        LuaLivePreview delay = new LuaLivePreview(library.get("computed:delay").source());
        delay.setField("delay", LuaValue.valueOf(2));
        delay.setInput("value", LuaValue.valueOf(1));
        delay.run();
        delay.setInput("value", LuaValue.valueOf(2));
        delay.run();
        delay.setInput("value", LuaValue.valueOf(3));
        assertEquals(1, delay.run().outputs().get("delayed").toint());
    }

    @Test
    void buttonEmitsOneTickPulseForEveryClick() {
        BuiltinEndpoints.register();
        LuaLivePreview button = new LuaLivePreview(
                BundledLuaLibrary.load().get("computed:button_widget").source());

        LuaInvocationResult initial = button.run();
        assertTrue(initial.diagnostics().isEmpty(), initial.diagnostics().toString());
        assertFalse(initial.outputs().get("clicked").toboolean());

        button.event("input");
        assertTrue(button.run().outputs().get("clicked").toboolean());
        assertFalse(button.run().outputs().get("clicked").toboolean());

        button.event("input");
        assertTrue(button.run().outputs().get("clicked").toboolean());
        assertFalse(button.run().outputs().get("clicked").toboolean());
    }

    @Test
    void redstoneLinkSenderHasOptionalBooleanTrigger() {
        LuaNodeDefinition sender = load(
                IntegrationLuaLibrary.load(), "computed:create_link_sender");
        LuaPortSchema trigger = sender.inputs().stream()
                .filter(input -> input.id().equals("trigger"))
                .findFirst()
                .orElseThrow();

        assertEquals(ConnectionType.BOOLEAN, trigger.type());
        assertFalse(trigger.defaultValue().toboolean());
    }

    private static LuaNodeDefinition load(
            Map<String, dev.propulsionteam.computed.graph.LuaDefinitionSource> library,
            String id) {
        return load(library.get(id));
    }

    private static LuaNodeDefinition load(
            dev.propulsionteam.computed.graph.LuaDefinitionSource source) {
        return new LuaDefinitionLoader().load(
                new LuaSourceCompiler().compile(source.apiVersion(), source.source()),
                new LuaSandbox());
    }

    private static void assertSlider(
            LuaNodeDefinition definition,
            String id,
            double minimum,
            double maximum,
            double defaultValue) {
        LuaFieldSchema field = field(definition, id);
        assertEquals(FieldControl.SLIDER, field.control());
        assertEquals(minimum, field.minimum());
        assertEquals(maximum, field.maximum());
        assertEquals(defaultValue, field.defaultValue().todouble());
    }

    private static LuaFieldSchema field(LuaNodeDefinition definition, String id) {
        return definition.fields().stream()
                .filter(field -> field.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
