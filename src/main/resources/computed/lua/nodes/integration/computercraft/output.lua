local node = computed.node(1, "computed:cc_output", "CC Output")

node:category("integration/computercraft/channels")
node:style("sink")
node:execution("input")
node:field("channel", "text", { default = "output" })
node:input("value", "table")
node:on_run(function(ctx)
    ctx:endpoint("computercraft:channel"):call("publish", ctx:field("channel"), ctx:input("value"))
end)

return node
