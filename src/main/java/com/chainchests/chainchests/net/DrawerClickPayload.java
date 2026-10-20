package com.chainchests.chainchests.net;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.HangableKind;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DrawerClickPayload(BlockPos pos, int netId, boolean left, double x, double y)
        implements CustomPacketPayload {
    public static final Type<DrawerClickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID, "drawer_click"));

    public static final StreamCodec<FriendlyByteBuf, DrawerClickPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DrawerClickPayload::pos,
            ByteBufCodecs.VAR_INT, DrawerClickPayload::netId,
            ByteBufCodecs.BOOL, DrawerClickPayload::left,
            ByteBufCodecs.DOUBLE, DrawerClickPayload::x,
            ByteBufCodecs.DOUBLE, DrawerClickPayload::y,
            DrawerClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DrawerClickPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player)
                payload.apply(player);
        });
    }

    private void apply(ServerPlayer player) {
        if (player.isSpectator())
            return;
        Level level = player.level();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be))
            return;
        ChainConveyorPackage box = ChainChests.findPackage(be, netId);
        if (box == null || HangableKind.of(box.item) != HangableKind.DRAWER)
            return;
        Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(pos);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3;
        if (player.distanceToSqr(at) > reach * reach)
            return;
        com.chainchests.chainchests.compat.DrawerCompat.interact(player, be, box, left,
                Math.max(0, Math.min(1, x)), Math.max(0, Math.min(1, y)));
    }
}
