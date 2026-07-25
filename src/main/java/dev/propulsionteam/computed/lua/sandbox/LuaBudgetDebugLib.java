package dev.propulsionteam.computed.lua.sandbox;

import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;

final class LuaBudgetDebugLib extends DebugLib {
    private final LuaInstructionBudget budget;
    private int instructions;

    LuaBudgetDebugLib(LuaInstructionBudget budget) {
        this.budget = budget;
    }

    @Override
    public void onCall(LuaFunction function) {}

    @Override
    public void onCall(LuaClosure closure, Varargs varargs, LuaValue[] stack) {}

    @Override
    public void onInstruction(int pc, Varargs varargs, int top) {
        instructions++;
        if (instructions == LuaInstructionBudget.METERING_QUANTUM) {
            instructions = 0;
            budget.consume(LuaInstructionBudget.METERING_QUANTUM);
        }
    }

    @Override
    public void onReturn() {}
}
