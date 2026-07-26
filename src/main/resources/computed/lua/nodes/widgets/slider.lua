local node = computed.node(1, "computed:slider_widget", "Slider Widget")

node:category("widgets")
node:input("minimum", "number", { default = 0 })
node:input("maximum", "number", { default = 1 })
node:input("color", "number", { default = 4294967295 })
node:field("layout_mode", "choice", {
    default = "line",
    label = "Layout",
    choices = { "line", "manual" }
})
node:field("line", "number", {
    default = 1, min = 1, step = 1, label = "Line",
    visible_when = { field = "layout_mode", equals = "line" }
})
node:field("span", "number", {
    default = 1, min = 1, step = 1, label = "Line Span",
    visible_when = { field = "layout_mode", equals = "line" }
})
node:field("fit", "choice", {
    default = "auto",
    label = "Fit",
    choices = { "auto", "fill" },
    visible_when = { field = "layout_mode", equals = "line" }
})
node:field("x", "number", {
    default = 0, label = "X",
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("y", "number", {
    default = 0, label = "Y",
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("width", "number", {
    default = 64, min = 1, label = "Width", step = 1,
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("height", "number", {
    default = 16, min = 1, label = "Height", step = 1,
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("step", "number", { default = 0.01, min = 0 })
node:output("widget", "widget")
node:output("value", "number")
node:state("value", 0)
local function render(ctx)
    local value = ctx:state("value")
    local widget = ctx:endpoint("computed:widget"):call(
        "slider",
        value,
        ctx:input("minimum"),
        ctx:input("maximum"),
        ctx:input("color"),
        ctx:field("step"))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    widget.layout_mode = ctx:field("layout_mode")
    widget.line = math.max(1, math.floor(ctx:field("line")))
    widget.span = math.max(1, math.floor(ctx:field("span")))
    widget.fit = ctx:field("fit")
    ctx:output("widget", widget)
    ctx:output("value", value)
end
node:on_run(render)
node:on_event("input", function(ctx, value)
    ctx:set_state("value", value)
    render(ctx)
end)

return node
