local node = computed.node(1, "computed:if_branch", "If")

node:category("flow")
node:input("condition", "boolean", { default = false })
node:output("true_branch", "boolean")
node:output("false_branch", "boolean")
node:on_run(function(ctx)
    local condition = ctx:input("condition")
    ctx:output("true_branch", condition)
    ctx:output("false_branch", not condition)
end)

return node
