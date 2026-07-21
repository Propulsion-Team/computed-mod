# Example Computed addon

This source tree is documentation and is intentionally outside the project's compiled source sets.

Call `ExampleNodes.register()` from the addon's common registration phase. Call
`ExampleNodeClient.registerPresentations()` from a loader-provided client-only initialization hook.
Both calls must happen before Computed freezes the corresponding registry.

The example node accumulates a scaled input. Its immutable state is a dependency boundary, while its
optional presentation reuses the editor's generated property control before drawing custom content.
