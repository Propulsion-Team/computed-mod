local node = computed.node(1, "computed:d_flipflop", "D Flip-Flop")

node:category("state")
node:input("data", "boolean", { default = false })
node:input("clock", "boolean", { default = false })
node:output("q", "boolean")
node:state("q", false)
node:state("previous_clock", false)
node:on_run(function(ctx)
    local clock = ctx:input("clock")
    local q = ctx:state("q")
    if clock and not ctx:state("previous_clock") then
        q = ctx:input("data")
    end
    ctx:set_state("q", q)
    ctx:set_state("previous_clock", clock)
    ctx:output("q", q)
end)

return node
