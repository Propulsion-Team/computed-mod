package net.minecraft.resources;

import java.util.Objects;

/** Minimal test signature used because the plain JUnit source set does not inherit ModDev's game classpath. */
public final class ResourceLocation {
    private final String namespace;
    private final String path;

    private ResourceLocation(String namespace, String path) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.path = Objects.requireNonNull(path, "path");
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(validateNamespace(namespace), validatePath(path));
    }

    public static ResourceLocation parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        String namespace = separator >= 0 ? value.substring(0, separator) : "minecraft";
        String path = separator >= 0 ? value.substring(separator + 1) : value;
        return fromNamespaceAndPath(namespace, path);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ResourceLocation location
                        && namespace.equals(location.namespace)
                        && path.equals(location.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }

    private static String validateNamespace(String namespace) {
        if (namespace == null || namespace.isBlank() || !namespace.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid resource namespace: " + namespace);
        }
        return namespace;
    }

    private static String validatePath(String path) {
        if (path == null || path.isBlank() || !path.matches("[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid resource path: " + path);
        }
        return path;
    }
}
