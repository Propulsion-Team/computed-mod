local node = computed.node(1, "computed:counter", "Counter")

node:category("state")
node:input("increment", "number", { default = 0 })
node:output("count", "number")
node:field("step", "number", { default = 1 })
node:state("count", 0)
node:on_run(function(ctx)
    local next = ctx:state("count") + ctx:input("increment") * ctx:field("step")
    ctx:set_state("count", next)
    ctx:output("count", next)
end)

return node
