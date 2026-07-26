package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.luaj.vm2.LuaValue;

public sealed interface EndpointResult permits EndpointResult.Immediate, EndpointResult.Yielded, EndpointResult.Unavailable {
    record Immediate(List<LuaValue> values) implements EndpointResult {
        public Immediate {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    record Yielded(CompletionStage<Immediate> continuation) implements EndpointResult {
        public Yielded {
            Objects.requireNonNull(continuation, "continuation");
        }
    }

    record Unavailable(String reason) implements EndpointResult {
        public Unavailable {
            reason = reason == null || reason.isBlank() ? "endpoint unavailable" : reason;
        }
    }

    public static Immediate immediate(LuaValue... values) {
        return new Immediate(List.of(values));
    }

    public static Yielded yielded(CompletionStage<Immediate> continuation) {
        return new Yielded(continuation);
    }

    public static Unavailable unavailable(String reason) {
        return new Unavailable(reason);
    }
}
