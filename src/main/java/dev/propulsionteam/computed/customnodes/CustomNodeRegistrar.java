package dev.propulsionteam.computed.customnodes;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.Computed;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CustomNodeRegistrar {
    private final Set<ResourceLocation> registeredTypeIds = new LinkedHashSet<>();
    private final Set<ResourceLocation> registeredCategoryIds = new LinkedHashSet<>();

    public record ReloadSummary(int loaded, int skipped, int warnings, int errors, List<String> messages) {}

    public ReloadSummary reload(Path root) {
        CustomNodeLoader.LoadResult load = new CustomNodeLoader().load(root);
        List<String> messages = new ArrayList<>();
        for (String warning : load.diagnostics().warnings()) {
            messages.add("WARN " + warning);
        }
        for (String error : load.diagnostics().errors()) {
            messages.add("ERROR " + error);
        }
        return applyDefinitions(load.definitions(), messages);
    }

    /** Replaces all custom registrations with the given definitions (used for server→client sync). */
    public ReloadSummary applyDefinitions(List<CustomNodeDefinition> definitions) {
        return applyDefinitions(definitions, new ArrayList<>());
    }

    private ReloadSummary applyDefinitions(List<CustomNodeDefinition> definitions, List<String> messages) {
        clearCustomRegistrations();
        int loaded = 0;
        int skipped = 0;
        for (CustomNodeDefinition def : definitions) {
            if (NodeRegistry.isRegistered(def.id())) {
                skipped++;
                messages.add("WARN " + def.sourceFile() + ": id already registered: " + def.id());
                continue;
            }
            try {
                ResourceLocation menuId = ensureBuiltinNodeCategories(def.menuPath());
                NodeRegistry.register(def.id(), (x, y) -> new CustomRuntimeNode(def, x, y));
                NodeMenuRegistry.addNodeEntry(menuId, def.id(), Component.literal(def.label()));
                registeredTypeIds.add(def.id());
                loaded++;
            } catch (Exception e) {
                skipped++;
                messages.add("ERROR " + def.sourceFile() + ": " + e.getMessage());
            }
        }
        int errors = (int) messages.stream().filter(m -> m.startsWith("ERROR ")).count();
        int warnings = (int) messages.stream().filter(m -> m.startsWith("WARN ")).count();
        return new ReloadSummary(loaded, skipped, warnings, errors, messages);
    }

    public void clearCustomRegistrations() {
        NodeMenuRegistry.removeNodeEntriesForTypes(registeredTypeIds);
        for (ResourceLocation typeId : registeredTypeIds) {
            NodeRegistry.unregister(typeId);
        }
        NodeMenuRegistry.removeCategories(registeredCategoryIds);
        registeredTypeIds.clear();
        registeredCategoryIds.clear();
    }

    private ResourceLocation ensureBuiltinNodeCategories(List<String> rawPath) {
        ResourceLocation root = rootCategory();
        ResourceLocation parent = root;
        StringBuilder pathKey = new StringBuilder();
        if (NodeMenuRegistry.getCategory(root) == null) {
            NodeMenuRegistry.registerCategory(root, Component.literal("Custom"), NodeMenuRegistry.ROOT);
            registeredCategoryIds.add(root);
        }
        for (String segment : rawPath) {
            if ("custom".equalsIgnoreCase(segment)) {
                continue;
            }
            String normalized = CustomNodeLoader.normalizeSegment(segment);
            if (!pathKey.isEmpty()) {
                pathKey.append("/");
            }
            pathKey.append(normalized);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    Computed.MODID, "menu_custom/" + pathKey.toString().toLowerCase(Locale.ROOT));
            if (NodeMenuRegistry.getCategory(id) == null) {
                NodeMenuRegistry.registerCategory(id, Component.literal(segment), parent);
                registeredCategoryIds.add(id);
            }
            parent = id;
        }
        return parent;
    }

    private static ResourceLocation rootCategory() {
        return ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_custom");
    }
}
