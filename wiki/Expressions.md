# Expressions

Every output pin on a custom node has an `expression` field. The expression is evaluated **once per tick** to produce the pin's value.

---

## Variables available in expressions

| Source | How to use |
|---|---|
| Input pin | Use the pin's `name` directly (case-insensitive) |
| Constant | Use the constant's key from the `constants` object |
| State variable | Use the variable's `name` from the `state` array |
| Local variable | Assign with `name = expr` in a multi-step expression |

---

## Data types

Expressions work with two types:

- **number** — a 64-bit floating-point value  
- **string** — a UTF-16 text value

Boolean results are represented as numbers: `1.0` = true, `0.0` = false.  
The threshold for "truthy" is `> 0.5`.

---

## Operators

| Operator | Description |
|---|---|
| `+` | Numeric addition, **or string concatenation** if either operand is a string |
| `-` | Subtraction |
| `*` | Multiplication |
| `/` | Division |
| `%` | Modulo |
| `<` `<=` `>` `>=` | Comparison — returns `1.0` or `0.0` |
| `==` `!=` | Equality — returns `1.0` or `0.0` |
| `&&` | Logical AND (short-circuit) |
| `\|\|` | Logical OR (short-circuit) |
| `!` | Logical NOT |
| `(` `)` | Grouping |

Operator precedence (highest to lowest): `!` → `* / %` → `+ -` → `< <= > >=` → `== !=` → `&&` → `||`

---

## Multi-step programs

Statements are separated by `;`. The value of the **last statement** is returned as the output value. Intermediate statements are typically assignments.

```
"expression": "diff = A - B; abs(diff)"
```

Assignment syntax: `name = expression`

Local variables defined in one statement are visible in all later statements of the **same** expression. They do not persist across ticks (use [`state`](Persistent-State) for that).

```
"expression": "lo = min(A, B); hi = max(A, B); hi - lo"
```

---

## String literals

Use double-quoted or single-quoted strings inside expressions:

```json
"expression": "concat(\"Temp: \", str(A), \"°C\")"
```

```json
"expression": "concat('Speed: ', str(rpm))"
```

Escape sequences inside string literals:

| Sequence | Character |
|---|---|
| `\"` | Double quote |
| `\'` | Single quote |
| `\\` | Backslash |
| `\n` | Newline |
| `\t` | Tab |

---

## Function calls

Functions are called with parentheses: `name(arg1, arg2, ...)`.  
Functions are case-insensitive.

```
"expression": "clamp(A * gain, 0, 100)"
```

See the individual function reference pages:

- [Math Functions](Functions:-Math)
- [String Functions](Functions:-String)
- [Stateful Functions](Functions:-Stateful)
- [World Source Functions](Functions:-World-Sources)
- [Create Source Functions](Functions:-Create-Sources)

---

## Type coercion

| Coercion | Rule |
|---|---|
| Number → String | `str(x)` or automatic when used with `+` alongside a string |
| String → Number | `num(s)` — parses the string; returns `0` if not a valid number |
| Number → Bool | `> 0.5` |
| Bool → Number | `1.0` (true) or `0.0` (false) |

---

## Examples

```
"expression": "A + B"
```

```
"expression": "if(is_raining(), light_level() * 2, light_level())"
```

```
"expression": "d = A - B; sign(d) * clamp(abs(d), 0, 10)"
```

```
"expression": "concat(\"Speed: \", str(round(create_speed(\"front\"))), \" RPM\")"
```
