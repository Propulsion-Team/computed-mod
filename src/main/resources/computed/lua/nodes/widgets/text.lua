local node = computed.node(1, "computed:text_widget", "Text Widget")

node:category("widgets")
node:input("text", "string", { default = "" })
node:field("x", "number", { default = 0, label = "X" })
node:field("y", "number", { default = 0, label = "Y" })
node:field("width", "number", { default = 64, min = 1, label = "Width", step = 1 })
node:field("height", "number", { default = 12, min = 1, label = "Height", step = 1 })
node:field("alignment", "choice", {
    default = "center",
    label = "Alignment",
    choices = { "left", "center", "right" }
})
node:output("widget", "widget")
node:on_run(function(ctx)
    local widgets = ctx:endpoint("computed:widget")
    local widget = widgets:call("text", ctx:input("text"))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    widget.alignment = ctx:field("alignment")
    ctx:output("widget", widget)
end)

return node
