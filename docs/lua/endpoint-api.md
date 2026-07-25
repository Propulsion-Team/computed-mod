# Java Endpoint API

Endpoints are the only bridge from Lua to Minecraft and addons. Handlers receive the host object; Lua receives only validated values.

## ComputedEndpoints.register(id, registration)

Registers one stable namespaced endpoint during mod setup and returns its immutable definition. The registration callback receives an `EndpointBuilder`. It runs on the registration thread, is unavailable to Lua previews, and has the side effect of adding a global API entry. It throws on invalid or duplicate endpoint IDs.

```java
ComputedEndpoints.register("addon:storage", endpoint -> endpoint
        .method("stored", signature, policy, handler, previewFixture, "Returns stored units."));
```

## EndpointBuilder.method(methodId, signature, policy, handler)

Adds a method and returns the builder. Parameters declare stable ID, argument/return schema, execution/yield/side-effect/preview policy, and handler. The short overload marks preview unavailable. It throws on duplicate IDs or incomplete preview policy.

## EndpointBuilder.method(methodId, signature, policy, handler, previewFixture, documentation)

Adds a fully described method and returns the builder. `previewFixture` must be deterministic when preview is enabled. The documentation string feeds completion/signature help. Handler side effects must match the policy.

## ComputedEndpoints.find(id)

Returns an optional immutable endpoint definition. It has no side effects and may be called on either side for metadata lookup.

## ComputedEndpoints.definitions()

Returns all definitions sorted by stable ID. It has no side effects and powers documentation/completion checks.

## computed:world/time

Signature: `() -> number`. Runs on the computer thread, does not yield, has no side effects, and returns fixture `6000` in preview. Errors when the computer host cannot expose world time. Example: `ctx:endpoint("computed:world"):call("time")`.

## computed:world/position

Signature: `() -> number, number, number`. Runs on the computer thread, has no side effects, and is available in previews with a fixed position fixture. Errors when host world access is unavailable. Example: `local x, y, z = ctx:endpoint("computed:world"):call("position")`.

## computed:world/rotation

Signature: `() -> number, number, number`. Runs on the computer thread, has no side effects, and is available in previews with a fixed rotation fixture. Errors when host world access is unavailable. Example: `local yaw, pitch, roll = ctx:endpoint("computed:world"):call("rotation")`.

## computed:world/block_present

Signature: `(face: string) -> boolean`. Runs on the computer thread, has no side effects, and returns `false` in previews. Unknown face names return `false`; invalid argument types are errors. Example: `ctx:endpoint("computed:world"):call("block_present", "front")`.

## computed:redstone/input

Signature: `(face: string) -> number`. Runs on the computer thread, has no side effects, and returns zero in previews. Unknown face names return zero. Example: `ctx:endpoint("computed:redstone"):call("input", "left")`.

## computed:redstone/comparator

Signature: `(face: string) -> number`. Runs on the computer thread, has no side effects, and returns zero in previews. Unknown face names return zero. Example: `ctx:endpoint("computed:redstone"):call("comparator", "front")`.

## computed:redstone/output

Signature: `(face: string, level: number) -> ()`. Runs on the computer thread, clamps power to 0–15, performs a world side effect, and is unavailable in previews. Unknown face names are ignored. Example: `ctx:endpoint("computed:redstone"):call("output", "back", 15)`.

## computed:command/run

Signature: `(string) -> ()`. Runs on the computer thread, does not yield, performs a command side effect, and is unavailable in preview. Errors on a missing host, invalid argument, or command failure. Example: `ctx:endpoint("computed:command"):call("run", "say hello")`.

## computed:widget/text

Signature: `(string) -> table`. Runs on the computer thread, does not yield, has no world side effect, and uses the same deterministic fixture in preview. Returns `{ type = "text", text = value }`. Example: `ctx:output("widget", ctx:endpoint("computed:widget"):call("text", "Ready"))`.

## computed:widget/clock

Signature: `(color: number, showSeconds: boolean) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Errors on invalid argument types. Example: `ctx:endpoint("computed:widget"):call("clock", 0xffffffff, true)`.

## computed:widget/button

Signature: `(label: string, color: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. The returned table carries the node instance ID for targeted input events. Example: `ctx:endpoint("computed:widget"):call("button", "Run", 0xffffffff)`.

## computed:widget/slider

Signature: `(value: number, minimum: number, maximum: number, color: number, step: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Errors on invalid argument types. Example: `ctx:endpoint("computed:widget"):call("slider", 0.5, 0, 1, 0xffffffff, 0.01)`.

## computed:widget/progress

Signature: `(value: number, maximum: number, color: number, segments: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Errors on invalid argument types. Example: `ctx:endpoint("computed:widget"):call("progress", 5, 10, 0xffffffff, 10)`.

## computed:monitor/show

Signature: `(widgets: table) -> ()`. Runs on the computer thread, refreshes an adjacent monitor, performs a world side effect, and is unavailable in previews. The endpoint target selects the computer-relative face. Invalid widget records are ignored. Example: `ctx:endpoint("computed:monitor", "front"):call("show", widgets)`.
