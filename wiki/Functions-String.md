# String Functions

These functions operate on string values. They are available in every expression and are case-insensitive.

String pins use `"type": "string"` in the JSON schema. Number and string values can be mixed freely — see [Type coercion](Expressions#type-coercion).

---

## Conversion

| Function | Returns | Description |
|---|---|---|
| `str(x)` | string | Convert a number to its string representation. Integers print without a decimal point. |
| `num(s)` | number | Parse a string to a number. Returns `0` if the string is not a valid number. |

---

## Building strings

| Function | Returns | Description |
|---|---|---|
| `concat(a, b, ...)` | string | Concatenate one or more values into a single string. Accepts any mix of numbers and strings. Requires at least one argument. |
| `format(fmt, args...)` | string | Java `String.format` style formatting. The first argument is the format string; remaining arguments are substituted. |

**`format` examples:**

```
format("%.2f RPM", speed)          → "12.34 RPM"
format("%d / %d", used, max)       → "7 / 27"
format("%s %s", "hello", "world")  → "hello world"
```

Common format specifiers: `%d` (integer), `%f` (float), `%.2f` (float, 2 decimal places), `%s` (string).

---

## Inspection

| Function | Returns | Description |
|---|---|---|
| `len(s)` | number | Number of characters in `s` |
| `contains(s, sub)` | 0 or 1 | `1` if `s` contains the substring `sub`, otherwise `0` |
| `starts_with(s, prefix)` | 0 or 1 | `1` if `s` starts with `prefix` |
| `ends_with(s, suffix)` | 0 or 1 | `1` if `s` ends with `suffix` |

---

## Transformation

| Function | Returns | Description |
|---|---|---|
| `upper(s)` | string | Convert `s` to uppercase |
| `lower(s)` | string | Convert `s` to lowercase |
| `substr(s, start)` | string | Substring from index `start` to the end of the string (0-based) |
| `substr(s, start, end)` | string | Substring from index `start` (inclusive) to `end` (exclusive). Both indices are clamped to `[0, len(s)]`. |
| `replace(s, old, new)` | string | Replace **all** occurrences of `old` with `new` in `s` |

**`substr` index note:** indices are 0-based characters. `substr("hello", 1, 3)` → `"el"`.

---

## The `+` operator with strings

When either operand of `+` is a string, the result is string concatenation:

```
"expression": "\"Speed: \" + str(rpm)"
```

This is equivalent to `concat("Speed: ", str(rpm))`.

---

## Examples

```json
"expression": "concat(\"Temp: \", str(round(A)), \"°C\")"
```

```json
"expression": "upper(biome_name())"
```

```json
"expression": "if(contains(block_id(\"front\"), \"chest\"), 1, 0)"
```

```json
"expression": "format(\"%.1f / %.1f SU\", create_stress(\"front\"), create_capacity(\"front\"))"
```

```json
"expression": "substr(block_id(\"front\"), 10)"
```
> Strips the `minecraft:` namespace prefix (10 characters) from a block ID.
