local node = computed.node(1, "computed:peripheral", "Monitor")

node:category("widgets")
node:style("sink")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:input("widget_1", "widget", { required = false })
node:input("widget_2", "widget", { required = false })
node:input("widget_3", "widget", { required = false })
node:input("widget_4", "widget", { required = false })
node:input("widget_5", "widget", { required = false })
node:input("widget_6", "widget", { required = false })
node:input("widget_7", "widget", { required = false })
node:input("widget_8", "widget", { required = false })
node:on_run(function(ctx)
    local widgets = {}
    for index = 1, 8 do
        local widget = ctx:input("widget_" .. index)
        if widget ~= nil then
            widgets[#widgets + 1] = widget
        end
    end
    ctx:endpoint("computed:monitor", ctx:field("face")):call("show", widgets)
end)

return node
