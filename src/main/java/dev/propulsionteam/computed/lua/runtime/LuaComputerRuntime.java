package dev.propulsionteam.computed.lua.runtime;

import dev.propulsionteam.computed.lua.compiler.LuaCompiledSource;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LuaComputerRuntime {
    private final UUID computerId;
    private final LuaSourceCompiler compiler = new LuaSourceCompiler();
    private final LuaDefinitionLoader definitionLoader = new LuaDefinitionLoader();
    private final LuaInstructionBudget budget;
    private final LuaSandbox sandbox;
    private final Object endpointHost;
    private final Map<UUID, LuaNodeInstance> instances = new LinkedHashMap<>();
    private long tick;
    private long graphStep;

    public LuaComputerRuntime(UUID computerId) {
        this(computerId, new LuaInstructionBudget(), null);
    }

    public LuaComputerRuntime(UUID computerId, LuaInstructionBudget budget) {
        this(computerId, budget, null);
    }

    public LuaComputerRuntime(UUID computerId, LuaInstructionBudget budget, Object endpointHost) {
        this.computerId = Objects.requireNonNull(computerId, "computerId");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.endpointHost = endpointHost;
        sandbox = new LuaSandbox(budget);
    }

    public LuaNodeInstance createNode(UUID nodeId, int apiVersion, String source) {
        Objects.requireNonNull(nodeId, "nodeId");
        LuaCompiledSource compiled = compiler.compile(apiVersion, source);
        LuaNodeDefinition definition = definitionLoader.load(compiled, sandbox);
        LuaNodeInstance instance = new LuaNodeInstance(computerId, nodeId, sandbox, definition, endpointHost);
        LuaNodeInstance previous = instances.putIfAbsent(nodeId, instance);
        if (previous != null) {
            throw new IllegalStateException("Lua node instance is already registered: " + nodeId);
        }
        return instance;
    }

    public LuaNodeInstance replaceNode(UUID nodeId, int apiVersion, String source) {
        LuaNodeInstance previous = instances.remove(nodeId);
        if (previous != null) {
            previous.cancelYield();
        }
        return createNode(nodeId, apiVersion, source);
    }

    public void removeNode(UUID nodeId) {
        LuaNodeInstance instance = instances.remove(nodeId);
        if (instance != null) {
            instance.cancelYield();
        }
    }

    public Optional<LuaNodeInstance> node(UUID nodeId) {
        return Optional.ofNullable(instances.get(nodeId));
    }

    public void beginTick(long tick) {
        this.tick = Math.max(0, tick);
        graphStep = 0;
        budget.beginTick();
    }

    public long nextGraphStep() {
        return ++graphStep;
    }

    public long graphStep() {
        return graphStep;
    }

    public long tick() {
        return tick;
    }

    public LuaSandbox sandbox() {
        return sandbox;
    }

    public void unload() {
        instances.values().forEach(LuaNodeInstance::cancelYield);
        instances.clear();
    }
}
