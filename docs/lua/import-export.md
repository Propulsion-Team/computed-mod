# Import and Export

The Lua editor exports readable source to the clipboard or `config/computed/nodes/<namespace>_<path>.lua`. Imports accept raw `.lua` source from those locations.

For a new in-game definition, choose **User Nodes → New Lua Node…** in the Node Explorer. Applying the starter stores the source in the current computer and places the first instance. **Paste Lua** remains available from the empty-canvas context menu for clipboard imports.

Server validation checks the 64 KiB source limit, API version, definition ID, schema, hash, permissions, and the 256 embedded-definition limit. An identical ID/hash is a no-op. The same ID with different source requires explicit confirmation.

After replacement, instances recompile. Connections survive only when direction, stable port ID, and connection type all match. Removed or changed ports are reported before apply.

CMP1, CMP2, format-2 graphs, JSON custom nodes, Functions, and Sections are not importable. Loading legacy world data initializes an empty format-3 program without backup.
