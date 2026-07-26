local node = computed.node(1, "computed:world_time", "World Time")

node:category("world")
node:style("source")
node:output("time", "number")
node:execution("tick")
node:on_run(function(ctx)
    local world = ctx:endpoint("computed:world")
    ctx:output("time", world:call("time"))
end)

return node
