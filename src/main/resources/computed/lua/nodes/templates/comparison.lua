local node = computed.node(1, "@ID@", "@TITLE@")

node:category("logic")
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:output("result", "boolean")
node:on_run(function(ctx)
    local a = ctx:input("a")
    local b = ctx:input("b")
    ctx:output("result", @EXPRESSION@)
end)

return node
