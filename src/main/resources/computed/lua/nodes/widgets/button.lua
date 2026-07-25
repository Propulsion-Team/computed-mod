local node = computed.node(1, "computed:button_widget", "Button Widget")

node:category("widgets")
node:input("label", "string", { default = "Button" })
node:input("color", "number", { default = 4294967295 })
node:output("widget", "widget")
node:output("clicked", "boolean")
node:on_run(function(ctx)
    ctx:output(
        "widget",
        ctx:endpoint("computed:widget"):call(
            "button",
            ctx:input("label"),
            ctx:input("color")))
    ctx:output("clicked", false)
end)
node:on_event("input", function(ctx)
    ctx:output("clicked", true)
end)

return node
