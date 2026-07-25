package dev.propulsionteam.computed.lua.sandbox;

public final class LuaInstructionBudget {
    public static final int DEFAULT_INVOCATION_LIMIT = 50_000;
    public static final int DEFAULT_TICK_LIMIT = 500_000;

    private final int invocationLimit;
    private final int tickLimit;
    private volatile int tickRemaining;
    private volatile int invocationRemaining;
    private volatile boolean active;

    public LuaInstructionBudget() {
        this(DEFAULT_INVOCATION_LIMIT, DEFAULT_TICK_LIMIT);
    }

    public LuaInstructionBudget(int invocationLimit, int tickLimit) {
        if (invocationLimit < 1 || tickLimit < invocationLimit) {
            throw new IllegalArgumentException("Invalid Lua instruction limits");
        }
        this.invocationLimit = invocationLimit;
        this.tickLimit = tickLimit;
        tickRemaining = tickLimit;
    }

    public void beginTick() {
        tickRemaining = tickLimit;
        invocationRemaining = 0;
        active = false;
    }

    public Scope beginInvocation() {
        if (active) {
            throw new IllegalStateException("A Lua invocation is already being metered");
        }
        active = true;
        invocationRemaining = invocationLimit;
        return new Scope(this);
    }

    void consume(int amount) {
        if (!active) {
            return;
        }
        invocationRemaining -= amount;
        tickRemaining -= amount;
        if (invocationRemaining < 0) {
            throw new LuaInstructionLimitException(
                    "Lua node exceeded the " + invocationLimit + "-instruction invocation limit");
        }
        if (tickRemaining < 0) {
            throw new LuaInstructionLimitException(
                    "Computer exceeded the " + tickLimit + "-instruction tick limit");
        }
    }

    public int tickRemaining() {
        return tickRemaining;
    }

    public final class Scope implements AutoCloseable {
        private LuaInstructionBudget owner;

        private Scope(LuaInstructionBudget owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (owner != null) {
                owner.active = false;
                owner.invocationRemaining = 0;
                owner = null;
            }
        }
    }
}
