local node = computed.node(1, "computed:clock_widget", "Clock Widget")

node:category("widgets")
node:input("color", "number", { default = 4294967295 })
node:field("x", "number", { default = 0, label = "X" })
node:field("y", "number", { default = 0, label = "Y" })
node:field("width", "number", { default = 60, min = 1, label = "Width", step = 1 })
node:field("height", "number", { default = 12, min = 1, label = "Height", step = 1 })
node:field("show_seconds", "boolean", { default = true })
node:field("alignment", "choice", {
    default = "center",
    label = "Alignment",
    choices = { "left", "center", "right" }
})
node:output("widget", "widget")
node:on_run(function(ctx)
    local widget = ctx:endpoint("computed:widget"):call(
        "clock",
        ctx:input("color"),
        ctx:field("show_seconds"))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    widget.alignment = ctx:field("alignment")
    ctx:output("widget", widget)
end)

return node
