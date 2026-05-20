# Computed — Wiki

**Computed** is a NeoForge 1.21.1 mod that adds programmable in-world computers driven by a visual node graph. Nodes are wired together to compute values each tick and drive outputs (redstone, displays, etc.).

This wiki covers the **data-driven custom node system**, which lets you define your own nodes in JSON files without writing any Java.

---

## Pages

| Page | Description |
|---|---|
| [Custom Nodes](Custom-Nodes) | JSON schema reference — how to define a node |
| [Expressions](Expressions) | Expression syntax, operators, multi-step programs |
| [Math Functions](Functions-Math) | Built-in math functions |
| [String Functions](Functions-String) | Built-in string functions |
| [Stateful Functions](Functions-Stateful) | `prev`, `rising`, `falling`, `changed` |
| [Persistent State](Persistent-State) | Per-node state variables that survive across ticks |
| [World Source Functions](Functions-World-Sources) | Read from the Minecraft world (light, weather, blocks, fluids, inventories) |
| [Create Source Functions](Functions-Create-Sources) | Read kinetic data from the Create mod |
| [Commands](Commands) | In-game commands (`/computed reload`) |
| [Examples](Examples) | Ready-to-use JSON node files |

---

## Quick start

1. Start your world. The folder `config/computed/nodes/` is created automatically.
2. Drop a `.json` file there (see [Custom Nodes](Custom-Nodes) for the schema).
3. Run `/computed reload` in-game — no restart needed.
4. Open a Computer block and find your node in the **Add Node** menu under its `menuPath`.
