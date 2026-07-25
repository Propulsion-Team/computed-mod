# CC:Tweaked Integration

CC:Tweaked support is optional and targets its public `dan200.computercraft.api` surface. It is peripheral interoperability, not CraftOS compatibility.

Adjacent peripherals are exposed through `ctx:endpoint("computercraft:peripheral", side)`. A proxy provides `methods()` and `call(methodName, ...)`. Immediate calls return directly. Main-thread tasks and yielded `MethodResult` continuations suspend the node while committed outputs remain visible. Unload, definition replacement, peripheral detach, or cancellation terminates the continuation safely.

Dedicated CC Input and CC Output Lua nodes exchange named channel values. The Computed peripheral lists channels, reads outputs, writes inputs, and emits output-changed events.

Filesystem, terminal, rednet, HTTP, and other CraftOS globals are not exposed.

```lua
local node = computed.node(1, "example:cc_query", "CC Query")
node:field("side", "direction", { default = "left" })
node:output("result", "table")
node:on_run(function(ctx)
    local peripheral = ctx:endpoint("computercraft:peripheral", ctx:field("side"))
    ctx:output("result", { methods = peripheral:methods() })
end)
return node
```
