local node = computed.node(1, "computed:block_rotation", "Block Rotation")

node:category("world")
node:style("source")
node:execution("tick")
node:output("yaw", "number")
node:output("pitch", "number")
node:output("roll", "number")
node:on_run(function(ctx)
    local yaw, pitch, roll = ctx:endpoint("computed:world"):call("rotation")
    ctx:output("yaw", yaw)
    ctx:output("pitch", pitch)
    ctx:output("roll", roll)
end)

return node
