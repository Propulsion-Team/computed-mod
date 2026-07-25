local node = computed.node(1, "computed:text_source", "Text")

node:category("text")
node:style("source")
node:field("text", "text", { default = "" })
node:output("text", "string")
node:on_run(function(ctx)
    ctx:output("text", ctx:field("text"))
end)

return node
