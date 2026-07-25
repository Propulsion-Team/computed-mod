local node = computed.node(1, "computed:pass_every_n", "Pass Every N")

node:category("state")
node:input("trigger", "boolean", { default = false })
node:field("count", "number", { default = 2, min = 1 })
node:output("pulse", "boolean")
node:state("previous", false)
node:state("seen", 0)
node:on_run(function(ctx)
    local active = ctx:input("trigger")
    local rising = active and not ctx:state("previous")
    local seen = ctx:state("seen")
    local pulse = false
    if rising then
        seen = seen + 1
        local count = math.max(1, math.floor(ctx:field("count")))
        pulse = seen % count == 0
    end
    ctx:set_state("previous", active)
    ctx:set_state("seen", seen)
    ctx:output("pulse", pulse)
end)

return node
