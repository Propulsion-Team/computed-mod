local node = computed.node(1, "computed:cmp_approx", "Approximately Equal")

node:category("logic")
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:field("epsilon", "number", { default = 0.000001, min = 0 })
node:output("result", "boolean")
node:on_run(function(ctx)
    ctx:output("result", math.abs(ctx:input("a") - ctx:input("b")) <= ctx:field("epsilon"))
end)

return node
