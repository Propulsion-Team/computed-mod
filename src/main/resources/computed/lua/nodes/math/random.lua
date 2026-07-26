local node = computed.node(1, "computed:math_random", "Random")

node:category("math")
node:style("source")
node:execution("tick")
node:field("minimum", "number", { default = 0 })
node:field("maximum", "number", { default = 1 })
node:output("result", "number")
node:on_run(function(ctx)
    local minimum = ctx:field("minimum")
    local maximum = ctx:field("maximum")
    ctx:output("result", minimum + math.random() * (maximum - minimum))
end)

return node
