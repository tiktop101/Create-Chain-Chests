package com.chainchests.chainchests.net;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.ChainRouting;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlaceChestPayload(BlockPos pos, BlockPos connection, float chainPosition)
        implements CustomPacketPayload {
    public static final Type<PlaceChestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID, "place_chest"));

    public static final StreamCodec<FriendlyByteBuf, PlaceChestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PlaceChestPayload::pos,
            BlockPos.STREAM_CODEC, PlaceChestPayload::connection,
            ByteBufCodecs.FLOAT, PlaceChestPayload::chainPosition,
            PlaceChestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaceChestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player))
                return;
            payload.apply(player);
        });
    }

    private void apply(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!player.isShiftKeyDown() || !ChainChests.isHangable(held) || !Float.isFinite(chainPosition))
            return;

        Level level = player.level();
        if (!level.isLoaded(pos))
            return;

        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)
                + AllConfigs.server().kinetics.maxChainConveyorLength.get() + 2;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach)
            return;

        if (!(level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be))
            return;

        ItemStack single = held.copyWithCount(1);

        if (connection.equals(BlockPos.ZERO)) {
            if (!be.canAcceptMorePackages())
                return;
            be.addLoopingPackage(new ChainConveyorPackage(be.wrapAngle(chainPosition), single));
        } else {
            if (!be.connections.contains(connection) || !be.canAcceptPackagesFor(connection))
                return;
            be.prepareStats();
            var stats = be.connectionStats.get(connection);
            if (stats == null)
                return;
            float clamped = Mth.clamp(chainPosition, 0, stats.chainLength());
            if (!be.addTravellingPackage(new ChainConveyorPackage(clamped, single), connection))
                return;
        }

        ChainRouting.register(be);
        if (!player.isCreative())
            held.shrink(1);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1f);
    }
}
