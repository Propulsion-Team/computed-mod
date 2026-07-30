package dev.propulsionteam.computed.persistence;

import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Portable program package and clipboard representation.
 *
 * <p>The archive deliberately carries its graph in a JSON document, while the NBT payload preserves every
 * format-3 value (including arbitrary field and persistent-state tags) without lossy JSON coercion.
 */
public final class ComputedProgramPackage {
    public static final String CLIPBOARD_PREFIX = "COMPUTED:1:GZIP:";
    private static final int MAX_ARCHIVE_ENTRIES = 260;
    private static final int MAX_ENTRY_BYTES = 2 * 1024 * 1024;
    private static final String MANIFEST = "manifest.json";
    private static final String GRAPH = "graph.json";

    private ComputedProgramPackage() {}

    public static byte[] exportArchive(ComputedProgramV3 program) {
        try {
            ComputedProgramV3 portable = portable(program);
            byte[] nbt = encode(portable);
            String graph = "{\n  \"formatVersion\": 1,\n  \"programNbt\": \""
                    + Base64.getEncoder().encodeToString(nbt) + "\"\n}\n";
            String manifest = manifest(portable);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                write(zip, MANIFEST, manifest.getBytes(StandardCharsets.UTF_8));
                write(zip, GRAPH, graph.getBytes(StandardCharsets.UTF_8));
                for (LuaDefinitionSource source : portable.library().values()) {
                    if (source.origin() == LuaDefinitionSource.Origin.EMBEDDED) {
                        write(zip, "nodes/" + safeFileName(source.id()) + ".lua", source.source().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Computed program package", exception);
        }
    }

    public static ComputedProgramV3 importArchive(byte[] archive) {
        if (archive == null || archive.length == 0) throw new IllegalArgumentException("Package is empty");
        Map<String, byte[]> entries = readArchive(archive);
        if (!entries.containsKey(MANIFEST) || !entries.containsKey(GRAPH)) {
            throw new IllegalArgumentException("A .computed package must contain manifest.json and graph.json");
        }
        String manifest = new String(entries.get(MANIFEST), StandardCharsets.UTF_8);
        if (!manifest.contains("\"format\": \"computed-program\"") || !manifest.contains("\"formatVersion\": 1")) {
            throw new IllegalArgumentException("Unsupported Computed package manifest");
        }
        String graph = new String(entries.get(GRAPH), StandardCharsets.UTF_8);
        String encoded = jsonString(graph, "programNbt");
        ComputedProgramV3 program = decode(Base64.getDecoder().decode(encoded));
        for (LuaDefinitionSource source : program.library().values()) {
            if (source.origin() == LuaDefinitionSource.Origin.EMBEDDED && source.id().startsWith("computed:")) {
                throw new IllegalArgumentException("Packages cannot replace integrated node " + source.id());
            }
        }
        return program;
    }

    public static String exportClipboard(ComputedProgramV3 program) {
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { gzip.write(exportArchive(program)); }
            return CLIPBOARD_PREFIX + Base64.getEncoder().encodeToString(compressed.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not compress Computed program", exception);
        }
    }

    public static ComputedProgramV3 importClipboard(String text) {
        if (text == null || !text.strip().startsWith(CLIPBOARD_PREFIX)) {
            throw new IllegalArgumentException("Clipboard does not contain a Computed program");
        }
        try {
            byte[] compressed = Base64.getDecoder().decode(text.strip().substring(CLIPBOARD_PREFIX.length()));
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                return importArchive(gzip.readAllBytes());
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Computed program clipboard data", exception);
        }
    }

    public static ComputedProgramV3 copySelection(
            ComputedProgramV3 program,
            Set<UUID> selectedNodeIds) {
        Set<UUID> selected = selectedNodeIds == null ? Set.of() : Set.copyOf(selectedNodeIds);
        List<GraphNode> nodes = program.rootGraph().nodes().stream()
                .filter(node -> selected.contains(node.id()))
                .toList();
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes are selected");
        }
        List<GraphConnection> connections = program.rootGraph().connections().stream()
                .filter(connection -> selected.contains(connection.sourceNode())
                        && selected.contains(connection.targetNode()))
                .toList();
        Set<String> definitionIds = nodes.stream()
                .map(GraphNode::definitionId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, LuaDefinitionSource> library = new LinkedHashMap<>();
        program.library().forEach((id, source) -> {
            if (definitionIds.contains(id) && source.origin() == LuaDefinitionSource.Origin.EMBEDDED) {
                library.put(id, source);
            }
        });
        Map<UUID, CompoundTag> state = new LinkedHashMap<>();
        program.persistentState().forEach((id, value) -> {
            if (selected.contains(id)) {
                state.put(id, value);
            }
        });
        return new ComputedProgramV3(
                0,
                new ComputedGraph(UUID.randomUUID(), nodes, connections),
                library,
                state,
                new CompoundTag());
    }

    public static PasteResult pasteSelection(
            ComputedProgramV3 target,
            ComputedProgramV3 fragment,
            int anchorX,
            int anchorY) {
        if (fragment.rootGraph().nodes().isEmpty()) {
            throw new IllegalArgumentException("Clipboard does not contain any nodes");
        }
        Map<String, LuaDefinitionSource> library = new LinkedHashMap<>(target.library());
        fragment.library().forEach((id, source) -> {
            LuaDefinitionSource existing = library.get(id);
            if (existing != null && !existing.hash().equals(source.hash())) {
                throw new IllegalArgumentException("Clipboard definition conflicts with " + id);
            }
            if (existing == null) {
                library.put(id, source);
            }
        });

        int minimumX = fragment.rootGraph().nodes().stream().mapToInt(GraphNode::x).min().orElse(0);
        int minimumY = fragment.rootGraph().nodes().stream().mapToInt(GraphNode::y).min().orElse(0);
        int deltaX = anchorX - minimumX;
        int deltaY = anchorY - minimumY;
        Map<UUID, UUID> replacements = new HashMap<>();
        fragment.rootGraph().nodes().forEach(node -> replacements.put(node.id(), UUID.randomUUID()));

        List<GraphNode> nodes = new ArrayList<>(target.rootGraph().nodes());
        fragment.rootGraph().nodes().forEach(node -> nodes.add(new GraphNode(
                replacements.get(node.id()),
                node.definitionId(),
                node.definitionHash(),
                node.x() + deltaX,
                node.y() + deltaY,
                node.ports(),
                node.fields())));

        List<GraphConnection> connections = new ArrayList<>(target.rootGraph().connections());
        for (GraphConnection connection : fragment.rootGraph().connections()) {
            UUID sourceNode = replacements.get(connection.sourceNode());
            UUID targetNode = replacements.get(connection.targetNode());
            if (sourceNode == null || targetNode == null) {
                throw new IllegalArgumentException("Clipboard contains a connection outside its node selection");
            }
            connections.add(new GraphConnection(
                    UUID.randomUUID(),
                    sourceNode,
                    connection.sourcePort(),
                    targetNode,
                    connection.targetPort(),
                    connection.waypoints().stream()
                            .map(point -> new GraphPoint(point.x() + deltaX, point.y() + deltaY))
                            .toList()));
        }

        Map<UUID, CompoundTag> state = new LinkedHashMap<>(target.persistentState());
        fragment.persistentState().forEach((id, value) -> {
            UUID replacement = replacements.get(id);
            if (replacement != null) {
                state.put(replacement, value);
            }
        });
        ComputedProgramV3 program = new ComputedProgramV3(
                target.revision(),
                new ComputedGraph(target.rootGraph().id(), nodes, connections),
                library,
                state,
                target.metadata());
        return new PasteResult(program, Set.copyOf(replacements.values()));
    }

    public record PasteResult(ComputedProgramV3 program, Set<UUID> pastedNodeIds) {}

    private static byte[] encode(ComputedProgramV3 program) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.write(ProgramV3Codec.encode(program), new DataOutputStream(output));
        return output.toByteArray();
    }

    private static ComputedProgramV3 decode(byte[] bytes) {
        try {
            CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
            return ProgramV3Codec.decodeV3(tag);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid graph payload", exception);
        }
    }

    private static Map<String, byte[]> readArchive(byte[] archive) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            int entryCount = 0;
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (++entryCount > MAX_ARCHIVE_ENTRIES || !safePath(name) || entries.containsKey(name)) {
                    throw new IllegalArgumentException("Unsafe or excessive package entries");
                }
                byte[] content = zip.readNBytes(MAX_ENTRY_BYTES + 1);
                if (content.length > MAX_ENTRY_BYTES) throw new IllegalArgumentException("Package entry is too large: " + name);
                entries.put(name, content);
            }
        } catch (IOException exception) { throw new IllegalArgumentException("Invalid .computed ZIP archive", exception); }
        return entries;
    }

    private static String manifest(ComputedProgramV3 program) {
        String nodes = program.library().values().stream()
                .filter(source -> source.origin() == LuaDefinitionSource.Origin.EMBEDDED)
                .map(source -> "{\"id\":\"" + escape(source.id()) + "\",\"nodeVersion\":" + source.apiVersion()
                        + ",\"file\":\"nodes/" + safeFileName(source.id()) + ".lua\",\"sourceHash\":\"sha256:" + source.hash() + "\"}")
                .reduce((a, b) -> a + "," + b).orElse("");
        String integrated = program.rootGraph().nodes().stream()
                .map(node -> node.definitionId())
                .filter(id -> !program.library().containsKey(id))
                .distinct()
                .map(id -> "{\"id\":\"" + escape(id) + "\",\"nodeVersion\":1}")
                .reduce((a, b) -> a + "," + b).orElse("");
        return "{\n  \"format\": \"computed-program\",\n  \"formatVersion\": 1,\n  \"computedApiVersion\": 1,\n  \"graph\": \"graph.json\",\n  \"customNodes\": [" + nodes + "],\n  \"integratedNodes\": [" + integrated + "]\n}\n";
    }

    /** Packages only custom definitions actually referenced by the graph. */
    private static ComputedProgramV3 portable(ComputedProgramV3 program) {
        Set<String> usedIds = program.rootGraph().nodes().stream()
                .map(node -> node.definitionId())
                .collect(java.util.stream.Collectors.toSet());
        Map<String, LuaDefinitionSource> usedCustom = new LinkedHashMap<>();
        program.library().forEach((id, source) -> {
            if (usedIds.contains(id) && source.origin() == LuaDefinitionSource.Origin.EMBEDDED) {
                usedCustom.put(id, source);
            }
        });
        return new ComputedProgramV3(
                program.revision(), program.rootGraph(), usedCustom, program.persistentState(), program.metadata());
    }

    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(data); zip.closeEntry();
    }
    private static boolean safePath(String name) { return !name.startsWith("/") && !name.contains("\\") && !name.contains("../") && !name.contains(":"); }
    private static String safeFileName(String id) { return id.replace(':', '_').replaceAll("[^a-zA-Z0-9_.-]", "_"); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String jsonString(String json, String key) {
        String marker = "\"" + key + "\""; int keyAt = json.indexOf(marker); int quote = keyAt < 0 ? -1 : json.indexOf('\"', json.indexOf(':', keyAt) + 1);
        int end = quote < 0 ? -1 : json.indexOf('\"', quote + 1);
        if (end < 0) throw new IllegalArgumentException("graph.json is missing " + key);
        return json.substring(quote + 1, end);
    }
}
