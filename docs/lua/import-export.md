# Import and Export

The graph toolbar has one **Import / Export** menu beside the zoom controls. It keeps graph and node package actions together and uses the same hover and selection treatment as the editor's other menus. Graph packages are stored under the Minecraft game directory in `computed/graphs/`; import presents the available `.computed` files and export asks for a name. Existing files can be overwritten or saved with the next ` (n)` suffix. A graph package is a validated ZIP archive containing `manifest.json`, `graph.json`, and only the embedded Lua definitions used by the graph.

Node source files use `computed/nodes/` and the `.lua` extension. **Import Node** is in the toolbar menu, while **Export Selected Node** is available there and in a user node's right-click menu. Import opens the selected Lua file in the Lua editor; export writes the source directly using the selected user node's title. Bundled and integration nodes cannot be exported, edited, or deleted.

Selected canvas nodes support Ctrl+C, Ctrl+X, and Ctrl+V. Pasted nodes are placed at the cursor and retain connections between nodes in the copied selection. Ctrl+D immediately clones the selection 24 pixels right and down; repeated presses continue from the latest clone to form a ladder.

The Lua editor also supports normal text selection and clipboard shortcuts. Use Ctrl+C to copy selected source and Ctrl+V to paste into editable user definitions. **Paste Lua** remains available from the empty-canvas context menu for clipboard imports.

For a new in-game definition, choose **User Nodes → New Lua Node…** in the Node Explorer. Saving the starter stores the source in the current computer and places the first instance.

Server validation checks the 64 KiB source limit, API version, definition ID, schema, hash, permissions, and the 256 embedded-definition limit. An identical ID/hash is a no-op. The same ID with different source requires explicit confirmation.

After replacement, instances recompile. Connections survive only when direction, stable port ID, and connection type all match. Removed or changed ports are reported before save.

CMP1, CMP2, format-2 graphs, JSON custom nodes, Functions, and Sections are not importable. Loading legacy world data initializes an empty format-3 program without backup.
