local node = computed.node(1, "computed:tick", "Tick")

node:category("flow")
node:style("source")
node:execution("tick")
node:field("rate", "number", {
    default = 20,
    min = 0,
    max = 20,
    label = "Rate",
    control = "slider",
    step = 1
})
node:output("tick", "number")
node:output("delta", "number")
node:state("accumulator", 0)
node:on_run(function(ctx)
    local rate = ctx:field("rate")
    if rate <= 0 then
        return
    end
    local accumulator = ctx:state("accumulator") + rate / 20
    if accumulator >= 1 then
        accumulator = accumulator - 1
        ctx:output("tick", ctx:tick())
        ctx:output("delta", 1 / rate)
    end
    ctx:set_state("accumulator", accumulator)
end)

return node
