# World Source Functions

World source functions read live data from the Minecraft world. They are available in every expression on every custom node.

All functions return their default value (`0` or `""`) when called outside a server-side world tick (e.g. during expression validation at load time).

---

## Face arguments

Many functions take a `face` argument that specifies which adjacent block to read. All face strings are **case-insensitive**.

| Value | Direction |
|---|---|
| `"front"` | The direction the Computer faces |
| `"back"` | Opposite of front |
| `"left"` | Left of the Computer's facing direction |
| `"right"` | Right of the Computer's facing direction |
| `"top"` | Directly above |
| `"bottom"` | Directly below |

The face is relative to the **Computer block's own facing**. A Computer placed facing north treats `"front"` as north, `"right"` as east, etc.

---

## Environment

These functions read conditions at the Computer's own position.

| Function | Returns | Description |
|---|---|---|
| `light_level()` | 0–15 | Maximum of sky light and block light at the Computer's position |
| `light_sky()` | 0–15 | Sky light level at the Computer |
| `light_block()` | 0–15 | Block light level (from torches, glowstone, etc.) at the Computer |
| `is_raining()` | 0 or 1 | `1` if it is currently raining in the dimension |
| `is_thundering()` | 0 or 1 | `1` if a thunderstorm is active |
| `is_day()` | 0 or 1 | `1` if the in-game time is daytime |
| `biome_temp()` | float | Biome base temperature at the Computer's position |
| `biome_downfall()` | float | Biome downfall value (moisture) |
| `biome_name()` | string | Biome registry ID, e.g. `"minecraft:plains"` |

---

## Block

These functions read the block at an adjacent position.

| Function | Returns | Description |
|---|---|---|
| `block_id(face)` | string | Registry ID of the block at `face`, e.g. `"minecraft:stone"` |
| `block_is(id, face)` | 0 or 1 | `1` if the block at `face` matches the given registry ID exactly |

**`block_id` example:**

```json
"expression": "block_id(\"front\")"
```

Returns `"minecraft:air"` for empty air, `"minecraft:water"` for a water source block, etc.

**`block_is` example:**

```json
"expression": "block_is(\"minecraft:dirt\", \"front\")"
```

Returns `1` if the block directly in front is dirt, `0` otherwise.

> **Tip:** Use `contains(block_id("front"), "chest")` to match any block whose ID contains the word "chest".

---

## Fluid

These functions read fluid state at an adjacent position.

| Function | Returns | Description |
|---|---|---|
| `fluid_present(face)` | 0 or 1 | `1` if any fluid occupies the block at `face` |
| `fluid_level(face)` | 0–8 | Fluid fill amount (8 = full source block, lower = flowing). Returns `0` if no fluid. |
| `fluid_type(face)` | string | `"water"`, `"lava"`, or `""` if no fluid or an unrecognised fluid type. |

**Example — detect full water tank:**

```json
"expression": "fluid_present(\"top\") && fluid_level(\"top\") == 8"
```

---

## Inventory / container

These functions read inventory data from an adjacent block that exposes an item handler (chest, barrel, hopper, furnace, etc.).

| Function | Returns | Description |
|---|---|---|
| `container_slots(face)` | int | Total number of slots in the adjacent inventory. Returns `0` if no inventory. |
| `container_count(face)` | int | Total number of items stacked across all slots |
| `container_fill(face)` | 0.0–1.0 | Fill fraction: `total_items / total_capacity`. Each slot's capacity is used if available, otherwise assumed to be 64. Returns `0` if no inventory. |
| `comparator(face)` | 0–15 | Analog comparator output signal of the adjacent block. Falls back to the block's weak redstone signal if the block does not implement `getAnalogOutputSignal`. |

**Example — trigger when chest is more than half full:**

```json
"expression": "container_fill(\"front\") > 0.5"
```

**Example — read cake slices eaten via comparator:**

```json
"expression": "7 - comparator(\"front\")"
```
> A full cake returns `14` from comparator; each slice eaten reduces it by 2.

---

## Examples

### Environment sensor node

```json
{
  "id": "computed:env_sensor",
  "label": "Environment Sensor",
  "outputs": [
    { "name": "Light",      "color": "#FFFF55", "expression": "light_level()" },
    { "name": "Raining",    "color": "#5599FF", "expression": "is_raining()" },
    { "name": "Thundering", "color": "#9955FF", "expression": "is_thundering()" },
    { "name": "IsDay",      "color": "#FFAA00", "expression": "is_day()" },
    { "name": "Biome", "type": "string", "color": "#55FF55", "expression": "biome_name()" }
  ]
}
```

### Fluid presence checker

```json
{
  "id": "computed:fluid_check",
  "label": "Fluid Checker",
  "outputs": [
    { "name": "Present", "color": "#5599FF", "expression": "fluid_present(\"front\")" },
    { "name": "Level",   "color": "#55CCFF", "expression": "fluid_level(\"front\")" },
    { "name": "Type", "type": "string", "color": "#FFC830", "expression": "fluid_type(\"front\")" }
  ]
}
```

### Dirt detector

```json
{
  "id": "computed:dirt_detector",
  "label": "Dirt Detector",
  "outputs": [
    { "name": "Is Dirt",  "color": "#8B5E3C", "expression": "block_is(\"minecraft:dirt\", \"front\")" },
    { "name": "Block ID", "type": "string", "color": "#AAAAAA", "expression": "block_id(\"front\")" }
  ]
}
```
