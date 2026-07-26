local node = computed.node(1, "computed:progress_bar_widget", "Progress Bar Widget")

node:category("widgets")
node:input("value", "number", { default = 0 })
node:input("maximum", "number", { default = 1 })
node:input("color", "number", { default = 4294967295 })
node:field("x", "number", { default = 0, label = "X" })
node:field("y", "number", { default = 0, label = "Y" })
node:field("width", "number", { default = 64, min = 1, label = "Width", step = 1 })
node:field("height", "number", { default = 12, min = 1, label = "Height", step = 1 })
node:field("segments", "number", { default = 0, min = 0 })
node:output("widget", "widget")
node:on_run(function(ctx)
    local widget = ctx:endpoint("computed:widget"):call(
        "progress",
        ctx:input("value"),
        ctx:input("maximum"),
        ctx:input("color"),
        ctx:field("segments"))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    ctx:output("widget", widget)
end)

return node
