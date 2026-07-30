package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComputedPackageStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void listsComputedFilesAndGeneratesSafeSuffixes() throws Exception {
        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);
        Path graphs = storage.directory(ComputedPackageStorage.Kind.GRAPHS);
        Files.writeString(graphs.resolve("z.computed"), "z");
        Files.writeString(graphs.resolve("A.computed"), "a");
        Files.writeString(graphs.resolve("ignore.txt"), "x");

        assertEquals("A.computed", storage.list(ComputedPackageStorage.Kind.GRAPHS).getFirst().getFileName().toString());
        assertEquals("z.computed", storage.nextAvailable(ComputedPackageStorage.Kind.GRAPHS, "z").getFileName().toString().replace(" (1)", ""));
        assertEquals("z (1).computed", storage.nextAvailable(ComputedPackageStorage.Kind.GRAPHS, "z").getFileName().toString());
        assertEquals("bad_name", ComputedPackageStorage.sanitizeName("bad/name"));
        assertThrows(IllegalArgumentException.class, () -> ComputedPackageStorage.sanitizeName(".."));
    }

    @Test
    void usesPlainLuaFilesForNodeStorage() throws Exception {
        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);
        Path nodes = storage.directory(ComputedPackageStorage.Kind.NODES);
        Files.writeString(nodes.resolve("Node.lua"), "return node");
        Files.writeString(nodes.resolve("legacy.computed"), "legacy");

        assertEquals(
                "Node.lua",
                storage.list(ComputedPackageStorage.Kind.NODES).getFirst().getFileName().toString());
        assertEquals(
                "Node (1).lua",
                storage.nextAvailable(ComputedPackageStorage.Kind.NODES, "Node").getFileName().toString());
        assertEquals(
                "Fresh.lua",
                storage.target(ComputedPackageStorage.Kind.NODES, "Fresh").getFileName().toString());
    }
}
