local node = computed.node(1, "computed:pulse", "Pulse")

node:category("flow")
node:style("source")
node:execution("tick")
node:field("period", "number", { default = 20, min = 1 })
node:output("pulse", "boolean")
node:on_run(function(ctx)
    local period = math.max(1, math.floor(ctx:field("period")))
    ctx:output("pulse", ctx:tick() % period == 0)
end)

return node
