local node = computed.node(1, "computed:tick", "Tick")

node:category("flow")
node:style("source")
node:execution("tick")
node:output("tick", "number")
node:output("delta", "number")
node:on_run(function(ctx)
    ctx:output("tick", ctx:tick())
    ctx:output("delta", 0.05)
end)

return node
