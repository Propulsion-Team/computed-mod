local node = computed.node(1, "computed:rgb_preview", "RGB Preview")

node:category("io")
node:style("sink")
node:input("red", "number", { default = 0 })
node:input("green", "number", { default = 0 })
node:input("blue", "number", { default = 0 })
node:on_run(function(ctx)
    ctx:set_state("color", {
        red = ctx:input("red"),
        green = ctx:input("green"),
        blue = ctx:input("blue")
    })
end)
node:state("color", { red = 0, green = 0, blue = 0 })

return node
