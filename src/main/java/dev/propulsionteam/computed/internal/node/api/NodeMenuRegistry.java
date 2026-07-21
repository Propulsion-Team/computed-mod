package dev.devce.websnodelib.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Hierarchical "add node" menu: categories (submenus) and node entries. Mods register categories and
 * place node types into any category. Node types registered in {@link NodeRegistry} but not added here
 * appear under {@link #UNCATEGORIZED}.
 */
public final class NodeMenuRegistry {
    /** Parent id for top-level categories. */
    public static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("websnodelib", "menu_root");
    /** Built-in category for node types with no explicit menu entry. */
    public static final ResourceLocation UNCATEGORIZED =
            ResourceLocation.fromNamespaceAndPath("websnodelib", "menu_uncategorized");

    public record Category(ResourceLocation id, Component title, ResourceLocation parentId) {}

    public record MenuEntry(ResourceLocation categoryId, ResourceLocation nodeType, Component label) {}

    private static final Map<ResourceLocation, Category> CATEGORIES = new LinkedHashMap<>();
    private static final List<MenuEntry> ENTRIES = new ArrayList<>();
    private static final java.util.Set<ResourceLocation> EXPLICIT_NODE_TYPES = new java.util.HashSet<>();
    /** Types never listed under uncategorized or search (editor-only / hidden nodes). */
    private static final Set<ResourceLocation> HIDDEN_FROM_ADD_MENU = new HashSet<>();

    private NodeMenuRegistry() {}

    /**
     * Register a submenu folder. {@code parentId} is usually {@link #ROOT} or another category id.
     */
    public static void registerCategory(ResourceLocation id, Component title, ResourceLocation parentId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(parentId, "parentId");
        CATEGORIES.put(id, new Category(id, title, parentId));
    }

    /**
     * Add a node type to a category. The node type must also be registered with {@link NodeRegistry}.
     */
    public static void addNodeEntry(ResourceLocation categoryId, ResourceLocation nodeType, Component label) {
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(label, "label");
        ENTRIES.add(new MenuEntry(categoryId, nodeType, label));
        EXPLICIT_NODE_TYPES.add(nodeType);
    }

    /** Hidden nodes stay off the add-node menu and search (still in {@link NodeRegistry}). */
    public static void hideFromAddMenu(ResourceLocation nodeType) {
        Objects.requireNonNull(nodeType, "nodeType");
        HIDDEN_FROM_ADD_MENU.add(nodeType);
    }

    public static void removeNodeEntriesForTypes(Set<ResourceLocation> nodeTypes) {
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            return;
        }
        ENTRIES.removeIf(e -> nodeTypes.contains(e.nodeType()));
        EXPLICIT_NODE_TYPES.removeAll(nodeTypes);
        HIDDEN_FROM_ADD_MENU.removeAll(nodeTypes);
    }

    public static void removeCategories(Set<ResourceLocation> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        ENTRIES.removeIf(e -> categoryIds.contains(e.categoryId()));
        CATEGORIES.keySet().removeIf(categoryIds::contains);
    }

    public static Category getCategory(ResourceLocation id) {
        return CATEGORIES.get(id);
    }

    public static List<Category> getChildCategories(ResourceLocation parentId) {
        return CATEGORIES.values().stream().filter(c -> c.parentId().equals(parentId)).toList();
    }

    /** Explicit entries only (not uncategorized). */
    public static List<MenuEntry> getExplicitEntriesIn(ResourceLocation categoryId) {
        return ENTRIES.stream().filter(e -> e.categoryId().equals(categoryId)).toList();
    }

    public static List<MenuEntry> getEntriesIn(ResourceLocation categoryId) {
        if (categoryId.equals(UNCATEGORIZED)) {
            return List.copyOf(computeUncategorized());
        }
        return getExplicitEntriesIn(categoryId);
    }

    private static List<MenuEntry> computeUncategorized() {
        List<MenuEntry> list = new ArrayList<>();
        for (ResourceLocation type : NodeRegistry.getRegisteredTypes()) {
            if (!EXPLICIT_NODE_TYPES.contains(type) && !HIDDEN_FROM_ADD_MENU.contains(type)) {
                list.add(new MenuEntry(UNCATEGORIZED, type, defaultLabelFor(type)));
            }
        }
        return list;
    }

    private static Component defaultLabelFor(ResourceLocation type) {
        return Component.literal(type.getNamespace() + ":" + type.getPath());
    }

    /** Flat list for search: every placeable node with its label. */
    public static List<MenuEntry> allSearchableEntries() {
        List<MenuEntry> all = new ArrayList<>(ENTRIES);
        all.addAll(computeUncategorized());
        return all;
    }

    public static List<MenuEntry> filterEntries(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return allSearchableEntries().stream()
                .filter(e -> entryMatches(e, q))
                .toList();
    }

    private static boolean entryMatches(MenuEntry e, String q) {
        if (e.label().getString().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        ResourceLocation t = e.nodeType();
        if (t.getPath().toLowerCase(Locale.ROOT).contains(q)
                || t.getNamespace().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        return categoryTitlePath(e.categoryId()).toLowerCase(Locale.ROOT).contains(q);
    }

    private static String categoryTitlePath(ResourceLocation categoryId) {
        if (categoryId.equals(ROOT)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        ResourceLocation id = categoryId;
        int guard = 0;
        while (id != null && !id.equals(ROOT) && guard++ < 64) {
            Category c = CATEGORIES.get(id);
            if (c == null) {
                break;
            }
            parts.add(c.title().getString());
            id = c.parentId();
        }
        return parts.reversed().stream().collect(Collectors.joining(" / "));
    }
}
