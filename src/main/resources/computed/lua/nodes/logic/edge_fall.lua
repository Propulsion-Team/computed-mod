local node = computed.node(1, "computed:edge_fall", "Falling Edge")

node:category("logic")
node:input("value", "boolean", { default = false })
node:output("pulse", "boolean")
node:state("previous", false)
node:on_run(function(ctx)
    local value = ctx:input("value")
    ctx:output("pulse", not value and ctx:state("previous"))
    ctx:set_state("previous", value)
end)

return node
