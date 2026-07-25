local node = computed.node(1, "computed:level_to_bool", "Level to Boolean")

node:category("io")
node:input("level", "number", { default = 0 })
node:output("value", "boolean")
node:on_run(function(ctx)
    ctx:output("value", ctx:input("level") > 0)
end)

return node
