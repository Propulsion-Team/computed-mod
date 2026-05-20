# Custom Nodes (v2)

Custom nodes are loaded from:

- `config/computed/nodes/`

Loader behavior:

- Recursively loads `*.json`. Ignores `*.md` files.
- Skips invalid files and logs warnings/errors.
- Skips IDs that conflict with built-in or already-registered nodes.
- **Reload live**: `/computed reload`

---

## JSON schema

```json
{
  "id": "computed:my_node",
  "label": "My Node",
  "menuPath": ["Custom", "Math"],
  "inputs": [
    { "name": "A",   "type": "number", "color": "#00FF88" },
    { "name": "Tag", "type": "string", "color": "#FFC830" }
  ],
  "outputs": [
    { "name": "Sum",    "type": "number", "color": "#FF5555", "expression": "A + gain" },
    { "name": "Label",  "type": "string", "color": "#FFC830", "expression": "concat(Tag, \" = \", str(A))" }
  ],
  "constants": {
    "gain": 2.0
  },
  "state": [
    { "name": "count", "init": 0, "update": "count + 1" }
  ]
}
```

### Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | ✓ | `namespace:path`. Must be unique. |
| `label` | string | ✓ | Display name shown on the node. |
| `menuPath` | string[] | | Category path in the add-node menu. Defaults to `["Custom"]`. |
| `inputs` | array | | List of input pin specs (see below). |
| `outputs` | array | ✓ | List of output pin specs. Must have at least one. |
| `constants` | object | | Named numeric constants accessible in all expressions. |
| `state` | array | | Persistent state variables (see **Persistent state** section). |

### Pin spec (`inputs` / `outputs`)

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | string | ✓ | Pin label. Must be unique across all inputs and outputs. |
| `type` | `"number"` \| `"string"` | `"number"` | Data type of the pin. |
| `color` | `"#RRGGBB"` or `"#AARRGGBB"` | auto | Pin accent colour. |
| `expression` | string | ✓ (outputs only) | Expression that computes this output's value every tick. |

---

## Expressions

### Variables

Available in all expressions:
- Input pin names (case-insensitive)
- Constant names from `constants`
- State variable names from `state`
- Local variables defined in the same multi-step expression

### Multi-step programs

Statements are separated by `;`. The value of the **last statement** is the result.
Assignment (`name = expr`) writes a local variable usable in later statements of the same expression.

```
"expression": "diff = A - B; abs(diff)"
```

### String literals

Use `"..."` or `'...'` in expressions:

```
"expression": "concat(\"temp: \", str(A), \"°C\")"
```

### Operators

`+ - * / %`, comparisons (`< <= > >= == !=`), logical (`&& || !`), parentheses.

`+` performs string concatenation when either operand is a string.

Boolean values: `1.0` = true, `0.0` = false. Threshold: `> 0.5`.

---

## Built-in functions

### Math

| Function | Description |
|---|---|
| `min(a, b)` | Minimum |
| `max(a, b)` | Maximum |
| `abs(x)` | Absolute value |
| `sqrt(x)` | Square root |
| `pow(a, b)` | Power |
| `floor(x)` | Floor |
| `ceil(x)` | Ceiling |
| `round(x)` | Round to nearest |
| `sign(x)` | Signum |
| `clamp(x, lo, hi)` | Clamp x to [lo, hi] |
| `lerp(lo, hi, t)` | Linear interpolation |
| `log(x)` / `log(x, base)` | Natural or base logarithm |
| `exp(x)` | e^x |
| `sin(x)`, `cos(x)`, `tan(x)` | Trig (radians) |
| `asin(x)`, `acos(x)`, `atan(x)` | Inverse trig |
| `atan2(y, x)` | Two-argument arctangent |
| `hypot(a, b)` | Hypotenuse |
| `rad(deg)` | Degrees → radians |
| `deg(rad)` | Radians → degrees |
| `if(cond, a, b)` | Conditional — returns `a` if `cond > 0.5`, else `b` |

### String

| Function | Description |
|---|---|
| `str(x)` | Convert number to string |
| `num(s)` | Parse string to number |
| `concat(a, b, ...)` | Concatenate any number of values |
| `len(s)` | String length |
| `substr(s, start[, end])` | Substring |
| `upper(s)` / `lower(s)` | Case conversion |
| `contains(s, sub)` | 1 if s contains sub |
| `starts_with(s, prefix)` | 1 if s starts with prefix |
| `ends_with(s, suffix)` | 1 if s ends with suffix |
| `replace(s, old, new)` | Replace all occurrences |
| `format(fmt, args...)` | Java `String.format` style |

---

## Persistent state

State variables hold their value across ticks. Define them in the `state` array:

```json
"state": [
  { "name": "count", "init": 0,   "update": "count + 1" },
  { "name": "prev",  "init": 0.0, "update": "A" }
]
```

- `name` — variable name (accessible in output expressions and other update expressions)
- `init` — initial value (number or string). Defaults to `0`.
- `update` — expression evaluated **each tick** to produce the next value. The snapshot of the *previous tick* is used for all updates, so updates are independent of each other.

State is saved to NBT and survives chunk unload / world reload.

### Stateful helper functions

These are implemented on top of the state store and keyed by call-site position:

| Function | Description |
|---|---|
| `prev(x)` / `prev(x, default)` | Returns the value `x` had last tick |
| `rising(x)` | 1 on the tick `x` transitions from false → true |
| `falling(x)` | 1 on the tick `x` transitions from true → false |
| `changed(x)` | 1 when `x` is different from last tick |

---

## World source functions

These read from the Minecraft world. Available in all JSON node expressions. All face arguments
accept `"front"`, `"back"`, `"left"`, `"right"`, `"top"`, `"bottom"` (case-insensitive).
Return defaults (0 / "") when executed outside a world tick.

### Environment

| Function | Returns | Description |
|---|---|---|
| `light_level()` | 0–15 | Max of sky + block light at computer |
| `light_sky()` | 0–15 | Sky light level |
| `light_block()` | 0–15 | Block light level |
| `is_raining()` | 0/1 | Is it raining? |
| `is_thundering()` | 0/1 | Is it thundering? |
| `is_day()` | 0/1 | Is it daytime? |
| `biome_temp()` | float | Biome base temperature |
| `biome_downfall()` | float | Biome downfall value |
| `biome_name()` | string | Biome resource location (e.g. `"minecraft:plains"`) |

### Block

| Function | Returns | Description |
|---|---|---|
| `block_id(face)` | string | Block's registry ID at that face (e.g. `"minecraft:dirt"`) |
| `block_is(id, face)` | 0/1 | 1 if the block at `face` matches the given registry ID |

### Fluid

| Function | Returns | Description |
|---|---|---|
| `fluid_present(face)` | 0/1 | Is there a fluid at that face? |
| `fluid_level(face)` | 0–8 | Fluid fill amount (0 = none) |
| `fluid_type(face)` | string | `"water"`, `"lava"`, or `""` |

### Inventory / container

| Function | Returns | Description |
|---|---|---|
| `container_slots(face)` | int | Slot count of adjacent inventory |
| `container_count(face)` | int | Total items across all slots |
| `container_fill(face)` | 0.0–1.0 | Fill fraction (used / capacity) |
| `comparator(face)` | 0–15 | Analog comparator signal (fallback: weak redstone) |

---

## Create mod source functions

Only available when the Create mod is installed. Face argument follows the same convention as world functions.

| Function | Returns | Description |
|---|---|---|
| `create_kinetic(face)` | 0/1 | Is the adjacent block a Create kinetic block? |
| `create_speed(face)` | float | Speed in RPM (signed; negative = reversed) |
| `create_stress(face)` | float | Stress currently applied by that block |
| `create_capacity(face)` | float | Stress capacity contributed (sources only) |

> **Note:** Create redstone link transmit/receive uses the dedicated **Create Redstone Link Sender/Receiver** nodes in the node graph, not expression functions, because they require per-instance network actor registration.

---

## Examples

See the `docs/examples/` folder for ready-to-use JSON files.
