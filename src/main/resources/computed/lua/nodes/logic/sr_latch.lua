local node = computed.node(1, "computed:sr_latch", "SR Latch")

node:category("state")
node:input("set", "boolean", { default = false })
node:input("reset", "boolean", { default = false })
node:output("q", "boolean")
node:state("q", false)
node:on_run(function(ctx)
    local q = ctx:state("q")
    if ctx:input("reset") then
        q = false
    elseif ctx:input("set") then
        q = true
    end
    ctx:set_state("q", q)
    ctx:output("q", q)
end)

return node
