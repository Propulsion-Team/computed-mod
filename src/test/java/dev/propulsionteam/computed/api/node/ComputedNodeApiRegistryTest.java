package dev.propulsionteam.computed.api.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** All irreversible common-registry assertions intentionally live in one test. */
class ComputedNodeApiRegistryTest {
    private RegistryIsolation.CommonSnapshot registrySnapshot;

    @BeforeEach
    void isolateRegistry() throws ReflectiveOperationException {
        registrySnapshot = RegistryIsolation.snapshotCommon();
        registrySnapshot.makeMutable();
    }

    @AfterEach
    void restoreRegistry() throws ReflectiveOperationException {
        registrySnapshot.restore();
    }

    @Test
    void registryRejectsDuplicatesAndBecomesImmutableAfterExplicitFreeze() {
        ResourceLocation categoryId =
                ResourceLocation.fromNamespaceAndPath("computed_api_test", "registry_category");
        ResourceLocation nodeId =
                ResourceLocation.fromNamespaceAndPath("computed_api_test", "registry_node");
        NodeCategory category = ComputedNodeApi.registerCategory(
                categoryId, Component.literal("Test category"), ComputedNodeApi.ROOT_CATEGORY);
        NodeType<Integer> type = nodeType(nodeId, categoryId);

        assertSame(type, ComputedNodeApi.register(type));
        assertSame(type, ComputedNodeApi.requireNodeType(nodeId));
        assertEquals(category, ComputedNodeApi.category(categoryId).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> ComputedNodeApi.nodeTypes().clear());

        IllegalStateException duplicateType =
                assertThrows(IllegalStateException.class, () -> ComputedNodeApi.register(type));
        assertTrue(duplicateType.getMessage().contains(nodeId.toString()));
        IllegalStateException duplicateCategory = assertThrows(
                IllegalStateException.class,
                () -> ComputedNodeApi.registerCategory(
                        categoryId, Component.literal("Duplicate"), ComputedNodeApi.ROOT_CATEGORY));
        assertTrue(duplicateCategory.getMessage().contains(categoryId.toString()));

        ComputedNodeApi.freeze();
        ComputedNodeApi.freeze();
        assertTrue(ComputedNodeApi.isFrozen());
        IllegalStateException frozenType = assertThrows(
                IllegalStateException.class,
                () -> ComputedNodeApi.register(nodeType(
                        ResourceLocation.fromNamespaceAndPath("computed_api_test", "too_late"), categoryId)));
        assertTrue(frozenType.getMessage().contains("frozen"));
        IllegalStateException frozenCategory = assertThrows(
                IllegalStateException.class,
                () -> ComputedNodeApi.registerCategory(
                        ResourceLocation.fromNamespaceAndPath("computed_api_test", "too_late_category"),
                        Component.literal("Too late"),
                        ComputedNodeApi.ROOT_CATEGORY));
        assertTrue(frozenCategory.getMessage().contains("frozen"));
    }

    private static NodeType<Integer> nodeType(ResourceLocation id, ResourceLocation category) {
        return NodeType.<Integer>builder(id)
                .title(Component.literal("Registry test node"))
                .category(category)
                .schema(NodeSchema.empty())
                .stateCodec(Codec.INT)
                .defaultState(0)
                .evaluator((prior, context) -> prior)
                .build();
    }
}
