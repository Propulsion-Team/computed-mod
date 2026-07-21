package dev.propulsionteam.computed.api.node;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.api.node.client.ComputedNodeClientApi;
import dev.propulsionteam.computed.api.node.client.NodePresentation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** All irreversible client-registry assertions intentionally live in one test. */
class ComputedNodeClientApiRegistryTest {
    private RegistryIsolation.ClientSnapshot registrySnapshot;

    @BeforeEach
    void isolateRegistry() throws ReflectiveOperationException {
        registrySnapshot = RegistryIsolation.snapshotClient();
        registrySnapshot.makeMutable();
    }

    @AfterEach
    void restoreRegistry() throws ReflectiveOperationException {
        registrySnapshot.restore();
    }

    @Test
    void presentationRegistryRejectsDuplicatesAndFreezes() {
        ResourceLocation nodeId =
                ResourceLocation.fromNamespaceAndPath("computed_api_test", "presented_node");
        NodePresentation presentation = ignored -> {};

        assertSame(presentation, ComputedNodeClientApi.registerPresentation(nodeId, presentation));
        assertSame(presentation, ComputedNodeClientApi.presentation(nodeId).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> ComputedNodeClientApi.presentations().clear());

        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class,
                () -> ComputedNodeClientApi.registerPresentation(nodeId, ignored -> {}));
        assertTrue(duplicate.getMessage().contains(nodeId.toString()));

        ComputedNodeClientApi.freeze();
        ComputedNodeClientApi.freeze();
        assertTrue(ComputedNodeClientApi.isFrozen());
        IllegalStateException frozen = assertThrows(
                IllegalStateException.class,
                () -> ComputedNodeClientApi.registerPresentation(
                        ResourceLocation.fromNamespaceAndPath("computed_api_test", "too_late_presentation"),
                        ignored -> {}));
        assertTrue(frozen.getMessage().contains("frozen"));
    }
}
