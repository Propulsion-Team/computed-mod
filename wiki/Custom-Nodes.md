# Custom Nodes

Custom nodes let you define your own node graph nodes entirely in JSON — no Java required.

---

## File location

Place `.json` files (any depth of subdirectories) inside:

```
config/computed/nodes/
```

The loader scans **recursively** for `*.json`. Files with other extensions (e.g. `.md`) are silently ignored.

---

## Reload without restarting

```
/computed reload
```

Reloads all JSON files from the nodes folder at runtime. The chat prints a summary:

```
Custom nodes reloaded: loaded=3, skipped=0, warnings=0, errors=0
```

A node whose ID conflicts with a built-in or already-registered node is skipped with a warning.

---

## Full JSON schema

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
    { "name": "Sum",   "type": "number", "color": "#FF5555", "expression": "A + gain" },
    { "name": "Label", "type": "string", "color": "#FFC830", "expression": "concat(Tag, \" = \", str(A))" }
  ],
  "constants": {
    "gain": 2.0
  },
  "state": [
    { "name": "count", "init": 0, "update": "count + 1" }
  ]
}
```

---

## Top-level fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | ✓ | `namespace:path`. Must be unique across all nodes. |
| `label` | string | ✓ | Display name shown on the node tile. |
| `menuPath` | string[] | | Category path in the Add Node menu. Defaults to `["Custom"]`. |
| `inputs` | pin[] | | Input pins. May be omitted if the node has no inputs. |
| `outputs` | pin[] | ✓ | Output pins. At least one required. |
| `constants` | object | | Named numeric constants available in all expressions on this node. |
| `state` | state[] | | Persistent per-tick state variables. See [Persistent State](Persistent-State). |

---

## Pin spec (`inputs` and `outputs`)

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | string | ✓ | Pin label. Must be unique across **all** pins on this node (inputs + outputs). |
| `type` | `"number"` \| `"string"` | `"number"` | Data type carried by this pin. |
| `color` | `"#RRGGBB"` or `"#AARRGGBB"` | auto | Pin accent colour in the UI. |
| `expression` | string | ✓ (outputs only) | Expression evaluated each tick to produce this output's value. See [Expressions](Expressions). |

Input pins do **not** have an `expression` field — their value comes from whatever is wired into them.

---

## ID naming rules

- Format: `namespace:path`  
- Both segments may contain lowercase letters, digits, `_`, `-`, `.`
- Must be globally unique — conflicts with built-in nodes cause the file to be skipped
- Recommended: use your own namespace (e.g. `mypack:node_name`) to avoid clashes

---

## menuPath

Controls where the node appears in the Add Node menu. Each string is a nested category level.

```json
"menuPath": ["Automation", "Sensors"]
```

Omitting `menuPath` places the node under `["Custom"]`.

---

## constants

A JSON object mapping names to fixed numeric values. Constants are available by name in all expressions on the node.

```json
"constants": {
  "pi": 3.14159,
  "threshold": 0.5
}
```

Constants cannot be changed at runtime and are not saved to NBT.

---

## See also

- [Expressions](Expressions) — expression syntax
- [Persistent State](Persistent-State) — the `state` array
- [Examples](Examples) — complete working node files
