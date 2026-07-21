package dev.propulsionteam.computed.internal.node.api;

import dev.propulsionteam.computed.api.node.ExecutionPolicy;
import dev.propulsionteam.computed.api.node.NodeExecutionContext;
import dev.propulsionteam.computed.api.node.NodeSchema;
import dev.propulsionteam.computed.api.node.NodeType;
import dev.propulsionteam.computed.api.node.PortDefinition;
import dev.propulsionteam.computed.api.node.PortKey;
import dev.propulsionteam.computed.api.node.PortType;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Transitional executor for Computed-owned nodes that still use the internal editor/runtime model.
 *
 * <p>The public descriptor never exposes {@link WNode}: its state is a complete NBT value and its
 * evaluator speaks only {@link NodeExecutionContext}. Each evaluation hydrates a fresh internal
 * instance from a copy of the immutable prior state, applies typed inputs, executes once, publishes
 * typed outputs, and returns a new state snapshot. This makes the public {@link NodeType} executable
 * while the individual built-ins are migrated to native executors.</p>
 */
public final class InternalNodeTypeAdapter {
    private static final Pattern VALID_PORT_ID = Pattern.compile("[a-z][a-z0-9_.-]*");
    private static final String TICK_ACCUMULATOR = "ComputedApiTickAccumulator";
    private static final String TICK_LAST_GAME_TICK = "ComputedApiTickLastGameTick";

    private InternalNodeTypeAdapter() {}

    public static NodeType<CompoundTag> describe(
            NodeRegistry.NodeFactory factory, WNode sample, ResourceLocation category) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(category, "category");

        ResourceLocation typeId = sample.getTypeId();
        // Saving assigns stable keys to older built-ins whose constructors only supplied labels.
        sample.save();
        NodeSchema schema = schema(sample);
        boolean stateBoundary = sample.isStateBoundary();

        return NodeType.<CompoundTag>builder(typeId)
                .title(Component.literal(sample.getTitle()))
                .category(category)
                .schema(schema)
                .stateCodec(CompoundTag.CODEC)
                .defaultState(() -> initialState(factory, typeId))
                .stateBoundary(stateBoundary)
                .executionPolicy(executionPolicy(sample, stateBoundary))
                .evaluator((priorState, context) -> execute(factory, typeId, priorState, context))
                .build();
    }

    private static CompoundTag execute(
            NodeRegistry.NodeFactory factory,
            ResourceLocation typeId,
            CompoundTag priorState,
            NodeExecutionContext context) {
        Objects.requireNonNull(priorState, "priorState");
        Objects.requireNonNull(context, "context");
        WNode node = create(factory, typeId);
        node.load(priorState.copy());
        copyInputs(context, node);

        double tickAccumulator = 0.0;
        if (WGraph.TICK_NODE_TYPE.equals(typeId)) {
            tickAccumulator = evaluateTickNode(node, priorState, context);
        } else {
            evaluateWithContext(node, context);
        }
        copyOutputs(node, context);

        CompoundTag nextState = node.save();
        if (WGraph.TICK_NODE_TYPE.equals(typeId)) {
            nextState.putDouble(TICK_ACCUMULATOR, tickAccumulator);
            nextState.putLong(TICK_LAST_GAME_TICK, context.gameTick());
        }
        return nextState;
    }

    private static void evaluateWithContext(WNode node, NodeExecutionContext context) {
        Runnable evaluation = () -> WGraph.evaluateIsolated(node, context.graphStep(), true);

        // Client previews have no server host by construction and never bind one below. Keeping this
        // path free of server block-entity linkage also lets descriptor execution be unit tested on
        // the API-only classpath.
        if (context.isPreview()) {
            evaluation.run();
            return;
        }

        // Internal world nodes predate NodeExecutionContext and resolve their computer through this
        // host scope. Explicit side-effect suppression must not inherit a live server host.
        if (!context.sideEffectsAllowed()) {
            ComputedGraphExecution.withoutHost(evaluation);
            return;
        }

        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) {
            host = hostFrom(context).orElse(null);
        }
        if (host == null) {
            evaluation.run();
        } else {
            ComputedGraphExecution.withHost(host, evaluation);
        }
    }

    private static Optional<ComputerBlockEntity> hostFrom(NodeExecutionContext context) {
        if (context.level().isEmpty() || context.origin().isEmpty()) {
            return Optional.empty();
        }
        BlockEntity blockEntity = context.level().get().getBlockEntity(context.origin().get());
        return blockEntity instanceof ComputerBlockEntity computer
                ? Optional.of(computer)
                : Optional.empty();
    }

    private static double evaluateTickNode(
            WNode node, CompoundTag priorState, NodeExecutionContext context) {
        if (node.getOutputs().size() < 2) {
            return 0.0;
        }
        double accumulator = priorState.contains(TICK_ACCUMULATOR)
                ? Math.max(0.0, priorState.getDouble(TICK_ACCUMULATOR))
                : 0.0;
        long elapsedTicks = priorState.contains(TICK_LAST_GAME_TICK)
                ? Math.max(0L, context.gameTick() - priorState.getLong(TICK_LAST_GAME_TICK))
                : 1L;
        accumulator += elapsedTicks / (double) WGraph.MAX_TICK_RATE;
        double rate = readTickRate(node);
        boolean pulse = false;
        if (rate > 1.0e-9 && accumulator >= 1.0 / rate) {
            pulse = true;
            node.getOutputs().get(1).setValue(accumulator);
            accumulator = 0.0;
        } else {
            node.getOutputs().get(1).setValue(accumulator);
        }
        node.getOutputs().get(0).setValue(pulse ? 1.0 : 0.0);
        return accumulator;
    }

    private static double readTickRate(WNode node) {
        for (WElement element : node.getElements()) {
            if (element instanceof dev.propulsionteam.computed.internal.node.api.elements.WSlider slider) {
                return Mth.clamp(slider.getValue(), 0.0, WGraph.MAX_TICK_RATE);
            }
        }
        return WGraph.MAX_TICK_RATE;
    }

    private static void copyInputs(NodeExecutionContext context, WNode node) {
        List<WPin> pins = node.getInputs();
        for (int i = 0; i < pins.size(); i++) {
            WPin pin = pins.get(i);
            String id = stablePortId(pins, i, "input");
            switch (pin.getDataType()) {
                case NUMBER -> {
                    PortKey<Double> key = PortKey.of(id, PortType.NUMBER);
                    pin.setValue(context.input(key));
                    pin.setConnected(context.isInputConnected(key));
                }
                case STRING -> {
                    PortKey<String> key = PortKey.of(id, PortType.STRING);
                    pin.setStringValue(context.input(key));
                    pin.setConnected(context.isInputConnected(key));
                }
                case WIDGET -> {
                    PortKey<Object> key = PortKey.of(id, PortType.WIDGET);
                    pin.setWidgetValue(context.input(key));
                    pin.setConnected(context.isInputConnected(key));
                }
            }
        }
    }

    private static void copyOutputs(WNode node, NodeExecutionContext context) {
        List<WPin> pins = node.getOutputs();
        for (int i = 0; i < pins.size(); i++) {
            WPin pin = pins.get(i);
            String id = stablePortId(pins, i, "output");
            switch (pin.getDataType()) {
                case NUMBER -> context.output(PortKey.of(id, PortType.NUMBER), pin.getValue());
                case STRING -> context.output(PortKey.of(id, PortType.STRING), pin.getStringValue());
                case WIDGET -> context.output(PortKey.of(id, PortType.WIDGET), pin.getWidgetValue());
            }
        }
    }

    private static NodeSchema schema(WNode node) {
        NodeSchema.Builder schema = NodeSchema.builder();
        addPorts(schema, node.getInputs(), true);
        addPorts(schema, node.getOutputs(), false);
        return schema.build();
    }

    private static void addPorts(NodeSchema.Builder schema, List<WPin> pins, boolean input) {
        for (int i = 0; i < pins.size(); i++) {
            WPin pin = pins.get(i);
            String id = stablePortId(pins, i, input ? "input" : "output");
            PortKey<?> key = switch (pin.getDataType()) {
                case NUMBER -> PortKey.of(id, PortType.NUMBER);
                case STRING -> PortKey.of(id, PortType.STRING);
                case WIDGET -> PortKey.of(id, PortType.WIDGET);
            };
            schema.port(input
                    ? PortDefinition.input(key, Component.literal(pin.getName()))
                    : PortDefinition.output(key, Component.literal(pin.getName())));
        }
    }

    private static String stablePortId(List<WPin> pins, int index, String direction) {
        WPin pin = pins.get(index);
        String explicit = pin.getStableKey();
        if (explicit != null && VALID_PORT_ID.matcher(explicit).matches()) {
            return explicit;
        }

        String label = slug(pin.getName());
        String base = direction + "." + label;
        Map<String, Integer> occurrences = new HashMap<>();
        for (int i = 0; i <= index; i++) {
            String candidate = direction + "." + slug(pins.get(i).getName());
            occurrences.merge(candidate, 1, Integer::sum);
        }
        int duplicate = occurrences.getOrDefault(base, 1);
        return duplicate == 1 ? base : base + "." + duplicate;
    }

    private static String slug(String label) {
        String slug = label == null ? "port" : label.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^[^a-z]+", "")
                .replaceAll("_+$", "");
        return slug.isEmpty() ? "port" : slug;
    }

    private static ExecutionPolicy executionPolicy(WNode node, boolean stateBoundary) {
        ExecutionPolicy declared = node.executionPolicy();
        if (declared != ExecutionPolicy.INPUT_DRIVEN) {
            return declared;
        }
        if (stateBoundary) {
            return ExecutionPolicy.EVERY_GRAPH_STEP;
        }
        return node.getInputs().isEmpty()
                ? ExecutionPolicy.EVERY_GAME_TICK
                : ExecutionPolicy.INPUT_DRIVEN;
    }

    private static CompoundTag initialState(NodeRegistry.NodeFactory factory, ResourceLocation typeId) {
        return create(factory, typeId).save();
    }

    private static WNode create(NodeRegistry.NodeFactory factory, ResourceLocation typeId) {
        WNode node = Objects.requireNonNull(factory.create(0, 0), "Factory returned null for " + typeId);
        if (!typeId.equals(node.getTypeId())) {
            throw new IllegalStateException(
                    "Factory for " + typeId + " created node type " + node.getTypeId());
        }
        return node;
    }
}
