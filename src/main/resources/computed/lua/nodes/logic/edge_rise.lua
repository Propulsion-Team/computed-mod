local node = computed.node(1, "computed:edge_rise", "Rising Edge")

node:category("logic")
node:input("value", "boolean", { default = false })
node:output("pulse", "boolean")
node:state("previous", false)
node:on_run(function(ctx)
    local value = ctx:input("value")
    ctx:output("pulse", value and not ctx:state("previous"))
    ctx:set_state("previous", value)
end)

return node
