local node = computed.node(1, "computed:switch", "Switch")

node:category("flow")
node:input("select", "boolean", { default = false })
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:output("value", "number")
node:on_run(function(ctx)
    ctx:output("value", ctx:input("select") and ctx:input("b") or ctx:input("a"))
end)

return node
