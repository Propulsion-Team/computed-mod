# Computed

Computed is a NeoForge 1.21.1 programmable node-graph mod. Its node API, persisted program format,
runtime, and editor live entirely under Computed-owned Java and resource namespaces, avoiding the
split-package conflict caused by the formerly vendored Web's Node Lib.

Legacy worlds and imports are migrated on load and written as version-2 `ComputedProgram` data after
the next successful save. Share exports use `CMP2`; `CMP1` and legacy Base64/SNBT remain import-only.

Addon authors: see the [public node API](docs/node-api.md), the
[Web's Node Lib migration guide](docs/node-migration.md), and the
[example addon](docs/example-addon/README.md).

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
