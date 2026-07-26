local node = computed.node(1, "computed:mux", "Multiplexer")

node:category("logic")
node:input("select", "boolean", { default = false })
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:output("result", "number")
node:on_run(function(ctx)
    ctx:output("result", ctx:input("select") and ctx:input("b") or ctx:input("a"))
end)

return node
