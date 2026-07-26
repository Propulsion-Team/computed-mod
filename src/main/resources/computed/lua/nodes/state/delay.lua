local node = computed.node(1, "computed:delay", "Delay")

node:category("state")
node:execution("tick")
node:input("value", "number", { default = 0 })
node:field("delay", "number", {
    default = 1,
    min = 0,
    max = 200,
    label = "Delay (ticks)",
    control = "slider",
    step = 1
})
node:output("delayed", "number")
node:state("values", {})
node:on_run(function(ctx)
    local delay = math.max(0, math.floor(ctx:field("delay")))
    if delay == 0 then
        ctx:output("delayed", ctx:input("value"))
        ctx:set_state("values", {})
        return
    end
    local values = ctx:state("values")
    values[#values + 1] = ctx:input("value")
    if #values > delay then
        ctx:output("delayed", table.remove(values, 1))
    end
    while #values > delay do
        table.remove(values, 1)
    end
    ctx:set_state("values", values)
end)

return node
