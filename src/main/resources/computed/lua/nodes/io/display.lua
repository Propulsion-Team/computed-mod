local node = computed.node(1, "computed:display", "Display")

node:category("io")
node:style("sink")
node:input("value", "number", { default = 0 })
node:on_run(function(ctx)
    ctx:set_state("unused", 0)
end)
node:state("unused", 0)

return node
