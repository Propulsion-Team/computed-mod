# Computed node API

Computed exposes its node declaration API from `dev.propulsionteam.computed.api.node`. Addons register
node types and palette categories during common startup, before Computed freezes the registry. Optional
custom editor presentations are registered from client startup through the separate
`dev.propulsionteam.computed.api.node.client` package.

See [`docs/example-addon`](example-addon/README.md) for a complete stateful node and client
presentation registration example.

## Declaring a node

Define port and property keys as constants. Their lowercase IDs are persistence identifiers, so never
derive them from translated labels and never reuse an ID for a different type.

```java
public static final PortKey<Double> INPUT = PortKey.of("input", PortType.NUMBER);
public static final PortKey<Double> OUTPUT = PortKey.of("output", PortType.NUMBER);
public static final NodeProperty<Double> SCALE =
        NodeProperty.number("scale", Component.literal("Scale"), 1.0D);
```

Build a `NodeType<S>` with its identity, schema, properties, state codec and evaluator. The evaluator
receives the immutable state from the beginning of the graph step, writes typed outputs through the
context, and returns the next state. The runtime commits returned states only after the step.

Use `stateBoundary(true)` when the node's prior state breaks combinational dependency cycles. Choose an
execution policy deliberately:

- `INPUT_DRIVEN` runs after an input or property changes.
- `EVERY_GAME_TICK` runs once per Minecraft tick.
- `EVERY_GRAPH_STEP` runs on every scheduler step.

World access is available only through `NodeExecutionContext`. Side-effecting nodes should use
`runSideEffect`; it does nothing during client previews or any evaluation where effects are suppressed.

## Dynamic schemas

Pass a `NodeSchemaFactory` instead of a fixed `NodeSchema` when properties determine the ports. Build
dynamic IDs from stable property values—for example `widget_0`, `widget_1`, and so on. Existing IDs must
not be renumbered merely because labels or translations change.

```java
.property(INPUT_COUNT)
.schema(properties -> {
    NodeSchema.Builder schema = NodeSchema.builder();
    for (int i = 0; i < properties.get(INPUT_COUNT); i++) {
        schema.input(PortKey.of("widget_" + i, PortType.WIDGET), Component.literal("Widget " + (i + 1)));
    }
    return schema.build();
})
```

The built-in neutral values are `0.0` for numbers, the empty string for text, and `null` for the opaque
no-widget value. Runtime validation accepts identical types and the documented number-to-string
conversion; it rejects other mismatches.

## Registration lifecycle

Register parent categories before child categories, then register node types. Duplicate IDs throw an
actionable startup error. Computed calls `ComputedNodeApi.freeze()` after addon registration; all later
common registrations are rejected. The client presentation registry has the same duplicate and freeze
behavior.

Custom presentations are optional. With no registration, the editor builds controls from the node's
typed property definitions. Client presentation code must stay in a client-only package and should only
be invoked from the loader's client initialization path.

## Persistence compatibility

`ResourceLocation` node IDs, `PortKey` IDs, property keys, and codec shapes are saved data. Changing any
of them requires an explicit migration. Add new ports and properties with stable defaults, preserve old
keys when practical, and treat state records as immutable values rather than mutating the prior state in
place.
