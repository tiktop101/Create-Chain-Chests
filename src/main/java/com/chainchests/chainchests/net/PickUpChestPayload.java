package com.chainchests.chainchests.net;

import java.util.List;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.HangableKind;
import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PickUpChestPayload(BlockPos pos, int netId) implements CustomPacketPayload {
    public static final Type<PickUpChestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID, "pick_up_chest"));

    public static final StreamCodec<FriendlyByteBuf, PickUpChestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PickUpChestPayload::pos,
            ByteBufCodecs.VAR_INT, PickUpChestPayload::netId,
            PickUpChestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PickUpChestPayload payload, IPayloadContext ctx) {
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

        boolean removed = be.getLoopingPackages().remove(box);
        if (!removed)
            for (List<ChainConveyorPackage> list : be.getTravellingPackages().values())
                if (list.remove(box)) {
                    removed = true;
                    break;
                }
        if (!removed)
            return;

        ItemStack result = box.item.copy();
        HangableKind kind = HangableKind.of(box.item);
        if (kind != null && kind.dropsContentsOnPickup) {
            NonNullList<ItemStack> contents = NonNullList.withSize(HangingChestContainer.SIZE, ItemStack.EMPTY);
            box.item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents);
            HangingChestContainer.discard(box);
            result.remove(DataComponents.CONTAINER);
            for (ItemStack stack : contents)
                if (!stack.isEmpty())
                    Containers.dropItemStack(level, at.x, at.y, at.z, stack);
        } else {
            com.chainchests.chainchests.ChainChestDrops.dropSophisticatedContents(level, at, result);
            HangingChestContainer.forget(box);
        }
        ItemStack chest = result;

        if (player.getMainHandItem().isEmpty())
            player.setItemInHand(InteractionHand.MAIN_HAND, chest);
        else
            player.getInventory().placeItemBackInInventory(chest);

        be.notifyUpdate();
    }
}
