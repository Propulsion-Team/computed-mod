package dev.propulsionteam.computed.integration.computercraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ComputerCraftChannelsTest {
    @Test
    void channelsReadWritePublishAndNotifyAttachedComputers() throws Exception {
        ComputerCraftChannels.Store store = new ComputerCraftChannels.Store();
        Access access = new Access();
        store.attach(access);

        store.write("control", Map.of("value", 12));
        store.publish("status", List.of("ready", true));
        store.publish("status", List.of("ready", true));

        assertEquals(Map.of("value", 12.0), store.input("control"));
        assertEquals(Map.of(1, "ready", 2, true), store.output("status"));
        assertEquals(List.of("control", "status"), store.channels());
        assertEquals(1, access.events.size());
        assertEquals("computed_output_changed", access.events.getFirst()[0]);

        store.detach(access);
        store.publish("status", List.of("changed"));
        assertEquals(1, access.events.size());
    }

    private static final class Access implements IComputerAccess {
        private final List<Object[]> events = new ArrayList<>();

        @Override
        public String mount(String desiredLocation, Mount mount, String driveName) {
            return "";
        }

        @Override
        public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            return "";
        }

        @Override
        public void unmount(String location) {}

        @Override
        public int getID() {
            return 1;
        }

        @Override
        public void queueEvent(String event, Object... arguments) {
            Object[] captured = new Object[arguments.length + 1];
            captured[0] = event;
            System.arraycopy(arguments, 0, captured, 1, arguments.length);
            events.add(captured);
        }

        @Override
        public String getAttachmentName() {
            return "test";
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of();
        }

        @Override
        public IPeripheral getAvailablePeripheral(String name) {
            return null;
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            return new WorkMonitor() {
                @Override
                public boolean canWork() {
                    return true;
                }

                @Override
                public boolean shouldWork() {
                    return true;
                }

                @Override
                public void trackWork(long time, TimeUnit unit) {}
            };
        }
    }
}
