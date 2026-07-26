local node = computed.node(1, "computed:schmitt", "Schmitt Trigger")

node:category("logic")
node:input("value", "number", { default = 0 })
node:field("low", "number", {
    default = 5,
    min = 0,
    max = 15,
    label = "Off threshold",
    control = "slider",
    step = 0.1
})
node:field("high", "number", {
    default = 10,
    min = 0,
    max = 15,
    label = "On threshold",
    control = "slider",
    step = 0.1
})
node:output("result", "boolean")
node:state("active", false)
node:on_run(function(ctx)
    local active = ctx:state("active")
    if active and ctx:input("value") <= ctx:field("low") then
        active = false
    elseif not active and ctx:input("value") >= ctx:field("high") then
        active = true
    end
    ctx:set_state("active", active)
    ctx:output("result", active)
end)

return node
