local node = computed.node(1, "computed:cc_input", "CC Input")

node:category("integration/computercraft/channels")
node:style("source")
node:execution("tick")
node:field("channel", "text", { default = "input" })
node:output("value", "table")
node:on_run(function(ctx)
    local endpoint = ctx:endpoint("computercraft:channel")
    ctx:output("value", endpoint:call("read", ctx:field("channel")))
end)

return node
