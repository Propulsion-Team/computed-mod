local node = computed.node(1, "computed:redstone_emitter", "Redstone Output")

node:category("io")
node:style("sink")
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:input("level", "number", { default = 0 })
node:field("face", "direction", { default = "front" })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    ctx:endpoint("computed:redstone"):call(
        "output",
        ctx:field("face"),
        active and ctx:input("level") or 0)
end)

return node
