# Persistent State

State variables hold a value **across ticks**. They are defined in the `state` array and updated every tick using an expression. Their values are saved to NBT so they survive chunk unload and world reload.

---

## Defining state variables

Add a `state` array to your node JSON:

```json
"state": [
  { "name": "count", "init": 0,   "update": "count + 1" },
  { "name": "prev",  "init": 0.0, "update": "A" }
]
```

Each entry is a state variable spec:

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | ✓ | Variable name, accessible in output expressions and other `update` expressions. |
| `init` | number or string | | Initial value when the node is first created. Defaults to `0`. |
| `update` | string | | Expression evaluated **each tick** to produce the next value. |

If `update` is omitted, the variable keeps its initial value forever (effectively a constant you can pre-set via NBT).

---

## How updates are evaluated

State updates use a **pre-tick snapshot**: every `update` expression sees the values from the *previous* tick, not values other variables are being updated to this tick. This means updates are independent of each other regardless of their order in the array.

```json
"state": [
  { "name": "a", "init": 1, "update": "b + 1" },
  { "name": "b", "init": 0, "update": "a + 1" }
]
```

Both `a` and `b` see each other's *old* values — there is no dependency ordering issue.

---

## Using state variables in output expressions

State variable names are available directly in any output expression, alongside input names and constants:

```json
"outputs": [
  { "name": "Count", "expression": "count" },
  { "name": "Delta", "expression": "A - prev" }
]
```

---

## String state

State variables can hold strings. Set `"init"` to a string literal to mark the variable as a string type:

```json
"state": [
  { "name": "last_biome", "init": "", "update": "biome_name()" }
]
```

---

## NBT persistence

All state variable values are written to the Computer block entity's NBT data each time the node is evaluated. They are restored when the chunk is loaded. This makes them suitable for:

- Counting ticks or events across play sessions
- Remembering the last seen value of a signal
- Accumulating totals over time

---

## Examples

### Tick counter with reset

```json
{
  "id": "computed:example_counter",
  "label": "Tick Counter",
  "inputs": [
    { "name": "Reset", "color": "#FF5555" }
  ],
  "outputs": [
    { "name": "Count", "color": "#00FF88", "expression": "count" }
  ],
  "state": [
    { "name": "count", "init": 0, "update": "if(Reset > 0.5, 0, count + 1)" }
  ]
}
```

### Remember last non-zero value

```json
"state": [
  { "name": "last", "init": 0, "update": "if(A != 0, A, last)" }
]
```

### Track a running maximum

```json
"state": [
  { "name": "peak", "init": 0, "update": "max(A, peak)" }
]
```

### Delta (difference from last tick)

```json
"state": [
  { "name": "prev_a", "init": 0, "update": "A" }
],
"outputs": [
  { "name": "Delta", "expression": "A - prev_a" }
]
```
