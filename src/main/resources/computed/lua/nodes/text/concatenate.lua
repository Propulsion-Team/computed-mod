local node = computed.node(1, "computed:concatenate_strings", "Concatenate")

node:category("text")
node:input("a", "string", { default = "" })
node:input("b", "string", { default = "" })
node:output("text", "string")
node:on_run(function(ctx)
    ctx:output("text", ctx:input("a") .. ctx:input("b"))
end)

return node
