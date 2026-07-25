package dev.propulsionteam.computed.content.blocks;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.LuaGraphScheduler;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ComputerBlockEntity extends BaseContainerBlockEntity implements BuiltinEndpointHost {
    public static final int CONTAINER_SIZE = 9;
    public static final String PROGRAM_TAG = "ComputedProgram";
    private static final int MAX_PROGRAM_NODES = 4096;
    private static final int MAX_PROGRAM_CONNECTIONS = 20_000;
    private static final int MAX_PROGRAM_BYTES = 4 * 1024 * 1024;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private ComputedProgramV3 program;
    private LuaGraphScheduler scheduler;
    private CompoundTag unreadableProgramData;
    private UUID computerUuid;
    private long programRevision;
    private transient boolean dropsHandled;

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.COMPUTER_BLOCK_ENTITY.get(), pos, state);
        program = ComputedProgramV3.empty(stableGraphId(pos));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity computer) {
        if (level.isClientSide || computer.isRemoved()) {
            return;
        }
        LuaGraphScheduler active = computer.ensureScheduler();
        ComputedProgramV3 before = computer.program;
        active.tick(false);
        ComputedProgramV3 after = active.snapshot(computer.programRevision);
        computer.program = after;
        if (!before.persistentState().equals(after.persistentState())) {
            computer.setChanged();
        }
    }

    public int getEmittedRedstone(Direction fromNeighborTowardSelf) {
        return 0;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.computed.computer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items.clear();
        for (int index = 0; index < Math.min(newItems.size(), items.size()); index++) {
            items.set(index, newItems.get(index));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ComputerPeripheralMenu(
                ComputedRegistries.COMPUTER_PERIPHERAL_MENU.get(),
                containerId,
                playerInventory,
                this);
    }

    public CompoundTag getGraphData() {
        CompoundTag envelope = new CompoundTag();
        envelope.put(PROGRAM_TAG, ProgramV3Codec.encode(snapshotProgram()));
        Peripherals.writePeripheralUnlockTag(this, envelope);
        return envelope;
    }

    public ComputedProgramV3 getProgram() {
        return snapshotProgram();
    }

    public long getProgramRevision() {
        return programRevision;
    }

    public boolean handleWidgetInput(UUID nodeId, double value) {
        return false;
    }

    public record ApplyGraphResult(boolean accepted, long serverRevision, String message) {
        static ApplyGraphResult accepted(long revision) {
            return new ApplyGraphResult(true, revision, "ok");
        }

        static ApplyGraphResult rejected(long revision, String message) {
            return new ApplyGraphResult(false, revision, message);
        }
    }

    public ApplyGraphResult applyGraphFromNetwork(CompoundTag tag, long expectedRevision) {
        if (expectedRevision != programRevision) {
            return ApplyGraphResult.rejected(
                    programRevision,
                    "stale editor revision (expected " + programRevision + ", received " + expectedRevision + ")");
        }
        String sizeError = validateEncodedSize(tag);
        if (sizeError != null) {
            return ApplyGraphResult.rejected(programRevision, sizeError);
        }
        ComputedProgramV3 incoming;
        try {
            ProgramV3Codec.LoadResult decoded =
                    ProgramV3Codec.decode(tag, worldPosition.toShortString(), ignored -> {});
            if (decoded.discardedLegacy()) {
                return ApplyGraphResult.rejected(programRevision, "legacy graph and clipboard formats are not accepted");
            }
            incoming = decoded.program();
        } catch (RuntimeException exception) {
            return ApplyGraphResult.rejected(
                    programRevision,
                    "program could not be decoded: " + exception.getMessage());
        }
        String validationError = validateProgram(incoming);
        if (validationError != null) {
            return ApplyGraphResult.rejected(programRevision, validationError);
        }
        ComputedProgramV3 stateMerged = preserveRuntimeState(incoming, snapshotProgram());
        LuaGraphScheduler nextScheduler;
        try {
            nextScheduler = new LuaGraphScheduler(stateMerged, getOrCreateUuid(), this);
            var error = nextScheduler.validationDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.severity()
                            == dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Severity.ERROR)
                    .findFirst();
            if (error.isPresent()) {
                nextScheduler.unload();
                return ApplyGraphResult.rejected(
                        programRevision,
                        "program validation failed: " + error.get().message());
            }
        } catch (RuntimeException exception) {
            return ApplyGraphResult.rejected(
                    programRevision,
                    "program validation failed: " + exception.getMessage());
        }
        if (scheduler != null) {
            scheduler.unload();
        }
        programRevision++;
        program = stateMerged.withRevision(programRevision);
        scheduler = nextScheduler;
        unreadableProgramData = null;
        setChanged();
        return ApplyGraphResult.accepted(programRevision);
    }

    private String validateProgram(ComputedProgramV3 candidate) {
        if (candidate.rootGraph().nodes().size() > MAX_PROGRAM_NODES) {
            return "program exceeds the node limit of " + MAX_PROGRAM_NODES;
        }
        if (candidate.rootGraph().connections().size() > MAX_PROGRAM_CONNECTIONS) {
            return "program exceeds the connection limit of " + MAX_PROGRAM_CONNECTIONS;
        }
        if (candidate.library().size() > ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS) {
            return "program exceeds the embedded definition limit of "
                    + ComputedProgramV3.MAX_EMBEDDED_DEFINITIONS;
        }
        return null;
    }

    private static ComputedProgramV3 preserveRuntimeState(
            ComputedProgramV3 incoming,
            ComputedProgramV3 authoritative) {
        Map<UUID, GraphNode> currentNodes = new LinkedHashMap<>();
        authoritative.rootGraph().nodes().forEach(node -> currentNodes.put(node.id(), node));
        Map<UUID, CompoundTag> merged = new LinkedHashMap<>(incoming.persistentState());
        incoming.rootGraph().nodes().forEach(node -> {
            GraphNode current = currentNodes.get(node.id());
            CompoundTag state = authoritative.persistentState().get(node.id());
            if (current != null
                    && state != null
                    && current.definitionId().equals(node.definitionId())
                    && current.definitionHash().equals(node.definitionHash())) {
                merged.put(node.id(), state);
            }
        });
        return new ComputedProgramV3(
                incoming.revision(),
                incoming.rootGraph(),
                incoming.library(),
                merged,
                incoming.metadata());
    }

    private static String validateEncodedSize(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return bytes.size() > MAX_PROGRAM_BYTES
                    ? "program exceeds the encoded size limit of " + MAX_PROGRAM_BYTES + " bytes"
                    : null;
        } catch (IOException | RuntimeException exception) {
            return "program NBT could not be measured safely";
        }
    }

    public boolean tryInsertPeripheralFromHand(ItemStack stack) {
        if (!Peripherals.isPeripheral(stack)) {
            return false;
        }
        ItemStack one = stack.split(1);
        for (int index = 0; index < CONTAINER_SIZE; index++) {
            if (getItem(index).isEmpty() && Peripherals.mayPlaceInComputer(this, index, one)) {
                setItem(index, one);
                return true;
            }
        }
        stack.grow(1);
        return false;
    }

    public boolean hasPeripheralEquipped(net.minecraft.resources.ResourceLocation nodeTypeId) {
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        computerUuid = tag.hasUUID("ComputerUUID") ? tag.getUUID("ComputerUUID") : null;
        loadProgramData(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        writeStoredProgram(tag);
        if (computerUuid != null) {
            tag.putUUID("ComputerUUID", computerUuid);
        }
    }

    public UUID getOrCreateUuid() {
        if (computerUuid == null) {
            computerUuid = UUID.randomUUID();
            setChanged();
        }
        return computerUuid;
    }

    public boolean hasStoredState() {
        if (unreadableProgramData != null && !unreadableProgramData.isEmpty()) {
            return true;
        }
        if (!program.rootGraph().nodes().isEmpty() || !program.library().isEmpty()) {
            return true;
        }
        return items.stream().anyMatch(stack -> !stack.isEmpty());
    }

    public void markDropsHandled() {
        dropsHandled = true;
    }

    public boolean dropsHandled() {
        return dropsHandled;
    }

    @Override
    public void setRemoved() {
        if (scheduler != null) {
            scheduler.unload();
            scheduler = null;
        }
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeStoredProgram(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadProgramData(tag);
    }

    @Override
    public double worldTime() {
        return level == null ? 0 : level.getDayTime();
    }

    @Override
    public void runCommand(String commandText) {
        if (commandText == null
                || commandText.isBlank()
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        String command = commandText.startsWith("/") ? commandText.substring(1) : commandText;
        if (server == null || command.isBlank()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(serverLevel)
                .withPosition(center)
                .withPermission(4)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private LuaGraphScheduler ensureScheduler() {
        if (scheduler == null) {
            scheduler = new LuaGraphScheduler(program, getOrCreateUuid(), this);
        }
        return scheduler;
    }

    private ComputedProgramV3 snapshotProgram() {
        if (scheduler != null) {
            program = scheduler.snapshot(programRevision);
        }
        return program.withRevision(programRevision);
    }

    private void writeStoredProgram(CompoundTag target) {
        if (unreadableProgramData != null && !unreadableProgramData.isEmpty()) {
            for (String key : unreadableProgramData.getAllKeys()) {
                Tag value = unreadableProgramData.get(key);
                if (value != null) {
                    target.put(key, value.copy());
                }
            }
            return;
        }
        target.put(PROGRAM_TAG, ProgramV3Codec.encode(snapshotProgram()));
    }

    private void loadProgramData(CompoundTag source) {
        if (scheduler != null) {
            scheduler.unload();
            scheduler = null;
        }
        if (!containsProgramData(source)) {
            program = ComputedProgramV3.empty(stableGraphId(worldPosition));
            programRevision = 0;
            unreadableProgramData = null;
            return;
        }
        try {
            ProgramV3Codec.LoadResult decoded = ProgramV3Codec.decode(
                    source,
                    worldPosition.toShortString(),
                    Computed.LOGGER::warn);
            program = decoded.program();
            programRevision = program.revision();
            unreadableProgramData = null;
        } catch (RuntimeException exception) {
            Computed.LOGGER.error(
                    "Could not load Computed format-3 program at {}; preserving raw program data",
                    worldPosition,
                    exception);
            program = ComputedProgramV3.empty(stableGraphId(worldPosition));
            programRevision = rawProgramRevision(source);
            unreadableProgramData = copyProgramFields(source);
        }
    }

    private static CompoundTag copyProgramFields(CompoundTag source) {
        CompoundTag preserved = new CompoundTag();
        for (String key : List.of(PROGRAM_TAG, "formatVersion", "revision", "graph", "library", "states", "metadata")) {
            Tag value = source.get(key);
            if (value != null) {
                preserved.put(key, value.copy());
            }
        }
        return preserved;
    }

    private static boolean containsProgramData(CompoundTag source) {
        return source.contains(PROGRAM_TAG, Tag.TAG_COMPOUND)
                || source.contains("formatVersion")
                || source.contains("ComputerGraph", Tag.TAG_COMPOUND)
                || source.contains("ComputerFunctions")
                || source.contains("graph", Tag.TAG_COMPOUND)
                || source.contains("nodes");
    }

    private static long rawProgramRevision(CompoundTag source) {
        long revision = Math.max(0, source.getLong("revision"));
        if (source.contains(PROGRAM_TAG, Tag.TAG_COMPOUND)) {
            revision = Math.max(revision, source.getCompound(PROGRAM_TAG).getLong("revision"));
        }
        return revision;
    }

    private static UUID stableGraphId(BlockPos pos) {
        return UUID.nameUUIDFromBytes(
                ("computed:graph:" + pos.toShortString()).getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
