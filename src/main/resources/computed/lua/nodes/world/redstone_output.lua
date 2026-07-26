local node = computed.node(1, "computed:redstone_emitter", "Redstone Output")

node:category("io")
node:style("sink")
node:input("level", "number", { default = 0 })
node:field("face", "direction", { default = "front" })
node:on_run(function(ctx)
    ctx:endpoint("computed:redstone"):call(
        "output",
        ctx:field("face"),
        ctx:input("level"))
end)

return node
