local node = computed.node(1, "computed:comparator_read", "Comparator Read")

node:category("world")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:output("level", "number")
node:on_run(function(ctx)
    ctx:output(
        "level",
        ctx:endpoint("computed:redstone"):call("comparator", ctx:field("face")))
end)

return node
