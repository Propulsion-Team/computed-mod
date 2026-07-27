local node = computed.node(1, "computed:event_sender", "Event Sender")

node:category("flow/events")
node:style("sink")
node:execution("input")
node:field("event_name", "text", { default = "event", label = "Event name" })
node:input("trigger", "boolean", { default = false })
node:state("previous_trigger", false)
node:on_run(function(ctx)
    local trigger = ctx:input("trigger")
    local previous = ctx:state("previous_trigger")
    if trigger and not previous then
        local payload = {}
        for id, value in pairs(ctx:inputs()) do
            if id ~= "trigger" then
                payload[id] = value
            end
        end
        ctx:emit("event_bus", ctx:field("event_name"), payload)
    end
    ctx:set_state("previous_trigger", trigger)
end)

return node
