local node = computed.node(1, "computed:level_to_bool", "Level to Boolean")

node:category("io")
node:input("level", "number", { default = 0 })
node:field("threshold", "number", {
    default = 8,
    min = 0,
    max = 15,
    label = "Threshold",
    control = "slider",
    step = 1
})
node:output("value", "boolean")
node:on_run(function(ctx)
    ctx:output("value", ctx:input("level") >= ctx:field("threshold"))
end)

return node
