package com.chainchests.chainchests.net;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.HangableKind;
import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenChestPayload(BlockPos pos, int netId) implements CustomPacketPayload {
    public static final Type<OpenChestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID, "open_chest"));

    public static final StreamCodec<FriendlyByteBuf, OpenChestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenChestPayload::pos,
            ByteBufCodecs.VAR_INT, OpenChestPayload::netId,
            OpenChestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenChestPayload payload, IPayloadContext ctx) {
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
        if (box == null || !ChainChests.isHangable(box.item))
            return;

        Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(pos);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3;
        if (player.distanceToSqr(at) > reach * reach)
            return;

        HangableKind kind = HangableKind.of(box.item);
        if (kind == null)
            return;

        if (kind == HangableKind.ENDER_CHEST) {
            openEnderChest(player, level, be, box, at);
            return;
        }

        if (kind == HangableKind.DRAWER) {
            com.chainchests.chainchests.compat.DrawerCompat.open(player, be, box);
            return;
        }
        if (kind == HangableKind.SOPHISTICATED) {
            com.chainchests.chainchests.compat.SophisticatedCompat.open(player, be, box);
            return;
        }

        HangingChestContainer container = HangingChestContainer.forPackage(be, box);
        var opened = player.openMenu(new SimpleMenuProvider((id, inv, p) -> kind == HangableKind.SHULKER_BOX
                ? new ShulkerBoxMenu(id, inv, container)
                : ChestMenu.threeRows(id, inv, container), box.item.getHoverName()));
        if (opened.isEmpty())
            container.releaseIfUnused();
    }

    private static void openEnderChest(ServerPlayer player, Level level, ChainConveyorBlockEntity be,
            ChainConveyorPackage box, Vec3 at) {
        var ender = player.getEnderChestInventory();
        HangableKind kind = HangableKind.ENDER_CHEST;
        level.playSound(null, at.x, at.y, at.z, kind.openSound, SoundSource.BLOCKS, 0.5f,
                level.random.nextFloat() * 0.1f + 0.9f);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, ender, 3) {
            @Override
            public boolean stillValid(Player pl) {
                return !be.isRemoved() && be.getLevel() == pl.level() && HangingChestContainer.isOnConveyor(be, box)
                        && pl.distanceToSqr(box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(be.getBlockPos())) <= 100;
            }

            @Override
            public void removed(Player pl) {
                super.removed(pl);
                level.playSound(null, at.x, at.y, at.z, kind.closeSound, SoundSource.BLOCKS, 0.5f,
                        level.random.nextFloat() * 0.1f + 0.9f);
            }
        }, Component.translatable("container.enderchest")));
    }
}
