local node = computed.node(1, "computed:logic_not", "Not")

node:category("logic")
node:input("value", "boolean", { default = false })
node:output("result", "boolean")
node:on_run(function(ctx)
    ctx:output("result", not ctx:input("value"))
end)

return node
