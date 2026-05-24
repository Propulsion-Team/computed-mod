package dev.propulsionteam.computed.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.FunctionDefinitionStore;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.propulsionteam.computed.customnodes.ComputedCustomNodes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

/**
 * Portable graph share strings for Computed editor.
 *
 * Format: {@code CMP1:<urlsafe-base64(gzip-nbt)>}
 */
public final class ComputedGraphShareCodec {
    public static final String PREFIX = "CMP1:";
    private static final int FORMAT_VERSION = 1;

    private ComputedGraphShareCodec() {}

    public record Decoded(CompoundTag graph, ListTag functions, int embeddedCustomNodeCount, boolean legacy) {}

    public static String encode(WGraph graph, FunctionDefinitionStore functionStore) {
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", FORMAT_VERSION);
        root.put("graph", graph.save().copy());
        ListTag functions = functionStore != null ? functionStore.saveList() : new ListTag();
        root.put("functions", functions.copy());

        List<String> embedded = collectEmbeddedCustomNodeDefinitions(graph, functions);
        if (!embedded.isEmpty()) {
            ListTag defs = new ListTag();
            for (String raw : embedded) {
                defs.add(net.minecraft.nbt.StringTag.valueOf(raw));
            }
            root.put("embeddedCustomNodes", defs);
        }

        try {
            byte[] compressed = writeCompressed(root);
            String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
            return PREFIX + b64;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode graph", e);
        }
    }

    public static Decoded decode(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Empty import string");
        }

        if (trimmed.startsWith(PREFIX)) {
            String payload = trimmed.substring(PREFIX.length());
            try {
                byte[] compressed = Base64.getUrlDecoder().decode(payload);
                CompoundTag root = readCompressed(compressed);
                int version = root.getInt("formatVersion");
                if (version != FORMAT_VERSION) {
                    throw new IllegalArgumentException("Unsupported share version: " + version);
                }
                List<String> defs = new ArrayList<>();
                ListTag defsTag = root.getList("embeddedCustomNodes", Tag.TAG_STRING);
                for (int i = 0; i < defsTag.size(); i++) {
                    defs.add(defsTag.getString(i));
                }
                if (!defs.isEmpty()) {
                    ComputedCustomNodes.applyServerDefinitions(defs);
                }
                CompoundTag graphTag = root.getCompound("graph").copy();
                ListTag functions = root.getList("functions", Tag.TAG_COMPOUND).copy();
                return new Decoded(graphTag, functions, defs.size(), false);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid compressed share string", e);
            }
        }

        // Legacy fallback: Base64-encoded SNBT text.
        try {
            String decoded = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
            CompoundTag root = TagParser.parseTag(decoded);
            if (root.contains("nodes", Tag.TAG_LIST)) {
                return new Decoded(root.copy(), new ListTag(), 0, true);
            }
            if (root.contains("graph", Tag.TAG_COMPOUND)) {
                return new Decoded(root.getCompound("graph").copy(), root.getList("functions", Tag.TAG_COMPOUND).copy(), 0, true);
            }
            throw new IllegalArgumentException("Legacy payload missing graph data");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid import string", e);
        }
    }

    private static List<String> collectEmbeddedCustomNodeDefinitions(WGraph graph, ListTag functionList) {
        Set<ResourceLocation> usedTypes = collectUsedNodeTypes(graph, functionList);
        if (usedTypes.isEmpty()) {
            return List.of();
        }
        Map<ResourceLocation, String> allCustomById = new HashMap<>();
        for (String raw : ComputedCustomNodes.readRawDefinitions()) {
            try {
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                if (!obj.has("id")) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse(obj.get("id").getAsString());
                allCustomById.put(id, raw);
            } catch (Exception ignored) {
            }
        }
        List<String> out = new ArrayList<>();
        for (ResourceLocation type : usedTypes) {
            String raw = allCustomById.get(type);
            if (raw != null) {
                out.add(raw);
            }
        }
        return out;
    }

    private static Set<ResourceLocation> collectUsedNodeTypes(WGraph graph, ListTag functionList) {
        Set<ResourceLocation> types = new HashSet<>();
        for (WNode node : graph.getNodes()) {
            types.add(node.getTypeId());
            if (node instanceof FunctionCardNode card) {
                collectNodeTypesFromGraphTag(card.getInnerGraph().save(), types);
            }
        }
        for (int i = 0; i < functionList.size(); i++) {
            CompoundTag def = functionList.getCompound(i);
            collectNodeTypesFromGraphTag(def.getCompound("Body"), types);
        }
        return types;
    }

    private static void collectNodeTypesFromGraphTag(CompoundTag graphTag, Set<ResourceLocation> out) {
        ListTag nodes = graphTag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag node = nodes.getCompound(i);
            if (node.contains("typeId", Tag.TAG_STRING)) {
                try {
                    out.add(ResourceLocation.parse(node.getString("typeId")));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static byte[] writeCompressed(CompoundTag tag) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            NbtIo.writeCompressed(tag, dos);
        }
        return baos.toByteArray();
    }

    private static CompoundTag readCompressed(byte[] bytes) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NbtIo.readCompressed(dis, NbtAccounter.unlimitedHeap());
        }
    }
}
