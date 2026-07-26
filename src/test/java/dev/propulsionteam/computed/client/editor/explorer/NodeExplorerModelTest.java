package dev.propulsionteam.computed.client.editor.explorer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NodeExplorerModelTest {
    @Test
    void sortsFoldersBeforeNodesAndRestoresExpansionAfterSearch() {
        NodeExplorerModel explorer = new NodeExplorerModel(List.of(
                new ExplorerNode(
                        "computed:add",
                        "Add",
                        ExplorerNode.Ownership.BUNDLED,
                        List.of("math", "arithmetic"),
                        true,
                        ""),
                new ExplorerNode(
                        "addon:kinetic",
                        "Kinetic Speed",
                        ExplorerNode.Ownership.INTEGRATION,
                        List.of("create", "kinetics"),
                        false,
                        "Create is not installed"),
                new ExplorerNode(
                        "user:counter",
                        "Counter",
                        ExplorerNode.Ownership.USER,
                        List.of("state"),
                        true,
                        "")));
        explorer.setExpanded("bundled/math", true);
        explorer.setExpanded("bundled/math/arithmetic", true);

        List<ExplorerRow> initial = explorer.visibleRows();
        assertEquals(List.of("Bundled", "Integrations", "User Nodes"), initial.stream()
                .filter(row -> row.depth() == 0)
                .map(ExplorerRow::label)
                .toList());

        explorer.search("kinetic");
        List<ExplorerRow> filtered = explorer.visibleRows();
        assertTrue(filtered.stream().anyMatch(row -> row.label().equals("Kinetic Speed")));
        ExplorerRow unavailable = filtered.stream()
                .filter(row -> row.node() != null)
                .findFirst()
                .orElseThrow();
        assertFalse(unavailable.node().available());

        explorer.search("");
        assertTrue(explorer.visibleRows().stream().anyMatch(row -> row.label().equals("Add")));
    }
}
