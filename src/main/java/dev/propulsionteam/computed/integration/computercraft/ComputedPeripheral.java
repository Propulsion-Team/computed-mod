package dev.propulsionteam.computed.integration.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;

public final class ComputedPeripheral implements IPeripheral {
    private final ComputerBlockEntity computer;

    public ComputedPeripheral(ComputerBlockEntity computer) {
        this.computer = computer;
    }

    @Override
    public String getType() {
        return "computed";
    }

    @Override
    public void attach(IComputerAccess access) {
        ComputerCraftChannels.store(computer).attach(access);
    }

    @Override
    public void detach(IComputerAccess access) {
        ComputerCraftChannels.store(computer).detach(access);
    }

    @LuaFunction
    public final MethodResult listChannels() {
        return MethodResult.of(ComputerCraftChannels.store(computer).channels());
    }

    @LuaFunction
    public final MethodResult read(IArguments arguments) throws LuaException {
        return MethodResult.of(ComputerCraftChannels.store(computer).output(arguments.getString(0)));
    }

    @LuaFunction
    public final MethodResult write(IArguments arguments) throws LuaException {
        ComputerCraftChannels.store(computer).write(arguments.getString(0), arguments.get(1));
        return MethodResult.of();
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ComputedPeripheral peripheral && peripheral.computer == computer;
    }
}
