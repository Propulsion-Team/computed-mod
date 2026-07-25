local node = computed.node(1, "computed:math_average", "Running Average")

node:category("state")
node:input("value", "number", { default = 0 })
node:output("mean", "number")
node:state("sum", 0)
node:state("count", 0)
node:on_run(function(ctx)
    local sum = ctx:state("sum") + ctx:input("value")
    local count = ctx:state("count") + 1
    ctx:set_state("sum", sum)
    ctx:set_state("count", count)
    ctx:output("mean", sum / count)
end)

return node
