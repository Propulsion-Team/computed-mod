package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class LuaDefinitionLibrary {
    private final LuaSourceCompiler compiler = new LuaSourceCompiler();
    private final LuaDefinitionLoader loader = new LuaDefinitionLoader();
    private final Map<String, LuaDefinitionSource> sources = new LinkedHashMap<>();
    private final Map<String, LuaNodeDefinition> schemas = new LinkedHashMap<>();

    public LuaDefinitionLibrary(Map<String, LuaDefinitionSource> initialDefinitions) {
        if (initialDefinitions != null) {
            initialDefinitions.values().forEach(this::addInitial);
        }
    }

    public LuaLibraryUpdate importSource(
            int apiVersion,
            String source,
            boolean confirmReplacement,
            Predicate<String> permission) {
        Objects.requireNonNull(permission, "permission");
        LuaDefinitionSource candidate = source(apiVersion, source);
        if (!permission.test(candidate.id())) {
            throw new SecurityException("Not permitted to author Lua definition " + candidate.id());
        }
        LuaDefinitionSource existing = sources.get(candidate.id());
        if (existing != null && existing.hash().equals(candidate.hash())) {
            return new LuaLibraryUpdate(
                    LuaLibraryUpdate.Status.UNCHANGED,
                    existing,
                    List.of(),
                    List.of(),
                    "Definition id and hash already exist");
        }
        if (existing != null && !confirmReplacement) {
            return new LuaLibraryUpdate(
                    LuaLibraryUpdate.Status.CONFIRMATION_REQUIRED,
                    candidate,
                    List.of(),
                    List.of(),
                    "Replacing the same id with different source requires confirmation");
        }
        if (existing == null && sources.size() >= dev.propulsionteam.computed.graph.ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS) {
            throw new IllegalArgumentException("Embedded Lua definition limit reached");
        }
        LuaNodeDefinition nextSchema = validate(candidate);
        List<String> retained = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        if (existing != null) {
            comparePorts(schemas.get(existing.id()), nextSchema, retained, removed);
        }
        sources.put(candidate.id(), candidate);
        schemas.put(candidate.id(), nextSchema);
        return new LuaLibraryUpdate(
                existing == null ? LuaLibraryUpdate.Status.ADDED : LuaLibraryUpdate.Status.REPLACED,
                candidate,
                retained,
                removed,
                existing == null ? "Definition added" : "Definition replaced");
    }

    public Map<String, LuaDefinitionSource> sources() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sources));
    }

    public LuaNodeDefinition schema(String id) {
        LuaNodeDefinition schema = schemas.get(id);
        if (schema == null) {
            throw new IllegalArgumentException("Unknown Lua definition: " + id);
        }
        return schema;
    }

    private LuaDefinitionSource source(int apiVersion, String source) {
        var compiled = compiler.compile(apiVersion, source);
        LuaNodeDefinition definition = loader.load(compiled, new LuaSandbox());
        return new LuaDefinitionSource(
                apiVersion,
                definition.id(),
                source,
                compiled.sourceHash(),
                LuaDefinitionSource.Origin.EMBEDDED);
    }

    private LuaNodeDefinition validate(LuaDefinitionSource source) {
        var compiled = compiler.compile(source.apiVersion(), source.source());
        if (!compiled.sourceHash().equals(source.hash())) {
            throw new LuaDefinitionException("Definition hash mismatch for " + source.id());
        }
        LuaNodeDefinition definition = loader.load(compiled, new LuaSandbox());
        if (!definition.id().equals(source.id())) {
            throw new LuaDefinitionException("Definition source id does not match library id");
        }
        return definition;
    }

    private void addInitial(LuaDefinitionSource source) {
        LuaNodeDefinition schema = validate(source);
        sources.put(source.id(), source);
        schemas.put(source.id(), schema);
    }

    private static void comparePorts(
            LuaNodeDefinition previous,
            LuaNodeDefinition next,
            List<String> retained,
            List<String> removed) {
        Set<String> nextPorts = portIdentities(next);
        for (String identity : portIdentities(previous)) {
            if (nextPorts.contains(identity)) {
                retained.add(identity);
            } else {
                removed.add(identity);
            }
        }
    }

    private static Set<String> portIdentities(LuaNodeDefinition definition) {
        Set<String> identities = new LinkedHashSet<>();
        definition.inputs().forEach(port -> identities.add("input:" + port.id() + ':' + port.type().name()));
        definition.outputs().forEach(port -> identities.add("output:" + port.id() + ':' + port.type().name()));
        return identities;
    }
}
