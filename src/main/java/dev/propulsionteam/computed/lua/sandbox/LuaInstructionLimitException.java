package dev.propulsionteam.computed.lua.sandbox;

import org.luaj.vm2.LuaError;

public final class LuaInstructionLimitException extends LuaError {
    public LuaInstructionLimitException(String message) {
        super(message);
    }
}
