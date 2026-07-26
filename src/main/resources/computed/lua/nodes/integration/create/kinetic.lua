local node = computed.node(1, "computed:create_kinetic", "Kinetic Sensor")

node:category("integration/create/kinetics")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:output("speed", "number")
node:output("stress", "number")
node:output("capacity", "number")
node:on_run(function(ctx)
    local create = ctx:endpoint("create:kinetic", ctx:field("face"))
    ctx:output("speed", create:call("speed"))
    ctx:output("stress", create:call("stress"))
    ctx:output("capacity", create:call("capacity"))
end)

return node
