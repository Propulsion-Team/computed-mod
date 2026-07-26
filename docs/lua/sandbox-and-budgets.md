# Sandbox and Execution Budgets

Each computer owns one Lua VM. Every node instance receives an isolated environment and state map. Compiled prototypes are globally cached by API version and SHA-256 source hash.

Available libraries are base primitives, `math`, `string`, `table`, `bit32`, and `coroutine`. The runtime does not expose `io`, `os`, `debug`, `package`, `require`, `load`, `loadfile`, `dofile`, or `luajava`. Java reflection and arbitrary Java objects are unreachable.

Each node invocation is limited to 50,000 Lua instructions. Each computer is limited to 500,000 Lua instructions per game tick. The private hook cannot be read or replaced by Lua. Limit errors abort the offending invocation, retain committed values, add an inline runtime diagnostic, and permit a later policy-driven retry.

Unloading a computer or replacing/removing a definition cancels its yielded coroutines.
