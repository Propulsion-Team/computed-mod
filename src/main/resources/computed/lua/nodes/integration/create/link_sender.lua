local node = computed.node(1, "computed:create_link_sender", "Redstone Link Sender")

node:category("integration/create/redstone_link")
node:style("sink")
node:execution("input")
node:field("first", "item", { default = "minecraft:air", label = "First frequency" })
node:field("second", "item", { default = "minecraft:air", label = "Second frequency" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:input("strength", "number")
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    local strength = active and ctx:input("strength") or 0
    ctx:endpoint("create:redstone_link"):call(
        "transmit",
        ctx:field("first"),
        ctx:field("second"),
        strength)
end)

return node
