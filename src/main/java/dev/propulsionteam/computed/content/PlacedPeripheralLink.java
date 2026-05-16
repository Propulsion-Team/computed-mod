package dev.propulsionteam.computed.content;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record PlacedPeripheralLink(BlockPos pos, ResourceLocation kind, int instanceId) {}
