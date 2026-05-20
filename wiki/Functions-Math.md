# Math Functions

All math functions are available in every expression on every custom node. Function names are case-insensitive.

---

## Single-argument functions

| Function | Returns | Description |
|---|---|---|
| `abs(x)` | number | Absolute value of `x` |
| `sqrt(x)` | number | Square root of `x`. Negative inputs are clamped to `0` before the call. |
| `floor(x)` | number | Round down to nearest integer |
| `ceil(x)` | number | Round up to nearest integer |
| `round(x)` | number | Round to nearest integer (half-even / banker's rounding) |
| `sign(x)` | number | Signum: `-1.0`, `0.0`, or `1.0` |
| `exp(x)` | number | Euler's number raised to the power `x` (eˣ) |
| `sin(x)` | number | Sine of `x` (radians) |
| `cos(x)` | number | Cosine of `x` (radians) |
| `tan(x)` | number | Tangent of `x` (radians) |
| `asin(x)` | number | Arc sine of `x` — result in radians |
| `acos(x)` | number | Arc cosine of `x` — result in radians |
| `atan(x)` | number | Arc tangent of `x` — result in radians |
| `rad(deg)` | number | Convert degrees to radians |
| `deg(rad)` | number | Convert radians to degrees |

---

## Two-argument functions

| Function | Returns | Description |
|---|---|---|
| `min(a, b)` | number | Smaller of `a` and `b` |
| `max(a, b)` | number | Larger of `a` and `b` |
| `pow(a, b)` | number | `a` raised to the power `b` |
| `atan2(y, x)` | number | Two-argument arc tangent — result in radians |
| `hypot(a, b)` | number | Hypotenuse: `sqrt(a² + b²)` |
| `log(x)` | number | Natural logarithm (base e). Values ≤ 0 are clamped to a small positive epsilon. |
| `log(x, base)` | number | Logarithm of `x` in the given `base`. Returns `0` if `base ≤ 0`. |

---

## Three-argument functions

| Function | Returns | Description |
|---|---|---|
| `clamp(x, lo, hi)` | number | Restrict `x` to the range `[lo, hi]` |
| `lerp(lo, hi, t)` | number | Linear interpolation between `lo` and `hi` at fraction `t`. `t = 0` → `lo`, `t = 1` → `hi`. No clamping applied to `t`. |

---

## Control flow

| Function | Returns | Description |
|---|---|---|
| `if(cond, a, b)` | any | Returns `a` if `cond > 0.5`, otherwise `b`. Both `a` and `b` are evaluated before the check. |

---

## Examples

```
"expression": "clamp(A * 2, 0, 100)"
```

```
"expression": "lerp(min_val, max_val, t)"
```

```
"expression": "sqrt(pow(dx, 2) + pow(dy, 2))"
```

```
"expression": "if(A > threshold, 1, 0)"
```

```
"expression": "deg(atan2(Y, X))"
```
