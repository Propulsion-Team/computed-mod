package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server acknowledgement for a transactional editor save. */
public record ComputerGraphSaveResultPayload(
        BlockPos pos, boolean accepted, long serverRevision, long editorRevision, String message)
        implements CustomPacketPayload {
    public static final Type<ComputerGraphSaveResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "computer_graph_save_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerGraphSaveResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    ComputerGraphSaveResultPayload::pos,
                    ByteBufCodecs.BOOL,
                    ComputerGraphSaveResultPayload::accepted,
                    ByteBufCodecs.VAR_LONG,
                    ComputerGraphSaveResultPayload::serverRevision,
                    ByteBufCodecs.VAR_LONG,
                    ComputerGraphSaveResultPayload::editorRevision,
                    ByteBufCodecs.STRING_UTF8,
                    ComputerGraphSaveResultPayload::message,
                    ComputerGraphSaveResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
