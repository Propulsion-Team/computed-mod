# Examples

All examples below can be saved as `.json` files in `config/computed/nodes/` and loaded with `/computed reload`.

---

## Simple addition

Two number inputs, one output that adds them.

```json
{
  "id": "computed:example_add",
  "label": "Example Add",
  "menuPath": ["Custom", "Examples"],
  "inputs": [
    { "name": "A", "color": "#00FF88" },
    { "name": "B", "color": "#00FF88" }
  ],
  "outputs": [
    { "name": "Result", "color": "#FF5555", "expression": "A + B" }
  ],
  "constants": {
    "bias": 0.0
  }
}
```

---

## Tick counter with reset

Uses [persistent state](Persistent-State). Counts ticks. A `> 0.5` signal on `Reset` zeroes the counter.

```json
{
  "id": "computed:example_counter",
  "label": "Tick Counter",
  "menuPath": ["Custom", "Examples"],
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

---

## Sensor label (string output)

Combines a number and a unit string into a formatted label.

```json
{
  "id": "computed:example_string_label",
  "label": "Sensor Label",
  "menuPath": ["Custom", "Examples"],
  "inputs": [
    { "name": "Value", "type": "number", "color": "#00FF88" },
    { "name": "Unit",  "type": "string", "color": "#FFC830" }
  ],
  "outputs": [
    {
      "name": "Label",
      "type": "string",
      "color": "#FFC830",
      "expression": "concat(str(round(Value)), \" \", Unit)"
    }
  ]
}
```

---

## Multi-step clamped difference

Demonstrates [multi-step expressions](Expressions#multi-step-programs) and [stateful functions](Functions:-Stateful).

```json
{
  "id": "computed:example_multistep",
  "label": "Clamped Difference",
  "menuPath": ["Custom", "Examples"],
  "inputs": [
    { "name": "A", "color": "#00FF88" },
    { "name": "B", "color": "#FF5555" }
  ],
  "outputs": [
    { "name": "Diff",    "color": "#FFFFFF",  "expression": "d = A - B; clamp(d, -10, 10)" },
    { "name": "AbsDiff", "color": "#FFAA00",  "expression": "abs(A - B)" },
    { "name": "Rising",  "color": "#55FF55",  "expression": "rising(A > B)" }
  ]
}
```

---

## Environment sensor

Reads weather, light, and biome data from the Computer's own position. No inputs needed.

```json
{
  "id": "computed:example_env_sensor",
  "label": "Environment Sensor",
  "menuPath": ["Custom", "Examples"],
  "outputs": [
    { "name": "Light",      "color": "#FFFF55", "expression": "light_level()" },
    { "name": "Raining",    "color": "#5599FF", "expression": "is_raining()" },
    { "name": "Thundering", "color": "#9955FF", "expression": "is_thundering()" },
    { "name": "IsDay",      "color": "#FFAA00", "expression": "is_day()" },
    {
      "name": "Biome",
      "type": "string",
      "color": "#55FF55",
      "expression": "biome_name()"
    }
  ]
}
```

---

## Fluid checker

Reads fluid type, presence, and fill level from the block in front.

```json
{
  "id": "computed:example_fluid_check",
  "label": "Fluid Checker",
  "menuPath": ["Custom", "Examples"],
  "outputs": [
    { "name": "Present", "color": "#5599FF", "expression": "fluid_present(\"front\")" },
    { "name": "Level",   "color": "#55CCFF", "expression": "fluid_level(\"front\")" },
    {
      "name": "Type",
      "type": "string",
      "color": "#FFC830",
      "expression": "fluid_type(\"front\")"
    }
  ]
}
```

---

## Dirt detector

Checks whether the block in front is dirt and returns its full ID.

```json
{
  "id": "computed:dirt_detector",
  "label": "Dirt Detector",
  "menuPath": ["Custom", "Detectors"],
  "outputs": [
    {
      "name": "Is Dirt",
      "color": "#8B5E3C",
      "expression": "block_is(\"minecraft:dirt\", \"front\")"
    },
    {
      "name": "Block ID",
      "type": "string",
      "color": "#AAAAAA",
      "expression": "block_id(\"front\")"
    }
  ]
}
```

---

## Cake detector

Reads a cake's state via comparator signal. A full cake returns `14`; each slice eaten reduces it by `2`.

```json
{
  "id": "computed:cake_detector",
  "label": "Cake Detector",
  "menuPath": ["Custom", "Detectors"],
  "outputs": [
    {
      "name": "Present",
      "color": "#FF5599",
      "expression": "comparator(\"front\") > 0"
    },
    {
      "name": "Signal",
      "color": "#FFAA00",
      "expression": "comparator(\"front\")"
    },
    {
      "name": "Slices Left",
      "color": "#FF88AA",
      "expression": "s = comparator(\"front\"); if(s > 0, s, 0)"
    },
    {
      "name": "Slices Eaten",
      "color": "#994422",
      "expression": "s = comparator(\"front\"); if(s > 0, 7 - s, 0)"
    }
  ]
}
```

---

## Create kinetic monitor *(requires Create mod)*

Reads speed, stress, and capacity from the kinetic block directly in front.

```json
{
  "id": "computed:example_create_kinetic",
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

> Place the Computer adjacent to any Create rotating block. Face the Computer toward it and use `"front"`. For a **source** (motor, portable engine), `Capacity` will show the SU generated and `Stress` will be `0`. For a **consumer** (press, millstone), `Stress` will show the SU drawn and `Capacity` will be `0`.
