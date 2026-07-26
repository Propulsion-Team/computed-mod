local node = computed.node(1, "computed:add", "Add")

node:category("math")
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:output("result", "number")
node:on_run(function(ctx)
    ctx:output("result", ctx:input("a") + ctx:input("b"))
end)

return node
