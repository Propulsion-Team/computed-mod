local node = computed.node(1, "computed:math_clamp", "Clamp")

node:category("math")
node:input("value", "number", { default = 0 })
node:input("minimum", "number", { default = 0 })
node:input("maximum", "number", { default = 1 })
node:output("result", "number")
node:on_run(function(ctx)
    local minimum = ctx:input("minimum")
    local maximum = ctx:input("maximum")
    ctx:output("result", math.max(minimum, math.min(maximum, ctx:input("value"))))
end)

return node
