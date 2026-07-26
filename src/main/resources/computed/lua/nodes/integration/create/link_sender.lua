local node = computed.node(1, "computed:create_link_sender", "Redstone Link Sender")

node:category("integration/create/redstone_link")
node:style("sink")
node:execution("input")
node:field("first", "item", { default = "minecraft:air", label = "First frequency" })
node:field("second", "item", { default = "minecraft:air", label = "Second frequency" })
node:input("strength", "number")
node:on_run(function(ctx)
    ctx:endpoint("create:redstone_link"):call(
        "transmit",
        ctx:field("first"),
        ctx:field("second"),
        ctx:input("strength"))
end)

return node
