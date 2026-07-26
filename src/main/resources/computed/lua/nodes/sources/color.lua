local node = computed.node(1, "computed:color_source", "Color")

node:category("utility")
node:style("source")
node:field("color", "color", { default = 4294928042 })
node:output("color", "number")
node:on_run(function(ctx)
    ctx:output("color", ctx:field("color"))
end)

return node
