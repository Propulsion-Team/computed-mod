package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.IntegrationLuaLibrary;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.LuaFieldValues;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ComputerEditPolicy {
    public static final double MAX_DISTANCE_SQ = 16.0 * 16.0;
    public static final int MAX_NODES = 4096;
    public static final int MAX_CONNECTIONS = 20_000;
    public static final int MAX_PROGRAM_BYTES = 4 * 1024 * 1024;

    private ComputerEditPolicy() {}

    public static String access(double distanceSquared, boolean mayBuild, boolean mayInteract) {
        if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_DISTANCE_SQ) {
            return "computer is too far away";
        }
        if (!mayBuild || !mayInteract) {
            return "you do not have permission to edit this computer";
        }
        return null;
    }

    public static String revision(long authoritative, long expected) {
        return authoritative == expected
                ? null
                : "stale editor revision (expected " + authoritative + ", received " + expected + ")";
    }

    public static String encodedSize(int bytes) {
        if (bytes < 0) {
            return "program NBT could not be measured safely";
        }
        return bytes > MAX_PROGRAM_BYTES
                ? "program exceeds the encoded size limit of " + MAX_PROGRAM_BYTES + " bytes"
                : null;
    }

    public static String programShape(ComputedProgramV3 candidate) {
        if (candidate.rootGraph().nodes().size() > MAX_NODES) {
            return "program exceeds the node limit of " + MAX_NODES;
        }
        if (candidate.rootGraph().connections().size() > MAX_CONNECTIONS) {
            return "program exceeds the connection limit of " + MAX_CONNECTIONS;
        }
        if (candidate.library().size() > ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS) {
            return "program exceeds the embedded definition limit of "
                    + ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS;
        }
        return fieldValues(candidate);
    }

    private static String fieldValues(ComputedProgramV3 candidate) {
        Map<String, LuaDefinitionSource> sources = new LinkedHashMap<>(BundledLuaLibrary.load());
        sources.putAll(IntegrationLuaLibrary.load());
        sources.putAll(candidate.library());
        Map<String, LuaNodeDefinition> definitions = new LinkedHashMap<>();
        Set<String> attemptedDefinitions = new LinkedHashSet<>();
        LuaSourceCompiler compiler = new LuaSourceCompiler();
        LuaDefinitionLoader loader = new LuaDefinitionLoader();
        LuaSandbox sandbox = new LuaSandbox();
        for (var node : candidate.rootGraph().nodes()) {
            String definitionId = node.definitionId();
            LuaDefinitionSource source = sources.get(definitionId);
            if (source == null || !attemptedDefinitions.add(definitionId)) {
                continue;
            }
            try {
                definitions.put(
                        definitionId,
                        loader.load(compiler.compile(source.apiVersion(), source.source()), sandbox));
            } catch (RuntimeException ignored) {
            }
        }
        LuaStateCodec codec = new LuaStateCodec();
        for (var node : candidate.rootGraph().nodes()) {
            LuaNodeDefinition definition = definitions.get(node.definitionId());
            if (definition == null) {
                continue;
            }
            Map<String, dev.propulsionteam.computed.lua.node.LuaFieldSchema> schemas =
                    new LinkedHashMap<>();
            definition.fields().forEach(field -> schemas.put(field.id(), field));
            for (String id : node.fields().keySet()) {
                if (!schemas.containsKey(id)) {
                    return "node " + node.id() + " contains undeclared field " + id;
                }
            }
            for (var field : definition.fields()) {
                var encoded = node.fields().get(field.id());
                if (encoded == null) {
                    continue;
                }
                try {
                    String error = LuaFieldValues.validationError(field, codec.decode(encoded));
                    if (error != null) {
                        return "node " + node.id() + ' ' + error;
                    }
                } catch (RuntimeException exception) {
                    return "node " + node.id() + " field " + field.id()
                            + " could not be decoded: " + exception.getMessage();
                }
            }
        }
        return null;
    }
}
