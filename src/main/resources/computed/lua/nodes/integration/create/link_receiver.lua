local node = computed.node(1, "computed:create_link_receiver", "Redstone Link Receiver")

node:category("integration/create/redstone_link")
node:style("source")
node:execution("tick")
node:field("first", "item", { default = "minecraft:air", label = "First frequency" })
node:field("second", "item", { default = "minecraft:air", label = "Second frequency" })
node:output("strength", "number")
node:on_run(function(ctx)
    local link = ctx:endpoint("create:redstone_link")
    ctx:output("strength", link:call("receive", ctx:field("first"), ctx:field("second")))
end)

return node
