package dev.propulsionteam.computed.integration.computercraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

class ComputerCraftPeripheralCallTest {
    @Test
    void supportsImmediateDynamicCallsAndAttachmentLifecycle() throws Exception {
        DynamicPeripheral peripheral = new DynamicPeripheral(false);

        EndpointResult result = ComputerCraftPeripheralCall.invoke(
                null,
                "north",
                peripheral,
                "echo",
                List.of("hello"));

        EndpointResult.Immediate immediate = assertInstanceOf(EndpointResult.Immediate.class, result);
        LuaTable values = immediate.values().getFirst().checktable();
        assertEquals("hello", values.get(1).tojstring());
        assertEquals(1, peripheral.attachments);
        assertEquals(1, peripheral.detachments);
    }

    @Test
    void resumesYieldedCallsFromPeripheralEvents() throws Exception {
        DynamicPeripheral peripheral = new DynamicPeripheral(true);

        EndpointResult result = ComputerCraftPeripheralCall.invoke(
                null,
                "north",
                peripheral,
                "echo",
                List.of("waiting"));
        EndpointResult.Yielded yielded = assertInstanceOf(EndpointResult.Yielded.class, result);
        assertEquals(0, peripheral.detachments);

        peripheral.access.queueEvent("resume", "done");
        EndpointResult.Immediate resumed = yielded.continuation().toCompletableFuture().join();

        assertEquals("done", resumed.values().getFirst().checktable().get(1).tojstring());
        assertEquals(1, peripheral.detachments);
    }

    @Test
    void discoversAndCallsAnnotatedMainThreadMethods() throws Exception {
        AnnotatedPeripheral peripheral = new AnnotatedPeripheral();

        assertTrue(ComputerCraftPeripheralCall.methods(peripheral).contains("main"));
        EndpointResult result = ComputerCraftPeripheralCall.invoke(
                null,
                "north",
                peripheral,
                "main",
                List.of(3.0));

        LuaTable values = assertInstanceOf(EndpointResult.Immediate.class, result)
                .values()
                .getFirst()
                .checktable();
        assertEquals(6, values.get(1).toint());
    }

    private static final class DynamicPeripheral implements IDynamicPeripheral {
        private final boolean yielding;
        private IComputerAccess access;
        private int attachments;
        private int detachments;

        private DynamicPeripheral(boolean yielding) {
            this.yielding = yielding;
        }

        @Override
        public String getType() {
            return "test";
        }

        @Override
        public String[] getMethodNames() {
            return new String[] {"echo"};
        }

        @Override
        public MethodResult callMethod(
                IComputerAccess computer,
                ILuaContext context,
                int method,
                IArguments arguments) throws LuaException {
            if (!yielding) {
                return MethodResult.of(arguments.get(0));
            }
            return MethodResult.pullEvent("resume", event -> MethodResult.of(event[1]));
        }

        @Override
        public void attach(IComputerAccess computer) {
            access = computer;
            attachments++;
        }

        @Override
        public void detach(IComputerAccess computer) {
            detachments++;
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other == this;
        }
    }

    private static final class AnnotatedPeripheral implements IPeripheral {
        @Override
        public String getType() {
            return "annotated";
        }

        @LuaFunction(value = "main", mainThread = true)
        public final MethodResult main(double value) {
            return MethodResult.of(value * 2);
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other == this;
        }
    }
}
