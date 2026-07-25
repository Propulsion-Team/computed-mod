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

## computed:command/run

Signature: `(string) -> ()`. Runs on the computer thread, does not yield, performs a command side effect, and is unavailable in preview. Errors on a missing host, invalid argument, or command failure. Example: `ctx:endpoint("computed:command"):call("run", "say hello")`.

## computed:widget/text

Signature: `(string) -> table`. Runs on the computer thread, does not yield, has no world side effect, and uses the same deterministic fixture in preview. Returns `{ type = "text", text = value }`. Example: `ctx:output("widget", ctx:endpoint("computed:widget"):call("text", "Ready"))`.
