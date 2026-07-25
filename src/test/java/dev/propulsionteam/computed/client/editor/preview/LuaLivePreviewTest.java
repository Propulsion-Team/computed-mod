package dev.propulsionteam.computed.client.editor.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.runtime.LuaNodeStatus;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class LuaLivePreviewTest {
    @Test
    void supportsSampleInputsEventsAndResettableState() {
        LuaLivePreview preview = new LuaLivePreview("""
                local node = computed.node(1, "example:preview_state", "Preview State")
                node:input("step", "number", { default = 1 })
                node:output("value", "number")
                node:state("value", 0)
                node:on_run(function(ctx)
                    local value = ctx:state("value") + ctx:input("step")
                    ctx:set_state("value", value)
                    ctx:output("value", value)
                end)
                node:on_event("reset", function(ctx, value)
                    ctx:set_state("value", value)
                    ctx:output("value", value)
                end)
                return node
                """);
        preview.setInput("step", LuaValue.valueOf(3));

        assertEquals(3, preview.run().outputs().get("value").toint());
        assertEquals(6, preview.run().outputs().get("value").toint());
        assertEquals(2, preview.event("reset", LuaValue.valueOf(2)).outputs().get("value").toint());

        preview.reset();
        preview.setInput("step", LuaValue.valueOf(3));
        assertEquals(3, preview.run().outputs().get("value").toint());
    }

    @Test
    void blocksSideEffectingEndpointsWithoutCallingAProductionHost() {
        BuiltinEndpoints.register();
        LuaLivePreview preview = new LuaLivePreview("""
                local node = computed.node(1, "example:preview_side_effect", "Preview Side Effect")
                node:on_run(function(ctx)
                    ctx:endpoint("computed:command"):call("run", "say blocked")
                end)
                return node
                """);

        var result = preview.run();

        assertEquals(LuaNodeStatus.FAILED, result.status());
        assertFalse(result.diagnostics().isEmpty());
    }
}
