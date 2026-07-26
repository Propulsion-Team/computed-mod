local node = computed.node(1, "computed:constant", "Constant")

node:category("utility")
node:style("source")
node:field("value", "number", { default = 10, label = "Value" })
node:output("value", "number")
node:on_run(function(ctx)
    ctx:output("value", ctx:field("value"))
end)

return node
