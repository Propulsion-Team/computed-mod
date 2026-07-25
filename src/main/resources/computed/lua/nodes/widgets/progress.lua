local node = computed.node(1, "computed:progress_bar_widget", "Progress Bar Widget")

node:category("widgets")
node:input("value", "number", { default = 0 })
node:input("maximum", "number", { default = 1 })
node:input("color", "number", { default = 4294967295 })
node:field("segments", "number", { default = 0, min = 0 })
node:output("widget", "widget")
node:on_run(function(ctx)
    ctx:output(
        "widget",
        ctx:endpoint("computed:widget"):call(
            "progress",
            ctx:input("value"),
            ctx:input("maximum"),
            ctx:input("color"),
            ctx:field("segments")))
end)

return node
