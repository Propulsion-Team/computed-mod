package dev.propulsionteam.computed.client.editor.explorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NodeExplorerModel {
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing(Entry::isNode)
            .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::stablePath);

    private final Entry root;
    private final Set<String> expanded = new HashSet<>();
    private Set<String> expansionBeforeSearch = Set.of();
    private String query = "";
    private int selectedIndex;

    public NodeExplorerModel(List<ExplorerNode> nodes) {
        MutableEntry mutableRoot = new MutableEntry("", "", null);
        if (nodes != null) {
            nodes.forEach(node -> insert(mutableRoot, node));
        }
        root = freeze(mutableRoot);
        for (Entry owner : root.children()) {
            expanded.add(owner.stablePath());
        }
    }

    public void search(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (this.query.isEmpty() && !normalized.isEmpty()) {
            expansionBeforeSearch = Set.copyOf(expanded);
        }
        if (!this.query.isEmpty() && normalized.isEmpty()) {
            expanded.clear();
            expanded.addAll(expansionBeforeSearch);
            expansionBeforeSearch = Set.of();
        }
        this.query = normalized;
        selectedIndex = 0;
    }

    public List<ExplorerRow> visibleRows() {
        List<ExplorerRow> rows = new ArrayList<>();
        for (Entry child : root.children()) {
            append(child, 0, rows);
        }
        return List.copyOf(rows);
    }

    public ExplorerRow selected() {
        List<ExplorerRow> rows = visibleRows();
        if (rows.isEmpty()) {
            return null;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, rows.size() - 1));
        return rows.get(selectedIndex);
    }

    public void moveSelection(int delta) {
        List<ExplorerRow> rows = visibleRows();
        if (rows.isEmpty()) {
            selectedIndex = 0;
        } else {
            selectedIndex = Math.floorMod(selectedIndex + delta, rows.size());
        }
    }

    public void toggleSelected() {
        ExplorerRow selected = selected();
        if (selected == null || !selected.folder()) {
            return;
        }
        if (!expanded.remove(selected.stablePath())) {
            expanded.add(selected.stablePath());
        }
    }

    public void setExpanded(String stablePath, boolean value) {
        if (value) {
            expanded.add(stablePath);
        } else {
            expanded.remove(stablePath);
        }
    }

    public boolean isSearching() {
        return !query.isEmpty();
    }

    private void append(Entry entry, int depth, List<ExplorerRow> rows) {
        Match match = match(entry);
        if (!match.visible()) {
            return;
        }
        boolean open = !query.isEmpty() ? match.descendantMatch() : expanded.contains(entry.stablePath());
        rows.add(new ExplorerRow(
                entry.stablePath(),
                entry.label(),
                depth,
                !entry.isNode(),
                open,
                entry.node()));
        if (!entry.isNode() && open) {
            entry.children().forEach(child -> append(child, depth + 1, rows));
        }
    }

    private Match match(Entry entry) {
        if (query.isEmpty()) {
            return new Match(true, false);
        }
        boolean self = entry.label().toLowerCase(Locale.ROOT).contains(query)
                || entry.stablePath().toLowerCase(Locale.ROOT).contains(query);
        boolean descendant = entry.children().stream().anyMatch(child -> match(child).visible());
        return new Match(self || descendant, descendant);
    }

    private static void insert(MutableEntry root, ExplorerNode node) {
        String ownerId = node.ownership().name().toLowerCase(Locale.ROOT);
        String ownerLabel = switch (node.ownership()) {
            case BUNDLED -> "Bundled";
            case INTEGRATION -> "Integrations";
            case USER -> "User Nodes";
        };
        MutableEntry current = root.children.computeIfAbsent(
                ownerId,
                ignored -> new MutableEntry(ownerId, ownerLabel, null));
        String path = ownerId;
        for (String segment : node.folderPath()) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            path += '/' + segment;
            String stablePath = path;
            current = current.children.computeIfAbsent(
                    segment,
                    ignored -> new MutableEntry(stablePath, segment, null));
        }
        String nodePath = path + "/@" + node.id();
        current.children.put("@" + node.id(), new MutableEntry(nodePath, node.title(), node));
    }

    private static Entry freeze(MutableEntry entry) {
        List<Entry> children = entry.children.values().stream()
                .map(NodeExplorerModel::freeze)
                .sorted(ENTRY_ORDER)
                .toList();
        return new Entry(entry.stablePath, entry.label, entry.node, children);
    }

    private record Entry(String stablePath, String label, ExplorerNode node, List<Entry> children) {
        private boolean isNode() {
            return node != null;
        }
    }

    private static final class MutableEntry {
        private final String stablePath;
        private final String label;
        private final ExplorerNode node;
        private final Map<String, MutableEntry> children = new LinkedHashMap<>();

        private MutableEntry(String stablePath, String label, ExplorerNode node) {
            this.stablePath = stablePath;
            this.label = label;
            this.node = node;
        }
    }

    private record Match(boolean visible, boolean descendantMatch) {}
}
