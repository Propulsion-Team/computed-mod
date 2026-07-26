local node = computed.node(1, "computed:bool_to_level", "Boolean to Level")

node:category("io")
node:input("value", "boolean", { default = false })
node:output("level", "number")
node:on_run(function(ctx)
    ctx:output("level", ctx:input("value") and 15 or 0)
end)

return node
