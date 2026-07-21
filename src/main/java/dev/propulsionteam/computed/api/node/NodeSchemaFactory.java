package dev.propulsionteam.computed.api.node;

import java.util.Objects;

/** Produces an instance schema from its typed properties, enabling stable dynamic ports. */
@FunctionalInterface
public interface NodeSchemaFactory {
    NodeSchema create(NodePropertyBag properties);

    static NodeSchemaFactory fixed(NodeSchema schema) {
        Objects.requireNonNull(schema, "schema");
        return ignored -> schema;
    }
}
