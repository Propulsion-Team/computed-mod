package dev.propulsionteam.computed.client.editor.preview;

import dev.propulsionteam.computed.client.renderer.node.NodeRenderLayout;
import dev.propulsionteam.computed.lua.runtime.LuaComputerRuntime;
import dev.propulsionteam.computed.lua.runtime.LuaInvocationResult;
import dev.propulsionteam.computed.lua.runtime.LuaNodeInstance;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.luaj.vm2.LuaValue;

public final class LuaLivePreview {
    private final UUID computerId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();
    private final Map<String, LuaValue> inputs = new LinkedHashMap<>();
    private final Map<String, LuaValue> fields = new LinkedHashMap<>();
    private String source;
    private LuaComputerRuntime runtime;
    private LuaNodeInstance node;
    private long tick;

    public LuaLivePreview(String source) {
        reset(source);
    }

    public void setInput(String id, LuaValue value) {
        inputs.put(id, value);
    }

    public void setField(String id, LuaValue value) {
        fields.put(id, value);
    }

    public LuaInvocationResult run() {
        runtime.beginTick(++tick);
        return node.run(inputs, fields, tick, runtime.nextGraphStep(), true, null);
    }

    public LuaInvocationResult event(String eventName, LuaValue... arguments) {
        runtime.beginTick(++tick);
        return node.event(
                eventName,
                List.of(arguments),
                inputs,
                fields,
                tick,
                runtime.nextGraphStep(),
                true,
                null);
    }

    public NodeRenderLayout layout() {
        return NodeRenderLayout.measure(node.definition());
    }

    public void reset() {
        reset(source);
    }

    public void reset(String source) {
        if (runtime != null) {
            runtime.unload();
        }
        this.source = source;
        runtime = new LuaComputerRuntime(computerId);
        node = runtime.createNode(nodeId, 1, source);
        inputs.clear();
        node.definition().inputs().forEach(input -> inputs.put(input.id(), input.defaultValue()));
        fields.clear();
        node.definition().fields().forEach(field -> fields.put(field.id(), field.defaultValue()));
        tick = 0;
    }
}
