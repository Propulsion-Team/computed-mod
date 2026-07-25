# Computed

Computed is a NeoForge 1.21.1 programmable Lua node-graph mod. LuaJ powers one sandboxed VM per
computer while Computed owns graph scheduling, safe Minecraft endpoints, persistence, networking,
the editor, monitor rendering, and integrations.

Program format 3 stores one root graph and an embedded Lua definition library. Legacy graphs,
Functions, Sections, JSON custom nodes, CMP1, and CMP2 are intentionally discarded or rejected
without migration.

Start with the [Lua authoring guide](docs/lua/authoring-guide.md), the
[Lua method reference](docs/lua/lua-api-reference.md), and the
[Java endpoint API](docs/lua/endpoint-api.md).

Build and verify with:

```text
./gradlew clean check build
```

The `check` lifecycle verifies that the built JAR contains no `dev/devce/websnodelib/**` classes or
`assets/websnodelib/**` resources.


# Credits – Third-Party Code

Computed now ships its own node engine, persistence model, runtime, and editor under the
`dev.propulsionteam.computed` and `assets/computed` namespaces. Portions of the editor and built-in
node implementation were derived from **Web's Node Lib** under the MIT License; the original package
and resource namespaces are not included in the distributable. Computed-specific changes and assets
remain governed by the main [license](LICENSE.txt).

**Web's Node Lib**  
• **Repository:** [webyep-art/webs_node_lib](https://github.com/webyep-art/webs_node_lib)  
• **Author:** webyep  
• **License text:** See `LICENSE-webs_node_lib.txt` (included in this repository)
