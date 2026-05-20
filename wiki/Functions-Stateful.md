# Stateful Functions

Stateful functions remember a value between ticks. Each call site is tracked independently — `prev(A)` in one output and `prev(B)` in another output are completely separate slots.

Call sites are identified by their **position in the expression**, so the same call always refers to the same stored slot. Do not generate call sites dynamically.

All stored values survive chunk unload and world reload (saved to NBT alongside [state variables](Persistent-State)).

---

## `prev(x)` / `prev(x, default)`

Returns the value that `x` had **last tick**. On the very first tick (no stored value yet), returns `default`. If `default` is omitted it is `0`.

| Argument | Type | Description |
|---|---|---|
| `x` | any | The value to observe |
| `default` | any | Value returned on the first tick. Defaults to `0`. |

**Returns:** the value of `x` from the previous tick.

```json
"expression": "prev(A)"
```

```json
"expression": "prev(light_level(), 15)"
```

> **Common use:** compute deltas — `A - prev(A)` gives the change since last tick.

---

## `rising(x)`

Returns `1` on the **single tick** that `x` transitions from false (`≤ 0.5`) to true (`> 0.5`). Returns `0` every other tick.

| Argument | Type | Description |
|---|---|---|
| `x` | number | Signal to watch (treated as boolean by the `> 0.5` threshold) |

**Returns:** `1` on the rising edge, `0` otherwise.

```json
"expression": "rising(comparator(\"front\") > 0)"
```

> **Common use:** detect the moment a redstone signal turns on.

---

## `falling(x)`

Returns `1` on the **single tick** that `x` transitions from true (`> 0.5`) to false (`≤ 0.5`). Returns `0` every other tick.

| Argument | Type | Description |
|---|---|---|
| `x` | number | Signal to watch |

**Returns:** `1` on the falling edge, `0` otherwise.

```json
"expression": "falling(is_raining())"
```

> **Common use:** detect when rain stops.

---

## `changed(x)`

Returns `1` on any tick that `x` is different from its value last tick. Works with both numbers and strings.

| Argument | Type | Description |
|---|---|---|
| `x` | any | Value to watch |

**Returns:** `1` on the tick `x` changes, `0` otherwise.

```json
"expression": "changed(block_id(\"front\"))"
```

```json
"expression": "changed(A)"
```

> **Common use:** detect when a signal or block ID changes.

---

## Difference from `state` variables

| | Stateful functions | `state` variables |
|---|---|---|
| Defined in | Expression (implicit, by call site) | `state` array in JSON |
| Readable from other outputs | No | Yes |
| Writable from expressions | No | Via `update` expression |
| Requires naming | No | Yes |

Use stateful functions when you only need to compare a value against its previous self. Use [state variables](Persistent-State) when you need to accumulate, count, or share a value across multiple outputs.

---

## Examples

Detect when a signal pulses (rises then falls):

```json
"outputs": [
  { "name": "Pulse", "expression": "rising(A > 0)" },
  { "name": "Delta", "expression": "A - prev(A)" }
]
```

Detect biome change:

```json
{ "name": "Biome Changed", "expression": "changed(biome_name())" }
```
