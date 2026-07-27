local node = computed.node(1, "computed:event_receiver", "Event Receiver")

node:category("flow/events")
node:style("source")
node:execution("event")
node:field("event_name", "text", { default = "event", label = "Event name" })
node:output("triggered", "event")
node:on_run(function(ctx)
end)
node:on_event("event_bus", function(ctx, event_name, payload)
    if event_name ~= ctx:field("event_name") or type(payload) ~= "table" then
        return
    end
    for id, value in pairs(payload) do
        ctx:output(id, value)
    end
    ctx:output("triggered", ctx:graph_step())
end)

return node
