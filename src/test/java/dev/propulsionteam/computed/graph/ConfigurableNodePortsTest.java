package dev.propulsionteam.computed.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurableNodePortsTest {
    @Test
    void keepsStableIdsWhileAddingRemovingAndTypingPayloadPorts() {
        List<PortSnapshot> ports = ConfigurableNodePorts.withInitialPort(
                ConfigurableNodePorts.EVENT_SENDER,
                List.of(new PortSnapshot(
                        "trigger", PortDirection.INPUT, ConnectionType.BOOLEAN, "trigger")));

        assertEquals("data_1", ports.getLast().id());
        ports = ConfigurableNodePorts.cycleLastType(
                ConfigurableNodePorts.EVENT_SENDER, ports);
        assertEquals(ConnectionType.BOOLEAN, ports.getLast().type());
        ports = ConfigurableNodePorts.add(ConfigurableNodePorts.EVENT_SENDER, ports);
        assertEquals("data_2", ports.getLast().id());
        assertEquals(ConnectionType.BOOLEAN, ports.getLast().type());
        ports = ConfigurableNodePorts.removeLast(
                ConfigurableNodePorts.EVENT_SENDER, ports);
        assertEquals(List.of("data_1"), ConfigurableNodePorts.dynamicPorts(
                ConfigurableNodePorts.EVENT_SENDER, ports).stream()
                .map(PortSnapshot::id)
                .toList());
    }

    @Test
    void monitorPortsAreWidgetInputs() {
        List<PortSnapshot> ports =
                ConfigurableNodePorts.withInitialPort(ConfigurableNodePorts.MONITOR, List.of());
        ports = ConfigurableNodePorts.add(ConfigurableNodePorts.MONITOR, ports);

        assertEquals(List.of("widget_1", "widget_2"), ports.stream()
                .map(PortSnapshot::id)
                .toList());
        assertEquals(List.of(ConnectionType.WIDGET, ConnectionType.WIDGET), ports.stream()
                .map(PortSnapshot::type)
                .toList());
        assertEquals(List.of(PortDirection.INPUT, PortDirection.INPUT), ports.stream()
                .map(PortSnapshot::direction)
                .toList());
    }
}
