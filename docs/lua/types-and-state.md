# Types and State Serialization

Connection types are `number`, `boolean`, `string`, `event`, `widget`, and `table`. Connections require exactly matching types and each input accepts at most one edge.

Persistent values support Lua `nil`, booleans, finite numbers, strings, and acyclic tables. Table keys must be strings or integers. The maximum table depth is 16 and all program data shares the four-megabyte limit.

Functions, userdata, threads, cyclic tables, non-finite numbers, fractional numeric keys, and unsupported Java values are rejected. A serialization failure fails only the invocation and preserves the last committed state and outputs.

State is copied into an invocation. `ctx:set_state` stages a replacement; successful callback return commits the entire staged map. Yielded callbacks keep staged changes private until resumption completes.
