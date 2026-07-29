package dev.propulsionteam.computed.lua.endpoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class BuiltinEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private BuiltinEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("computed:world", endpoint -> endpoint.method(
                        "time",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(
                                LuaValue.valueOf(requireHost(invocation).worldTime())),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(6000)),
                        "Returns the current world day time.")
                .method(
                        "position",
                        EndpointSignature.of(
                                List.of(),
                                List.of(EndpointType.NUMBER, EndpointType.NUMBER, EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> numbers(requireHost(invocation).position()),
                        invocation -> numbers(new double[] {0.5, 64.5, 0.5}),
                        "Returns the computer world position.")
                .method(
                        "rotation",
                        EndpointSignature.of(
                                List.of(),
                                List.of(EndpointType.NUMBER, EndpointType.NUMBER, EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> numbers(requireHost(invocation).rotation()),
                        invocation -> numbers(new double[] {0, 0, 0}),
                        "Returns the computer rotation in degrees.")
                .method(
                        "block_present",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireHost(invocation).blockPresent(argument(invocation, 0)))),
                        invocation -> EndpointResult.immediate(LuaValue.FALSE),
                        "Reports whether a block is present at a relative face."));
        ComputedEndpoints.register("computed:redstone", endpoint -> endpoint.method(
                        "input",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireHost(invocation).redstoneInput(argument(invocation, 0)))),
                        invocation -> EndpointResult.immediate(LuaValue.ZERO),
                        "Reads weak redstone power from a relative face.")
                .method(
                        "comparator",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireHost(invocation).comparatorInput(argument(invocation, 0)))),
                        invocation -> EndpointResult.immediate(LuaValue.ZERO),
                        "Reads comparator power from a relative face.")
                .method(
                        "output",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        invocation -> {
                            requireHost(invocation).redstoneOutput(
                                    argument(invocation, 0),
                                    invocation.arguments().get(1).checkint());
                            return EndpointResult.immediate();
                        },
                        null,
                        "Writes weak redstone power to a relative face."));
        ComputedEndpoints.register("computed:command", endpoint -> endpoint.method(
                "run",
                EndpointSignature.of(List.of(EndpointType.STRING), List.of()),
                EndpointPolicy.serverThread(true, false),
                invocation -> {
                    BuiltinEndpointHost host = requireHost(invocation);
                    host.runCommand(invocation.arguments().getFirst().tojstring());
                    return EndpointResult.immediate();
                },
                null,
                "Runs a command through the computer host."));
        ComputedEndpoints.register("computed:widget", endpoint -> endpoint.method(
                        "text",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "text",
                                Map.of(
                                        "text", argument(invocation, 0),
                                        "color", invocation.arguments().get(1).checkint(),
                                        "alignment", "left"))),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "text",
                                Map.of(
                                        "text", argument(invocation, 0),
                                        "color", invocation.arguments().get(1).checkint(),
                                        "alignment", "left"))),
                        "Creates a text widget value.")
                .method(
                        "clock",
                        EndpointSignature.of(
                                List.of(EndpointType.NUMBER, EndpointType.BOOLEAN),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(clockWidget(invocation)),
                        invocation -> EndpointResult.immediate(clockWidget(invocation)),
                        "Creates a clock widget value.")
                .method(
                        "button",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(buttonWidget(invocation)),
                        invocation -> EndpointResult.immediate(buttonWidget(invocation)),
                        "Creates a button widget value.")
                .method(
                        "slider",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(sliderWidget(invocation)),
                        invocation -> EndpointResult.immediate(sliderWidget(invocation)),
                        "Creates a slider widget value.")
                .method(
                        "progress",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(progressWidget(invocation)),
                        invocation -> EndpointResult.immediate(progressWidget(invocation)),
                        "Creates a progress bar widget value."));
        ComputedEndpoints.register("computed:monitor", endpoint -> endpoint.method(
                "show",
                EndpointSignature.of(List.of(EndpointType.TABLE), List.of()),
                EndpointPolicy.serverThread(true, false),
                invocation -> {
                    requireHost(invocation).showWidgets(
                            invocation.target(),
                            parseWidgets(invocation.arguments().getFirst().checktable(), invocation.nodeId()));
                    return EndpointResult.immediate();
                },
                null,
                "Shows widget values on the monitor at the endpoint target."));
    }

    private static BuiltinEndpointHost requireHost(EndpointInvocation invocation) {
        if (invocation.host() instanceof BuiltinEndpointHost host) {
            return host;
        }
        throw new IllegalStateException("Computer host does not provide built-in endpoint access");
    }

    private static LuaTable widget(UUID nodeId, String type, Map<String, Object> properties) {
        LuaTable widget = new LuaTable();
        widget.set("id", nodeId.toString());
        widget.set("type", type);
        widget.set("x", 0);
        widget.set("y", 0);
        widget.set("width", 64);
        widget.set("height", 16);
        properties.forEach((key, value) -> widget.set(key, toLua(value)));
        return widget;
    }

    private static LuaTable clockWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "clock", Map.of(
                "color", invocation.arguments().get(0).checkint(),
                "show_seconds", invocation.arguments().get(1).checkboolean()));
    }

    private static LuaTable buttonWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "button", Map.of(
                "label", argument(invocation, 0),
                "color", invocation.arguments().get(1).checkint()));
    }

    private static LuaTable sliderWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "slider", Map.of(
                "value", invocation.arguments().get(0).checkdouble(),
                "minimum", invocation.arguments().get(1).checkdouble(),
                "maximum", invocation.arguments().get(2).checkdouble(),
                "color", invocation.arguments().get(3).checkint(),
                "step", invocation.arguments().get(4).checkdouble()));
    }

    private static LuaTable progressWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "progress", Map.of(
                "value", invocation.arguments().get(0).checkdouble(),
                "maximum", invocation.arguments().get(1).checkdouble(),
                "color", invocation.arguments().get(2).checkint(),
                "segments", invocation.arguments().get(3).checkint()));
    }

    private static List<BuiltinWidget> parseWidgets(LuaTable table, UUID fallbackId) {
        java.util.ArrayList<BuiltinWidget> widgets = new java.util.ArrayList<>();
        if (!table.get("type").isnil()) {
            widgets.add(parseWidget(table, fallbackId));
            return List.copyOf(widgets);
        }
        int count = Math.min(64, table.length());
        for (int index = 1; index <= count; index++) {
            LuaValue value = table.get(index);
            if (value.istable() && !value.get("type").isnil()) {
                widgets.add(parseWidget(value.checktable(), fallbackId));
            }
        }
        return List.copyOf(widgets);
    }

    private static BuiltinWidget parseWidget(LuaTable table, UUID fallbackId) {
        UUID id;
        try {
            id = UUID.fromString(table.get("id").optjstring(fallbackId.toString()));
        } catch (IllegalArgumentException exception) {
            id = fallbackId;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        copyText(table, properties, "text", "");
        copyText(table, properties, "label", "");
        copyText(table, properties, "alignment", "left");
        copyText(table, properties, "layout_mode", "line");
        copyText(table, properties, "fit", "auto");
        copyNumber(table, properties, "line", 1);
        copyNumber(table, properties, "span", 1);
        copyNumber(table, properties, "value", 0);
        copyNumber(table, properties, "minimum", 0);
        copyNumber(table, properties, "maximum", 1);
        copyNumber(table, properties, "step", 1);
        copyNumber(table, properties, "segments", 0);
        properties.put("show_seconds", table.get("show_seconds").optboolean(false));
        return new BuiltinWidget(
                id,
                table.get("type").checkjstring(),
                table.get("x").optint(0),
                table.get("y").optint(0),
                Math.max(1, table.get("width").optint(64)),
                Math.max(1, table.get("height").optint(16)),
                table.get("color").optint(0xFFFFFFFF),
                properties);
    }

    private static void copyText(
            LuaTable table,
            Map<String, Object> properties,
            String key,
            String fallback) {
        properties.put(key, table.get(key).optjstring(fallback));
    }

    private static void copyNumber(
            LuaTable table,
            Map<String, Object> properties,
            String key,
            double fallback) {
        properties.put(key, table.get(key).optdouble(fallback));
    }

    private static LuaValue toLua(Object value) {
        return switch (value) {
            case String text -> LuaValue.valueOf(text);
            case Boolean flag -> LuaValue.valueOf(flag);
            case Integer number -> LuaValue.valueOf(number);
            case Double number -> LuaValue.valueOf(number);
            default -> LuaValue.NIL;
        };
    }

    private static String argument(EndpointInvocation invocation, int index) {
        return invocation.arguments().get(index).checkjstring();
    }

    private static EndpointResult.Immediate numbers(double[] values) {
        return EndpointResult.immediate(
                LuaValue.valueOf(values[0]),
                LuaValue.valueOf(values[1]),
                LuaValue.valueOf(values[2]));
    }
}
