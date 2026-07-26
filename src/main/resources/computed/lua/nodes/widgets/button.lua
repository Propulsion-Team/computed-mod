local node = computed.node(1, "computed:button_widget", "Button Widget")

node:category("widgets")
node:input("label", "string", { default = "Button" })
node:input("color", "number", { default = 4294967295 })
node:field("x", "number", { default = 0, label = "X" })
node:field("y", "number", { default = 0, label = "Y" })
node:field("width", "number", { default = 64, min = 1, label = "Width", step = 1 })
node:field("height", "number", { default = 16, min = 1, label = "Height", step = 1 })
node:output("widget", "widget")
node:output("clicked", "boolean")
node:on_run(function(ctx)
    local widget = ctx:endpoint("computed:widget"):call(
        "button",
        ctx:input("label"),
        ctx:input("color"))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    ctx:output("widget", widget)
    ctx:output("clicked", false)
end)
node:on_event("input", function(ctx)
    ctx:output("clicked", true)
end)

return node
