local node = computed.node(1, "computed:delay", "Delay")

node:category("state")
node:input("value", "number", { default = 0 })
node:output("delayed", "number")
node:state("previous", 0)
node:on_run(function(ctx)
    ctx:output("delayed", ctx:state("previous"))
    ctx:set_state("previous", ctx:input("value"))
end)

return node
