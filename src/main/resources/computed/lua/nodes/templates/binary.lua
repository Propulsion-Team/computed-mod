local node = computed.node(1, "@ID@", "@TITLE@")

node:category("@CATEGORY@")
node:input("a", "number", { default = 0 })
node:input("b", "number", { default = 0 })
node:output("result", "number")
node:on_run(function(ctx)
    local a = ctx:input("a")
    local b = ctx:input("b")
    ctx:output("result", @EXPRESSION@)
end)

return node
