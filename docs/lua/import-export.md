# Clipboard Import

The Lua editor supports normal text selection and clipboard shortcuts. Use Ctrl+C to copy selected source and Ctrl+V to paste into editable user definitions. **Paste Lua** remains available from the empty-canvas context menu for clipboard imports.

For a new in-game definition, choose **User Nodes → New Lua Node…** in the Node Explorer. Saving the starter stores the source in the current computer and places the first instance.

Server validation checks the 64 KiB source limit, API version, definition ID, schema, hash, permissions, and the 256 embedded-definition limit. An identical ID/hash is a no-op. The same ID with different source requires explicit confirmation.

After replacement, instances recompile. Connections survive only when direction, stable port ID, and connection type all match. Removed or changed ports are reported before save.

CMP1, CMP2, format-2 graphs, JSON custom nodes, Functions, and Sections are not importable. Loading legacy world data initializes an empty format-3 program without backup.
