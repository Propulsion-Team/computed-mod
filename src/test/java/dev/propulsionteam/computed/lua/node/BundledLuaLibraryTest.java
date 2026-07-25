package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.runtime.LuaComputerRuntime;
import dev.propulsionteam.computed.lua.runtime.LuaNodeStatus;
import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BundledLuaLibraryTest {
    @Test
    void everyBundledDefinitionCompilesAndReturnsItsRegisteredId() {
        var compiler = new LuaSourceCompiler();
        var loader = new LuaDefinitionLoader();
        var sandbox = new LuaSandbox(new LuaInstructionBudget());
        var definitions = BundledLuaLibrary.load();

        assertTrue(definitions.size() >= 30);
        definitions.forEach((id, source) -> {
            var compiled = compiler.compile(source.apiVersion(), source.source());
            var definition = loader.load(compiled, sandbox);
            assertEquals(id, definition.id());
        });
    }

    @Test
    void everyBundledDefinitionRunsWithItsDefaults() {
        BuiltinEndpoints.register();
        var host = new BuiltinEndpointHost() {
            @Override
            public double worldTime() {
                return 6000;
            }

            @Override
            public double[] position() {
                return new double[] {0.5, 64.5, 0.5};
            }

            @Override
            public double[] rotation() {
                return new double[] {0, 0, 0};
            }

            @Override
            public int redstoneInput(String face) {
                return 0;
            }

            @Override
            public int comparatorInput(String face) {
                return 0;
            }

            @Override
            public boolean blockPresent(String face) {
                return false;
            }

            @Override
            public void redstoneOutput(String face, int level) {}

            @Override
            public void runCommand(String command) {}
        };
        var runtime = new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        runtime.beginTick(1);

        BundledLuaLibrary.load().forEach((id, source) -> {
            var instance = runtime.createNode(UUID.randomUUID(), source.apiVersion(), source.source());
            var inputs = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
            instance.definition().inputs().forEach(port -> inputs.put(port.id(), port.defaultValue()));
            var fields = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
            instance.definition().fields().forEach(field -> fields.put(field.id(), field.defaultValue()));
            var result = instance.run(inputs, fields, 1, runtime.nextGraphStep(), false, (name, values) -> {});
            assertEquals(LuaNodeStatus.IDLE, result.status(), id + ": " + result.diagnostics());
            assertEquals(List.of(), result.diagnostics(), id);
        });
    }
}
