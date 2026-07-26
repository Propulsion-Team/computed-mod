local node = computed.node(1, "computed:oscillator", "Oscillator")

node:category("math")
node:style("source")
node:execution("tick")
node:field("period", "number", {
    default = 20,
    min = 1,
    max = 200,
    label = "Period (ticks)",
    control = "slider",
    step = 1
})
node:field("amplitude", "number", {
    default = 1,
    min = 1,
    max = 100,
    label = "Amplitude",
    control = "slider",
    step = 1
})
node:output("value", "number")
node:on_run(function(ctx)
    local period = math.max(1, ctx:field("period"))
    ctx:output(
        "value",
        math.sin(ctx:tick() * math.pi * 2 / period)
            * ctx:field("amplitude"))
end)

return node
