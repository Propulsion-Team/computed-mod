# Lua API Reference

All methods execute on the computer thread. Definition methods have no side effects, are available during preview validation, return the same node for chaining, and fail when called after validation.

## computed.node(apiVersion, id, title)

Parameters: integer API version (`1`), namespaced definition ID, and display title. Returns a mutable definition builder. Errors on unsupported versions, invalid IDs, invalid titles, or a second node in the same file. Preview behavior is identical to production. Example: `local node = computed.node(1, "example:add", "Add")`.

## node:category(name)

Sets the stable semantic category or nested category path. The default is `utility`. Errors on blank or overlong names. It selects a named renderer palette and has no runtime side effect. Example: `node:category("math/arithmetic")`.

## node:style(style)

Sets `standard`, `compact`, `source`, or `sink`; the default is `standard`. Errors on unknown styles. Example: `node:style("compact")`.

## node:input(id, type, options)

Adds an input. `options` is optional; `required` defaults to `true` and `default` defaults to `nil`. Type is `number`, `boolean`, `string`, `event`, `widget`, or `table`. Errors on duplicate IDs or invalid values. Example: `node:input("value", "number", { default = 0 })`.

## node:output(id, type, options)

Adds an output with the same option and type rules as inputs. Outputs retain their last committed value after errors or yields. Example: `node:output("result", "number")`.

## node:field(id, fieldType, options)

Adds a `number`, `text`, `boolean`, `choice`, `color`, `direction`, or `item` field. Every field is rendered as an editable control inside each node instance. Options include `default`, an optional display `label`, numeric `min`/`max`, a positive numeric `step`, and `choices` for choice fields. `visible_when = { field = "mode", equals = "advanced" }` conditionally shows a field while preserving its value when hidden. Number fields default to `control = "value"`; `control = "slider"` requires finite `min` and `max` values with `max > min`. Text uses a value box, booleans use toggles, choice and direction fields use dropdowns, colors use ARGB hexadecimal controls, and items open the searchable item picker. Errors on invalid defaults, ranges, steps, controls, visibility references, or empty choice lists. Example: `node:field("gain", "number", { default = 1, min = 0, max = 4, control = "slider", step = 0.1, label = "Gain" })`.

## node:state(id, defaultValue)

Declares persistent state with a serializable default. Returns the node. Errors on duplicate IDs or unsupported values during validation/persistence. Example: `node:state("count", 0)`.

## node:execution(policy)

Sets `input`, `tick`, `step`, or `event`; the default is `input`. Returns the node and errors on an unknown policy. Example: `node:execution("tick")`.

## node:on_run(callback)

Installs the single run callback. Returns the node. Errors when declared twice or when the value is not a function. The callback runs transactionally. Example: `node:on_run(function(ctx) ctx:output("ok", true) end)`.

## node:on_event(eventName, callback)

Installs one named event callback. Returns the node. Errors on an invalid or duplicate event name. The callback receives `ctx` followed by emitted arguments. Example: `node:on_event("reset", function(ctx) ctx:set_state("count", 0) end)`.

Context methods are available only inside callbacks. Reads have no side effects; writes stage changes until successful return.

## ctx:input(id)

Returns the current connected or default input. Errors on malformed calls. Preview returns the editable sample input. Example: `local speed = ctx:input("speed")`.

## ctx:inputs()

Returns a copy of all current inputs keyed by stable port ID, including configurable per-node ports. Example: `for id, value in pairs(ctx:inputs()) do ... end`.

## ctx:output(id, value)

Stages an output and returns nothing. Errors if the value cannot later be serialized. Preview updates the real renderer sample. Example: `ctx:output("result", 4)`.

## ctx:field(id)

Returns the authoritative field or its default. Preview returns the editable sample field. Example: `local color = ctx:field("color")`.

## ctx:state(id)

Returns an isolated copy of committed state. Mutating a returned table does not commit it; use `set_state`. Example: `local count = ctx:state("count")`.

## ctx:set_state(id, value)

Stages persistent state and returns nothing. Errors on unknown or non-serializable state. It commits only after callback success. Example: `ctx:set_state("count", count + 1)`.

## ctx:endpoint(id, target)

Returns a safe endpoint proxy. `target` is optional and defaults to an empty target. Errors on unknown endpoints. Preview uses fixtures or reports unavailable; it never performs production side effects. Example: `local world = ctx:endpoint("computed:world")`.

## ctx:emit(eventName, ...)

Queues a named graph event with serializable arguments and returns nothing. Delivery is deterministic after the current graph pass. Preview delivers inside the isolated preview graph. Example: `ctx:emit("changed", 12)`.

## ctx:tick()

Returns the current non-negative computer tick as an integer. It has no side effects and preview uses the preview tick. Example: `local now = ctx:tick()`.

## ctx:graph_step()

Returns the current deterministic graph step as an integer. It has no side effects. Example: `local order = ctx:graph_step()`.

## ctx:is_preview()

Returns `true` only in the isolated live preview. It has no side effects. Production logic should prefer endpoint policies over branching on this value. Example: `if ctx:is_preview() then ... end`.

## endpoint:methods()

Returns an alphabetically stable array of method IDs. Bound dynamic endpoints such as `computercraft:peripheral` instead return methods exposed by the selected target. It has no side effects. Static endpoint metadata is available in preview; dynamic target discovery may be unavailable. Example: `for _, name in ipairs(endpoint:methods()) do ... end`.

## endpoint:call(methodName, ...)

Validates arguments and invokes the registered handler. It returns the declared values, may yield only when the policy permits, and errors on unavailable previews, signature mismatches, handler failures, or invalid return values. Side effects and execution side are method-specific. Example: `local time = world:call("time")`.
