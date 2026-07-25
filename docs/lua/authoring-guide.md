# Lua Node Authoring Guide

Every Computed node is a Lua definition. A file creates one node, declares an immutable schema, installs callbacks, and returns that node.

```lua
local node = computed.node(1, "example:counter", "Counter")

node:category("state")
node:style("standard")
node:input("increment", "number")
node:output("count", "number")
node:field("step", "number", { default = 1 })
node:state("count", 0)

node:on_run(function(ctx)
    local next = ctx:state("count") + ctx:input("increment") * ctx:field("step")
    ctx:set_state("count", next)
    ctx:output("count", next)
end)

return node
```

Definition IDs are stable, lowercase, namespaced identifiers. Port, field, state, and event IDs start with a lowercase letter and contain only lowercase letters, digits, `_`, `.`, or `-`. Schemas cannot change while an instance is running.

Choose `input` execution for dataflow nodes, `tick` for world sensors, `step` for explicitly stepped nodes, and `event` when only named handlers should run. A failed invocation discards all staged output and state changes. The next eligible execution retries the callback.

Use endpoints for Minecraft or addon access. Lua values never contain Java or Minecraft objects.

See [Lua API Reference](lua-api-reference.md), [Types and State](types-and-state.md), [Sandbox and Budgets](sandbox-and-budgets.md), and the files under `docs/lua/examples`.
