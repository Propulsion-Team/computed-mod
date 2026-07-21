# Migrating addons from Web's Node Lib

Computed no longer exports or embeds `dev.devce.websnodelib`. This is an intentional source- and
binary-incompatible break that prevents the Java module split-package conflict with Aeronautics. An
addon compiled against `WNode`, `WGraph`, or any other Web's Node Lib type must be ported and rebuilt.
There is no compatibility facade.

## Registration

- Replace `NodeRegistry.register(...)` with `ComputedNodeApi.register(NodeType<?>)`.
- Register palette groups with `ComputedNodeApi.registerCategory(...)` before registering children.
- Perform common registration during mod startup, before Computed freezes the registry.
- Move editor-only code to a client-only package and register it with
  `ComputedNodeClientApi.registerPresentation(...)`.
- Treat duplicate IDs as startup errors. Do not catch and ignore them.

The complete registration lifecycle and a buildable source example are documented in
[`node-api.md`](node-api.md) and [`example-addon`](example-addon/README.md).

## Node declarations

| Old concept | Computed API |
| --- | --- |
| `WNode` subclass | `NodeType<S>` built with `NodeType.builder(...)` |
| positional `WPin` | stable `PortKey<T>` in a `NodeSchema` |
| pin value reads/writes | typed `NodeExecutionContext.input(...)` and `output(...)` |
| mutable fields saved by the node | immutable state `S` plus a state codec |
| element-backed settings | typed `NodeProperty<T>` entries |
| pins rebuilt from controls | `NodeSchemaFactory` driven by properties |
| `evaluate(...)` | `NodeExecutor<S>` returning the next state |
| editor rendering in the node class | optional client `NodePresentation` |

Keep node IDs, port-key IDs, property keys, and state codec fields stable after release. Labels and
translations may change; persistence identifiers must not. When a property changes a dynamic schema,
retain existing keys and append new keys instead of renumbering ports.

Use `stateBoundary(true)` for latches, delays, counters, and other memory nodes. Their outputs expose
prior-step state; returned state is committed after the graph step. World access and side effects must
go through `NodeExecutionContext`, which suppresses effects in client preview.

## Saved-program migration

Computed accepts the old `ComputerGraph` and `ComputerFunctions` NBT layout, legacy function and
clipboard fragments, Base64/SNBT imports, and `CMP1` share strings. Built-in IDs in the
`websnodelib:*` namespace are canonicalized to `computed:*`. The first successful save writes only the
version-2 `ComputedProgram` layout, and share export emits `CMP2`.

This migration is one-way. A world saved by the rewritten engine is not supported by older Computed
releases. Back up a world before upgrading if downgrade support matters.

Unknown addon nodes are retained as disabled placeholders with their raw type, properties, state,
ports, and connections. Reinstalling an addon that registers the original node ID allows the program
to resolve it again. Malformed connections and legacy combinational cycles remain visible as
diagnostics so users can repair them rather than losing content.
