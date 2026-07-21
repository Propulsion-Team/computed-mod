package dev.propulsionteam.computed.internal.node;

import com.mojang.serialization.Codec;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.api.node.DiagnosticSink;
import dev.propulsionteam.computed.api.node.ExecutionPolicy;
import dev.propulsionteam.computed.api.node.NodeDiagnostic;
import dev.propulsionteam.computed.api.node.NodeExecutionContext;
import dev.propulsionteam.computed.api.node.NodeProperty;
import dev.propulsionteam.computed.api.node.NodePropertyBag;
import dev.propulsionteam.computed.api.node.NodeSchema;
import dev.propulsionteam.computed.api.node.NodeType;
import dev.propulsionteam.computed.api.node.PortDefinition;
import dev.propulsionteam.computed.api.node.PortKey;
import dev.propulsionteam.computed.api.node.PortType;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WCheckbox;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.internal.node.api.elements.WTextField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/** Runtime bridge for node types registered through the clean public API. */
final class ApiBackedWNode extends WNode {
    private static final String STATE_TAG = "ComputedApiState";
    private static final String PROPERTIES_TAG = "ComputedApiProperties";

    private final NodeType<Object> type;
    private final Map<PortKey<?>, Integer> inputIndexes = new LinkedHashMap<>();
    private final Map<PortKey<?>, Integer> outputIndexes = new LinkedHashMap<>();
    private final Map<NodeProperty<?>, WElement> propertyControls = new LinkedHashMap<>();
    private NodePropertyBag properties;
    private Object state;
    private List<PortDefinition<?>> schemaSignature = List.of();

    @SuppressWarnings("unchecked")
    ApiBackedWNode(NodeType<?> type, int x, int y) {
        super(type.id(), type.title().getString(), x, y);
        this.type = (NodeType<Object>) Objects.requireNonNull(type, "type");
        properties = type.defaultProperties();
        state = this.type.defaultState();
        buildGenericPropertyControls();
        rebuildSchema();
        setEvaluator(ignored -> evaluatePublicNode());
    }

    private void rebuildSchema() {
        inputIndexes.clear();
        outputIndexes.clear();
        getInputs().clear();
        getOutputs().clear();
        markPinSchemaChanged();
        NodeSchema schema = type.schema(properties);
        schemaSignature = schema.ports();
        for (PortDefinition<?> definition : schema.inputs()) {
            inputIndexes.put(definition.key(), inputIndexes.size());
            addInput(
                    definition.key().id(),
                    definition.label().getString(),
                    legacyType(definition.key().type()),
                    definition.key().type().defaultColor());
        }
        for (PortDefinition<?> definition : schema.outputs()) {
            outputIndexes.put(definition.key(), outputIndexes.size());
            addOutput(
                    definition.key().id(),
                    definition.label().getString(),
                    legacyType(definition.key().type()),
                    definition.key().type().defaultColor());
        }
        updateLayout();
    }

    private void buildGenericPropertyControls() {
        for (NodeProperty<?> property : type.properties()) {
            if (property.valueClass() == Boolean.class) {
                WCheckbox checkbox = new WCheckbox(property.title().getString());
                checkbox.setChecked((Boolean) properties.values().get(property.key()));
                propertyControls.put(property, checkbox);
                addElement(checkbox);
            } else if (property.valueClass() == String.class
                    || property.valueClass() == Double.class
                    || property.valueClass() == Integer.class) {
                addElement(new WLabel(property.title().getString(), 0xFFAAAAAA));
                WTextField field = new WTextField(88);
                field.setValue(String.valueOf(properties.values().get(property.key())));
                propertyControls.put(property, field);
                addElement(field);
            } else {
                addElement(new WLabel(property.title().getString() + " (custom)", 0xFFFFAA55));
            }
        }
    }

    private void syncPropertiesFromControls() {
        NodePropertyBag next = properties;
        for (Map.Entry<NodeProperty<?>, WElement> entry : propertyControls.entrySet()) {
            NodeProperty<?> property = entry.getKey();
            try {
                Object value;
                if (entry.getValue() instanceof WCheckbox checkbox) {
                    value = checkbox.isChecked();
                } else if (entry.getValue() instanceof WTextField field) {
                    String raw = field.getValue().trim();
                    if (property.valueClass() == String.class) value = raw;
                    else if (property.valueClass() == Integer.class) value = Integer.parseInt(raw);
                    else value = Double.parseDouble(raw.replace(',', '.'));
                } else {
                    continue;
                }
                next = withUnchecked(next, property, value);
            } catch (IllegalArgumentException ignored) {
                // Keep the last valid value while the user is typing or validation rejects the edit.
            }
        }
        if (!next.values().equals(properties.values())) {
            properties = next;
            NodeSchema schema = type.schema(properties);
            if (!schema.ports().equals(schemaSignature)) rebuildSchema();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static NodePropertyBag withUnchecked(NodePropertyBag bag, NodeProperty property, Object value) {
        return bag.with(property, value);
    }

    private void updateControlsFromProperties() {
        for (Map.Entry<NodeProperty<?>, WElement> entry : propertyControls.entrySet()) {
            Object value = properties.values().get(entry.getKey().key());
            if (entry.getValue() instanceof WCheckbox checkbox && value instanceof Boolean bool) {
                checkbox.setChecked(bool);
            } else if (entry.getValue() instanceof WTextField field && value != null) {
                field.setValue(String.valueOf(value));
            }
        }
    }

    private void evaluatePublicNode() {
        syncPropertiesFromControls();
        try {
            Object nextState = type.evaluator().execute(state, new Context());
            state = Objects.requireNonNull(nextState, "Node executor returned null state");
        } catch (Exception exception) {
            resetOutputs();
            Computed.LOGGER.error("Public node {} ({}) failed to evaluate", type.id(), getId(), exception);
        }
    }

    private void resetOutputs() {
        for (WPin pin : getOutputs()) {
            switch (pin.getDataType()) {
                case NUMBER -> pin.setValue(0.0);
                case STRING -> pin.setStringValue("");
                case WIDGET -> pin.setWidgetValue(null);
            }
        }
    }

    @Override
    public boolean isStateBoundary() {
        return type.stateBoundary();
    }

    @Override
    public ExecutionPolicy executionPolicy() {
        return type.executionPolicy();
    }

    @Override
    public CompoundTag save() {
        syncPropertiesFromControls();
        CompoundTag tag = super.save();
        encode(type.stateCodec(), state).ifPresent(encoded -> tag.put(STATE_TAG, encoded));
        CompoundTag propertyTag = new CompoundTag();
        for (NodeProperty<?> property : type.properties()) {
            encodeProperty(property, properties, propertyTag);
        }
        if (!propertyTag.isEmpty()) {
            tag.put(PROPERTIES_TAG, propertyTag);
        }
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains(PROPERTIES_TAG, Tag.TAG_COMPOUND)) {
            NodePropertyBag.Builder builder = NodePropertyBag.builder(type.properties());
            CompoundTag propertyTag = tag.getCompound(PROPERTIES_TAG);
            for (NodeProperty<?> property : type.properties()) {
                decodeProperty(property, propertyTag, builder);
            }
            properties = builder.build();
            rebuildSchema();
        }
        super.load(tag);
        updateControlsFromProperties();
        if (tag.contains(STATE_TAG)) {
            type.stateCodec()
                    .parse(NbtOps.INSTANCE, tag.get(STATE_TAG))
                    .resultOrPartial(message -> Computed.LOGGER.warn(
                            "Could not decode state for public node {}: {}", type.id(), message))
                    .ifPresent(decoded -> state = decoded);
        }
    }

    @Override
    public void render(
            net.minecraft.client.gui.GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        syncPropertiesFromControls();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            dev.propulsionteam.computed.api.node.client.ComputedNodeClientApi.presentation(type.id())
                    .ifPresent(presentation -> presentation.render(
                            new PresentationContext(graphics, mouseX, mouseY, partialTick)));
        }
    }

    private static Optional<Tag> encode(Codec<Object> codec, Object value) {
        return codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(message -> Computed.LOGGER.warn("Could not encode public node state: {}", message));
    }

    private static <T> void encodeProperty(
            NodeProperty<T> property, NodePropertyBag bag, CompoundTag target) {
        property.codec()
                .encodeStart(NbtOps.INSTANCE, bag.get(property))
                .resultOrPartial(message -> Computed.LOGGER.warn(
                        "Could not encode public node property {}: {}", property.key(), message))
                .ifPresent(value -> target.put(property.key(), value));
    }

    private static <T> void decodeProperty(
            NodeProperty<T> property, CompoundTag source, NodePropertyBag.Builder target) {
        if (!source.contains(property.key())) {
            return;
        }
        property.codec()
                .parse(NbtOps.INSTANCE, source.get(property.key()))
                .resultOrPartial(message -> Computed.LOGGER.warn(
                        "Could not decode public node property {}: {}", property.key(), message))
                .filter(property::isValid)
                .ifPresent(value -> target.set(property, value));
    }

    private static WPin.DataType legacyType(PortType<?> type) {
        if (type == PortType.NUMBER) return WPin.DataType.NUMBER;
        if (type == PortType.STRING) return WPin.DataType.STRING;
        return WPin.DataType.WIDGET;
    }

    private final class Context implements NodeExecutionContext {
        @Override
        public NodePropertyBag properties() {
            return properties;
        }

        @Override
        public <T> T input(PortKey<T> key) {
            Integer index = inputIndexes.get(key);
            if (index == null) {
                throw new IllegalArgumentException("Unknown input port " + key + " on " + type.id());
            }
            WPin pin = getInputs().get(index);
            Object value = switch (pin.getDataType()) {
                case NUMBER -> pin.getValue();
                case STRING -> pin.getStringValue();
                case WIDGET -> pin.getWidgetValue();
            };
            return key.type().castOrDefault(value);
        }

        @Override
        public <T> void output(PortKey<T> key, T value) {
            Integer index = outputIndexes.get(key);
            if (index == null) {
                throw new IllegalArgumentException("Unknown output port " + key + " on " + type.id());
            }
            if (!key.type().accepts(value)) {
                throw new IllegalArgumentException("Output " + key + " received an incompatible value");
            }
            WPin pin = getOutputs().get(index);
            if (key.type() == PortType.NUMBER) {
                pin.setValue((Double) value);
            } else if (key.type() == PortType.STRING) {
                pin.setStringValue((String) value);
            } else {
                pin.setWidgetValue(value);
            }
        }

        @Override
        public boolean isInputConnected(PortKey<?> key) {
            Integer index = inputIndexes.get(key);
            return index != null && getInputs().get(index).isConnected();
        }

        @Override
        public long gameTick() {
            return level().map(ServerLevel::getGameTime).orElse(0L);
        }

        @Override
        public long graphStep() {
            return evaluationGraph() == null ? 0L : evaluationGraph().getSimulationStepCounter();
        }

        @Override
        public boolean isPreview() {
            return level().isEmpty();
        }

        @Override
        public Optional<ServerLevel> level() {
            ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
            return host != null && host.getLevel() instanceof ServerLevel serverLevel
                    ? Optional.of(serverLevel)
                    : Optional.empty();
        }

        @Override
        public Optional<BlockPos> origin() {
            ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
            return host == null ? Optional.empty() : Optional.of(host.getBlockPos());
        }

        @Override
        public boolean sideEffectsAllowed() {
            return level().isPresent();
        }

        @Override
        public DiagnosticSink diagnostics() {
            return diagnostic -> logDiagnostic(diagnostic.forNode(getId()));
        }

        private void logDiagnostic(NodeDiagnostic diagnostic) {
            switch (diagnostic.severity()) {
                case INFO -> Computed.LOGGER.info("Node {}: {}", type.id(), diagnostic.message().getString());
                case WARNING -> Computed.LOGGER.warn("Node {}: {}", type.id(), diagnostic.message().getString());
                case ERROR -> Computed.LOGGER.error("Node {}: {}", type.id(), diagnostic.message().getString());
            }
        }
    }

    private final class PresentationContext
            implements dev.propulsionteam.computed.api.node.client.NodePresentationContext {
        private final net.minecraft.client.gui.GuiGraphics graphics;
        private final int mouseX;
        private final int mouseY;
        private final float partialTick;

        private PresentationContext(
                net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.graphics = graphics;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        @Override public java.util.UUID nodeId() { return getId(); }
        @Override public NodeType<?> nodeType() { return type; }
        @Override public NodePropertyBag properties() { return properties; }
        @Override public net.minecraft.client.gui.GuiGraphics graphics() { return graphics; }
        @Override public int x() { return getX(); }
        @Override public int y() { return getY() + 16; }
        @Override public int width() { return getWidth(); }
        @Override public int height() { return Math.max(0, getHeight() - 16); }
        @Override public int mouseX() { return mouseX; }
        @Override public int mouseY() { return mouseY; }
        @Override public float partialTick() { return partialTick; }

        @Override
        public <T> void setProperty(NodeProperty<T> property, T value) {
            properties = properties.with(property, value);
            updateControlsFromProperties();
            NodeSchema schema = type.schema(properties);
            if (!schema.ports().equals(schemaSignature)) rebuildSchema();
        }

        @Override
        public void renderGenericPropertyControls() {
            // Standard WElements were already rendered by the base node immediately before this hook.
        }
    }
}
