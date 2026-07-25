package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.lua.compiler.LuaCompiledSource;
import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaDefinitionLoader {
    public LuaNodeDefinition load(LuaCompiledSource source, LuaSandbox sandbox) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sandbox, "sandbox");
        LuaTable environment = sandbox.createEnvironment();
        Holder holder = new Holder(source);
        LuaTable computed = new LuaTable();
        computed.set("node", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (holder.builder != null) {
                    throw new LuaDefinitionException("A source file may define exactly one node");
                }
                holder.builder = new Builder(
                        args.arg(1).checkint(),
                        args.arg(2).checkjstring(),
                        args.arg(3).checkjstring(),
                        source.sourceHash());
                return holder.builder.table;
            }
        });
        environment.set("computed", computed);
        LuaValue returned;
        try (LuaInstructionBudget.Scope ignored = sandbox.budget().beginInvocation()) {
            returned = new LuaClosure(source.prototype(), environment).call();
        } catch (LuaDefinitionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LuaDefinitionException("Lua definition evaluation failed: " + exception.getMessage(), exception);
        }
        if (holder.builder == null) {
            throw new LuaDefinitionException("Lua source did not call computed.node");
        }
        if (returned != holder.builder.table) {
            throw new LuaDefinitionException("Lua source must return the node created by computed.node");
        }
        return holder.builder.build();
    }

    private static final class Holder {
        private final LuaCompiledSource source;
        private Builder builder;

        private Holder(LuaCompiledSource source) {
            this.source = source;
        }
    }

    private static final class Builder {
        private final int apiVersion;
        private final String id;
        private final String title;
        private final String sourceHash;
        private final LuaTable table = new LuaTable();
        private final List<LuaPortSchema> inputs = new ArrayList<>();
        private final List<LuaPortSchema> outputs = new ArrayList<>();
        private final List<LuaFieldSchema> fields = new ArrayList<>();
        private final Map<String, LuaValue> stateDefaults = new LinkedHashMap<>();
        private final Map<String, LuaFunction> eventHandlers = new LinkedHashMap<>();
        private String category = "utility";
        private NodeStyle style = NodeStyle.STANDARD;
        private LuaExecutionPolicy executionPolicy = LuaExecutionPolicy.INPUT;
        private LuaFunction onRun;
        private boolean built;

        private Builder(int apiVersion, String id, String title, String sourceHash) {
            this.apiVersion = apiVersion;
            this.id = id;
            this.title = title;
            this.sourceHash = sourceHash;
            installMethods();
        }

        private void installMethods() {
            table.set("category", method(args -> {
                category = requireText(args.arg(2), "category");
                return table;
            }));
            table.set("style", method(args -> {
                style = NodeStyle.parse(args.arg(2).checkjstring());
                return table;
            }));
            table.set("input", method(args -> {
                inputs.add(port(args, "input"));
                return table;
            }));
            table.set("output", method(args -> {
                outputs.add(port(args, "output"));
                return table;
            }));
            table.set("field", method(args -> {
                fields.add(field(args));
                return table;
            }));
            table.set("state", method(args -> {
                String stateId = LuaSchemaNames.requireStableId(args.arg(2).checkjstring(), "state");
                if (stateDefaults.putIfAbsent(stateId, args.arg(3)) != null) {
                    throw new LuaDefinitionException("Duplicate state id: " + stateId);
                }
                return table;
            }));
            table.set("execution", method(args -> {
                executionPolicy = LuaExecutionPolicy.parse(args.arg(2).checkjstring());
                return table;
            }));
            table.set("on_run", method(args -> {
                if (onRun != null) {
                    throw new LuaDefinitionException("on_run may only be declared once");
                }
                onRun = args.arg(2).checkfunction();
                return table;
            }));
            table.set("on_event", method(args -> {
                String eventName = LuaSchemaNames.requireStableId(args.arg(2).checkjstring(), "event");
                if (eventHandlers.putIfAbsent(eventName, args.arg(3).checkfunction()) != null) {
                    throw new LuaDefinitionException("Duplicate event handler: " + eventName);
                }
                return table;
            }));
        }

        private VarArgFunction method(java.util.function.Function<Varargs, LuaValue> action) {
            return new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    ensureMutable();
                    if (args.arg1() != table) {
                        throw new LuaDefinitionException("Definition methods must be called with ':'");
                    }
                    return action.apply(args);
                }
            };
        }

        private LuaPortSchema port(Varargs args, String kind) {
            String portId = args.arg(2).checkjstring();
            ConnectionType type = ConnectionType.parse(args.arg(3).checkjstring());
            LuaTable options = options(args.arg(4));
            boolean required = options.get("required").optboolean(true);
            return new LuaPortSchema(portId, type, required, options.get("default"));
        }

        private LuaFieldSchema field(Varargs args) {
            String fieldId = args.arg(2).checkjstring();
            FieldType type = FieldType.parse(args.arg(3).checkjstring());
            LuaTable options = options(args.arg(4));
            List<String> choices = new ArrayList<>();
            LuaValue choiceValue = options.get("choices");
            if (!choiceValue.isnil()) {
                LuaTable choiceTable = choiceValue.checktable();
                for (int index = 1; index <= choiceTable.length(); index++) {
                    choices.add(choiceTable.get(index).checkjstring());
                }
            }
            Double minimum = optionalNumber(options.get("min"));
            Double maximum = optionalNumber(options.get("max"));
            return new LuaFieldSchema(fieldId, type, options.get("default"), choices, minimum, maximum);
        }

        private LuaNodeDefinition build() {
            ensureMutable();
            built = true;
            return new LuaNodeDefinition(
                    apiVersion,
                    id,
                    title,
                    category,
                    style,
                    executionPolicy,
                    inputs,
                    outputs,
                    fields,
                    stateDefaults,
                    onRun,
                    eventHandlers,
                    sourceHash);
        }

        private void ensureMutable() {
            if (built) {
                throw new LuaDefinitionException("Node definition schema is immutable");
            }
        }

        private static LuaTable options(LuaValue value) {
            return value.isnil() ? new LuaTable() : value.checktable();
        }

        private static Double optionalNumber(LuaValue value) {
            return value.isnil() ? null : value.checkdouble();
        }

        private static String requireText(LuaValue value, String label) {
            String text = value.checkjstring().strip();
            if (text.isEmpty() || text.length() > 128) {
                throw new LuaDefinitionException(label + " must contain between 1 and 128 characters");
            }
            return text;
        }
    }
}
