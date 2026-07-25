local node = computed.node(1, "computed:sample_hold", "Sample and Hold")

node:category("state")
node:input("value", "number", { default = 0 })
node:input("clock", "boolean", { default = false })
node:output("held", "number")
node:state("value", 0)
node:state("previous_clock", false)
node:on_run(function(ctx)
    local clock = ctx:input("clock")
    local held = ctx:state("value")
    if clock and not ctx:state("previous_clock") then
        held = ctx:input("value")
    end
    ctx:set_state("value", held)
    ctx:set_state("previous_clock", clock)
    ctx:output("held", held)
end)

return node
