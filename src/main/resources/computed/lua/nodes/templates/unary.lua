local node = computed.node(1, "@ID@", "@TITLE@")

node:category("@CATEGORY@")
node:input("value", "number", { default = 0 })
node:output("result", "number")
node:on_run(function(ctx)
    local value = ctx:input("value")
    ctx:output("result", @EXPRESSION@)
end)

return node
