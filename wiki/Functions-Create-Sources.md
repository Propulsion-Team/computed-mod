# Create Source Functions

These functions read kinetic data from the [Create mod](https://www.curseforge.com/minecraft/mc-mods/create). They are only available when Create is installed. All face arguments follow the same [face convention](Functions-World-Sources#face-arguments) as world source functions.

If Create is not installed, all four functions return `0` silently — no error is thrown.

---

## Functions

### `create_kinetic(face)`

Returns `1` if the adjacent block at `face` is a Create kinetic block entity (a block that participates in the stress/rotation network), `0` otherwise.

| Argument | Type | Description |
|---|---|---|
| `face` | string | Which neighbor to check (`"front"`, `"back"`, `"left"`, `"right"`, `"top"`, `"bottom"`) |

**Returns:** `0` or `1`

```json
"expression": "create_kinetic(\"front\")"
```

---

### `create_speed(face)`

Returns the rotational speed of the kinetic block at `face` in **RPM** (rotations per minute). The value is signed — negative means the shaft is spinning in the opposite direction.

| Argument | Type | Description |
|---|---|---|
| `face` | string | Adjacent face to read |

**Returns:** signed float (RPM). `0` if no kinetic block or Create is absent.

```json
"expression": "create_speed(\"front\")"
```

> Use `abs(create_speed("front"))` if direction does not matter.

---

### `create_stress(face)`

Returns the **stress (SU) consumed** by the kinetic block at `face`. This is the actual stress units displayed in the Create UI tooltip.

| Argument | Type | Description |
|---|---|---|
| `face` | string | Adjacent face to read |

**Returns:** float (SU). `0` if no kinetic block, if the block is a pure source (e.g. a motor), or if Create is absent.

```json
"expression": "create_stress(\"front\")"
```

> **Note:** Sources (motors, engines, waterwheels) return `0` for `create_stress`. Use `create_capacity` for them.

---

### `create_capacity(face)`

Returns the **stress capacity (SU) generated** by the kinetic block at `face`. This is the maximum SU the block contributes to its kinetic network.

| Argument | Type | Description |
|---|---|---|
| `face` | string | Adjacent face to read |

**Returns:** float (SU). `0` if no kinetic block, if the block is a pure consumer (e.g. a mechanical press), or if Create is absent.

```json
"expression": "create_capacity(\"front\")"
```

> **Note:** Consumers (mechanical press, millstone, etc.) return `0` for `create_capacity`. Use `create_stress` for them.

---

## Stress model summary

Create's stress system distinguishes between *sources* and *consumers*:

| Block type | `create_stress` | `create_capacity` |
|---|---|---|
| Source (motor, portable engine, waterwheel) | `0` | SU provided |
| Consumer (mechanical press, millstone, fan) | SU drawn | `0` |
| Mixed (some gearboxes) | SU drawn | SU provided |

The returned values match what the Create UI shows in the block's tooltip — they already account for the block's current speed.

---

## Example node

```json
{
  "id": "computed:kinetic_monitor",
  "label": "Kinetic Monitor",
  "menuPath": ["Custom", "Examples", "Create"],
  "outputs": [
    { "name": "IsKinetic", "color": "#FFAA00", "expression": "create_kinetic(\"front\")" },
    { "name": "Speed",     "color": "#FF6600", "expression": "create_speed(\"front\")" },
    { "name": "Stress",    "color": "#FF3333", "expression": "create_stress(\"front\")" },
    { "name": "Capacity",  "color": "#33FF88", "expression": "create_capacity(\"front\")" }
  ]
}
```

---

## Network load ratio

Compute the percentage of a kinetic network's capacity currently in use:

```json
"expression": "create_stress(\"front\") / max(create_capacity(\"front\"), 1) * 100"
```

> Returns `0`–`100`+ (over 100 means overstressed). Cap with `clamp(..., 0, 100)` if desired.

---

## Formatted readout (string output)

```json
{
  "name": "Status",
  "type": "string",
  "expression": "format(\"%.0f / %.0f SU @ %.0f RPM\", create_stress(\"front\"), create_capacity(\"front\"), abs(create_speed(\"front\")))"
}
```
