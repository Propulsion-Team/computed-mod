local node = computed.node(1, "computed:command", "Run Command")

node:category("io")
node:style("sink")
node:input("trigger", "boolean", { default = false })
node:field("command", "text", { default = "" })
node:on_run(function(ctx)
    if ctx:input("trigger") then
        local commands = ctx:endpoint("computed:command")
        commands:call("run", ctx:field("command"))
    end
end)

return node
