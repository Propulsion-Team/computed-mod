local node = computed.node(1, "computed:math_lerp", "Lerp")

node:category("math")
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 1 })
node:input("amount", "number", { default = 0.5 })
node:output("result", "number")
node:on_run(function(ctx)
    local a = ctx:input("a")
    ctx:output("result", a + (ctx:input("b") - a) * ctx:input("amount"))
end)

return node
