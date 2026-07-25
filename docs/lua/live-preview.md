# Live Preview

The focused Lua editor compiles 250 milliseconds after the last edit. Validation runs in order: syntax, definition contract, schema, then endpoint availability.

The right pane uses the production semantic palette and node layout. Sample inputs and fields are editable. Running the preview advances isolated preview ticks and state; Reset recreates the node from its defaults.

When source becomes invalid, the last valid preview remains visible, dimmed, and marked stale. Inline diagnostics describe the new invalid source. Preview endpoint calls use deterministic fixtures. Methods without fixtures, including command side effects, return an unavailable error and never touch a world.

Applying source is separate from preview: the server recompiles it, checks permissions and size, and only then replaces the embedded definition.
