package dev.propulsionteam.computed.client.editor.lua;

import java.util.UUID;

public final class LuaNodeStarter {
    private LuaNodeStarter() {}

    public static Starter create() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String id = "user:node_" + suffix;
        String source = """
                local node = computed.node(1, "%s", "New Lua Node")

                node:category("lua")
                node:input("value", "number", { default = 0 })
                node:field("factor", "number", {
                    default = 1,
                    label = "Factor",
                    control = "value",
                    step = 0.1
                })
                node:output("result", "number")

                node:on_run(function(ctx)
                    ctx:output("result", ctx:input("value") * ctx:field("factor"))
                end)

                return node
                """.formatted(id);
        return new Starter(id, source);
    }

    public record Starter(String id, String source) {}
}
