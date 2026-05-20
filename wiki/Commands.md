# Commands

Computed registers a single command tree under `/computed`.

---

## `/computed reload`

Reloads all custom node JSON files from `config/computed/nodes/` at runtime. No game restart is needed.

**Usage:**

```
/computed reload
```

**Output (chat message):**

```
Custom nodes reloaded: loaded=3, skipped=0, warnings=0, errors=0
```

| Field | Description |
|---|---|
| `loaded` | Number of nodes successfully registered this reload |
| `skipped` | Number of files/nodes skipped (ID conflicts, duplicate IDs, etc.) |
| `warnings` | Non-fatal issues logged (e.g. unknown optional fields) |
| `errors` | Fatal parse or validation errors — nodes in these files were not loaded |

**Return value:** Returns `1` if there were no errors, `0` if any errors occurred (for use in command blocks or other command chaining).

---

## Reload behavior

- All previously loaded custom nodes from the last reload are **replaced** by the new set.
- Built-in nodes (defined in Java) are never affected by reload.
- If a JSON file has a parse error, that file is skipped entirely — other valid files in the same run still load.
- If a node ID conflicts with a built-in node, a warning is logged and the file is skipped.
- The `config/computed/nodes/` directory is created automatically if it does not exist.

---

## Server-side note

The command is registered on the server side. In single-player it runs in the integrated server context. On a dedicated server, any operator can run it.

---

## Log output

In addition to the chat message, Computed logs detailed per-file results to the game log at `INFO` level. Errors are logged at `ERROR` level and warnings at `WARN` level. Check `logs/latest.log` if you need to diagnose a failed reload.

Example log lines:

```
[custom-nodes] Loaded computed:my_node from my_node.json
[custom-nodes] WARN Skipped computed:builtin_conflict — ID already registered
[custom-nodes] reload complete: loaded=2, skipped=1, warnings=1, errors=0, root=.../config/computed/nodes
```
