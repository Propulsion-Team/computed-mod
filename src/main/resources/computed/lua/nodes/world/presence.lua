local node = computed.node(1, "computed:block_presence", "Block Presence")

node:category("world")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:output("present", "boolean")
node:on_run(function(ctx)
    ctx:output(
        "present",
        ctx:endpoint("computed:world"):call("block_present", ctx:field("face")))
end)

return node
