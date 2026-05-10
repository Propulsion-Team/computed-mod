package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenComputerEditorPayload(BlockPos pos, CompoundTag graphTag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenComputerEditorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "open_computer_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenComputerEditorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenComputerEditorPayload::pos,
            ByteBufCodecs.COMPOUND_TAG,
            OpenComputerEditorPayload::graphTag,
            OpenComputerEditorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
