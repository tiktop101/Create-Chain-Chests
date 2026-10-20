package com.chainchests.chainchests.gui;

import java.util.Optional;
import java.util.UUID;

import com.chainchests.chainchests.ChainChests;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SophisticatedMenuProvider;
import net.p3pp3rf1y.sophisticatedstorage.block.ItemContentsStorage;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;

public final class SophisticatedGui {
    private SophisticatedGui() {}

    public static MenuType<HungStorageMenu> createType() {
        return IMenuTypeExtension.create(HungStorageMenu::fromBuffer);
    }

    public static boolean open(ServerPlayer player, ChainConveyorBlockEntity be, ChainConveyorPackage box) {
        try {
            StackStorageWrapper wrapper = StackStorageWrapper.fromStack(player.level().registryAccess(), box.item);
            wrapper.ensureContentsUuid();
            Optional<UUID> id = wrapper.getContentsUuid();
            if (id.isEmpty())
                return false;
            UUID uuid = id.get();
            CompoundTag contents = ItemContentsStorage.get().getOrCreateStorageContents(uuid).copy();
            ItemStack synced = box.item.copy();

            Level level = player.level();
            Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(be.getBlockPos());
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.CHEST_OPEN, SoundSource.BLOCKS,
                    0.5f, level.random.nextFloat() * 0.1f + 0.9f);

            @SuppressWarnings("unchecked")
            MenuType<HungStorageMenu> type = (MenuType<HungStorageMenu>) GuiMenus.SOPHISTICATED.get();
            var opened = player.openMenu(new SophisticatedMenuProvider(
                    (windowId, inv, p) -> new HungStorageMenu(type, windowId, p, wrapper, be, box, () -> {
                        be.setChanged();
                        be.notifyUpdate();
                        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                                SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
                    }), box.item.getHoverName()),
                    (RegistryFriendlyByteBuf buf) -> {
                        ItemStack.STREAM_CODEC.encode(buf, synced);
                        buf.writeUUID(uuid);
                        buf.writeNbt(contents);
                    });
            return opened.isPresent();
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not open Sophisticated Storage menu on chain, using fallback", t);
            return false;
        }
    }
}
