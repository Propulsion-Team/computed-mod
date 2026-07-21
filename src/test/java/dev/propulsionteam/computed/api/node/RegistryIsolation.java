package dev.propulsionteam.computed.api.node;

import dev.propulsionteam.computed.api.node.client.ComputedNodeClientApi;
import dev.propulsionteam.computed.api.node.client.NodePresentation;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Saves and restores irreversible static registries so freeze tests cannot affect other test classes. */
final class RegistryIsolation {
    private RegistryIsolation() {}

    static CommonSnapshot snapshotCommon() throws ReflectiveOperationException {
        Field typesField = field(ComputedNodeApi.class, "NODE_TYPES");
        Field categoriesField = field(ComputedNodeApi.class, "CATEGORIES");
        Field frozenField = field(ComputedNodeApi.class, "frozen");
        return new CommonSnapshot(
                new LinkedHashMap<>(map(typesField)),
                new LinkedHashMap<>(map(categoriesField)),
                frozenField.getBoolean(null),
                typesField,
                categoriesField,
                frozenField);
    }

    static ClientSnapshot snapshotClient() throws ReflectiveOperationException {
        Field presentationsField = field(ComputedNodeClientApi.class, "PRESENTATIONS");
        Field frozenField = field(ComputedNodeClientApi.class, "frozen");
        return new ClientSnapshot(
                new LinkedHashMap<>(map(presentationsField)),
                frozenField.getBoolean(null),
                presentationsField,
                frozenField);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Field field) throws IllegalAccessException {
        return (Map<K, V>) field.get(null);
    }

    record CommonSnapshot(
            Map<ResourceLocation, NodeType<?>> types,
            Map<ResourceLocation, NodeCategory> categories,
            boolean frozen,
            Field typesField,
            Field categoriesField,
            Field frozenField) {
        void makeMutable() throws IllegalAccessException {
            frozenField.setBoolean(null, false);
        }

        void restore() throws IllegalAccessException {
            Map<ResourceLocation, NodeType<?>> liveTypes = map(typesField);
            liveTypes.clear();
            liveTypes.putAll(types);
            Map<ResourceLocation, NodeCategory> liveCategories = map(categoriesField);
            liveCategories.clear();
            liveCategories.putAll(categories);
            frozenField.setBoolean(null, frozen);
        }
    }

    record ClientSnapshot(
            Map<ResourceLocation, NodePresentation> presentations,
            boolean frozen,
            Field presentationsField,
            Field frozenField) {
        void makeMutable() throws IllegalAccessException {
            frozenField.setBoolean(null, false);
        }

        void restore() throws IllegalAccessException {
            Map<ResourceLocation, NodePresentation> livePresentations = map(presentationsField);
            livePresentations.clear();
            livePresentations.putAll(presentations);
            frozenField.setBoolean(null, frozen);
        }
    }
}
