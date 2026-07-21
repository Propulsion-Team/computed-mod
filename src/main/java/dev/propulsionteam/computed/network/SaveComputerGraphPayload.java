package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveComputerGraphPayload(
        BlockPos pos, long expectedServerRevision, long editorRevision, CompoundTag graphTag)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveComputerGraphPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "save_computer_graph"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveComputerGraphPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SaveComputerGraphPayload::pos,
            ByteBufCodecs.VAR_LONG,
            SaveComputerGraphPayload::expectedServerRevision,
            ByteBufCodecs.VAR_LONG,
            SaveComputerGraphPayload::editorRevision,
            ByteBufCodecs.COMPOUND_TAG,
            SaveComputerGraphPayload::graphTag,
            SaveComputerGraphPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
