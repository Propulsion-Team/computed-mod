local node = computed.node(1, "computed:math_map", "Map Range")

node:category("math")
node:input("value", "number", { default = 0 })
node:input("input_minimum", "number", { default = 0 })
node:input("input_maximum", "number", { default = 1 })
node:input("output_minimum", "number", { default = 0 })
node:input("output_maximum", "number", { default = 1 })
node:output("result", "number")
node:on_run(function(ctx)
    local input_minimum = ctx:input("input_minimum")
    local input_maximum = ctx:input("input_maximum")
    local result = ctx:input("output_minimum")
    if input_maximum ~= input_minimum then
        local ratio = (ctx:input("value") - input_minimum) / (input_maximum - input_minimum)
        result = result + ratio * (ctx:input("output_maximum") - result)
    end
    ctx:output("result", result)
end)

return node
