local node = computed.node(1, "computed:block_location", "Block Location")

node:category("world")
node:style("source")
node:execution("tick")
node:output("x", "number")
node:output("y", "number")
node:output("z", "number")
node:on_run(function(ctx)
    local x, y, z = ctx:endpoint("computed:world"):call("position")
    ctx:output("x", x)
    ctx:output("y", y)
    ctx:output("z", z)
end)

return node
