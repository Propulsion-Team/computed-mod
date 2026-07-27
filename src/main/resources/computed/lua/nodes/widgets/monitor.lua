local node = computed.node(1, "computed:peripheral", "Monitor")

node:category("widgets")
node:style("sink")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:on_run(function(ctx)
    local widgets = {}
    local inputs = ctx:inputs()
    for index = 1, 16 do
        local widget = inputs["widget_" .. index]
        if widget ~= nil then
            widgets[#widgets + 1] = widget
        end
    end
    ctx:endpoint("computed:monitor", ctx:field("face")):call("show", widgets)
end)

return node
