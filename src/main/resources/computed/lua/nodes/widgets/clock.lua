local node = computed.node(1, "computed:clock_widget", "Clock Widget")

node:category("widgets")
node:input("color", "number", { default = 4294967295 })
node:field("show_seconds", "boolean", { default = true })
node:output("widget", "widget")
node:on_run(function(ctx)
    ctx:output(
        "widget",
        ctx:endpoint("computed:widget"):call(
            "clock",
            ctx:input("color"),
            ctx:field("show_seconds")))
end)

return node
