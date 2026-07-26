local node = computed.node(1, "computed:cmp_approx", "Approximately Equal")

node:category("logic")
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:field("epsilon", "number", {
    default = 0.5,
    min = 0,
    max = 15,
    label = "Tolerance",
    control = "slider",
    step = 0.01
})
node:output("result", "boolean")
node:on_run(function(ctx)
    ctx:output("result", math.abs(ctx:input("a") - ctx:input("b")) <= ctx:field("epsilon"))
end)

return node
