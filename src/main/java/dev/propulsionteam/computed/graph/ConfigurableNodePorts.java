package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-instance port rules for bundled nodes whose payload width is authored in the graph editor.
 * Stable numbered IDs deliberately survive label/layout changes and only the last port is removed.
 */
public final class ConfigurableNodePorts {
    public static final String EVENT_SENDER = "computed:event_sender";
    public static final String EVENT_RECEIVER = "computed:event_receiver";
    public static final String MONITOR = "computed:peripheral";
    public static final int MAX_PORTS = 16;

    private static final List<ConnectionType> PAYLOAD_TYPES =
            List.of(ConnectionType.NUMBER, ConnectionType.BOOLEAN, ConnectionType.STRING,
                    ConnectionType.TABLE, ConnectionType.WIDGET);

    private ConfigurableNodePorts() {}

    public static boolean configurable(String definitionId) {
        return EVENT_SENDER.equals(definitionId)
                || EVENT_RECEIVER.equals(definitionId)
                || MONITOR.equals(definitionId);
    }

    public static List<PortSnapshot> withInitialPort(
            String definitionId, List<PortSnapshot> staticPorts) {
        if (!configurable(definitionId)) {
            return List.copyOf(staticPorts);
        }
        List<PortSnapshot> ports = new ArrayList<>(staticPorts);
        if (dynamicPorts(definitionId, ports).isEmpty()) {
            ports.add(newPort(definitionId, 1, defaultType(definitionId)));
        }
        return List.copyOf(ports);
    }

    public static List<PortSnapshot> dynamicPorts(
            String definitionId, List<PortSnapshot> ports) {
        String prefix = prefix(definitionId);
        PortDirection direction = direction(definitionId);
        if (prefix == null || direction == null) {
            return List.of();
        }
        return ports.stream()
                .filter(port -> port.direction() == direction && port.id().startsWith(prefix))
                .toList();
    }

    public static List<PortSnapshot> add(String definitionId, List<PortSnapshot> current) {
        List<PortSnapshot> dynamic = dynamicPorts(definitionId, current);
        if (dynamic.size() >= MAX_PORTS) {
            return List.copyOf(current);
        }
        int next = dynamic.stream()
                .mapToInt(port -> suffix(port.id(), prefix(definitionId)))
                .max()
                .orElse(0) + 1;
        ConnectionType type = MONITOR.equals(definitionId)
                ? ConnectionType.WIDGET
                : dynamic.isEmpty() ? defaultType(definitionId) : dynamic.getLast().type();
        List<PortSnapshot> updated = new ArrayList<>(current);
        updated.add(newPort(definitionId, next, type));
        return List.copyOf(updated);
    }

    public static List<PortSnapshot> removeLast(String definitionId, List<PortSnapshot> current) {
        List<PortSnapshot> dynamic = dynamicPorts(definitionId, current);
        if (dynamic.size() <= 1) {
            return List.copyOf(current);
        }
        PortSnapshot removed = dynamic.getLast();
        List<PortSnapshot> updated = new ArrayList<>(current);
        updated.remove(removed);
        return List.copyOf(updated);
    }

    public static List<PortSnapshot> cycleLastType(
            String definitionId, List<PortSnapshot> current) {
        if (MONITOR.equals(definitionId)) {
            return List.copyOf(current);
        }
        List<PortSnapshot> dynamic = dynamicPorts(definitionId, current);
        if (dynamic.isEmpty()) {
            return List.copyOf(current);
        }
        PortSnapshot selected = dynamic.getLast();
        int typeIndex = PAYLOAD_TYPES.indexOf(selected.type());
        ConnectionType next = PAYLOAD_TYPES.get((Math.max(0, typeIndex) + 1) % PAYLOAD_TYPES.size());
        PortSnapshot replacement = new PortSnapshot(
                selected.id(), selected.direction(), next, selected.label());
        List<PortSnapshot> updated = new ArrayList<>(current);
        updated.set(updated.indexOf(selected), replacement);
        return List.copyOf(updated);
    }

    private static PortSnapshot newPort(String definitionId, int index, ConnectionType type) {
        String id = prefix(definitionId) + index;
        return new PortSnapshot(id, direction(definitionId), type, label(definitionId, index));
    }

    private static String prefix(String definitionId) {
        return MONITOR.equals(definitionId) ? "widget_"
                : configurable(definitionId) ? "data_"
                : null;
    }

    private static PortDirection direction(String definitionId) {
        return EVENT_RECEIVER.equals(definitionId) ? PortDirection.OUTPUT
                : configurable(definitionId) ? PortDirection.INPUT
                : null;
    }

    private static ConnectionType defaultType(String definitionId) {
        return MONITOR.equals(definitionId) ? ConnectionType.WIDGET : ConnectionType.NUMBER;
    }

    private static String label(String definitionId, int index) {
        return MONITOR.equals(definitionId) ? "Widget " + index : "Data " + index;
    }

    private static int suffix(String id, String prefix) {
        try {
            return Integer.parseInt(id.substring(prefix.length()));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
