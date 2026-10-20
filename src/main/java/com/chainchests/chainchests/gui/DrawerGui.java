package com.chainchests.chainchests.gui;

import com.chainchests.chainchests.ChainChests;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.inventory.ContainerDrawers;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public final class DrawerGui {
    private DrawerGui() {}

    public static MenuType<ContainerDrawers> createType() {
        return IMenuTypeExtension.create(HungDrawerMenu::fromBuffer);
    }

    public static boolean open(ServerPlayer player, BlockEntityDrawers tile, ChainConveyorBlockEntity be,
            ChainConveyorPackage box, Runnable changed, Runnable closed) {
        try {
            changed.run();
            ItemStack synced = box.item.copy();
            Level level = player.level();
            Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(be.getBlockPos());
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.5f,
                    level.random.nextFloat() * 0.1f + 0.9f);

            @SuppressWarnings("unchecked")
            MenuType<ContainerDrawers> type = (MenuType<ContainerDrawers>) GuiMenus.DRAWERS.get();
            var opened = player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> HungDrawerMenu.create(type, id, inv, tile, be, box, changed, () -> {
                        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS,
                                0.5f, level.random.nextFloat() * 0.1f + 0.9f);
                        closed.run();
                    }), box.item.getHoverName()),
                    (RegistryFriendlyByteBuf buf) -> ItemStack.STREAM_CODEC.encode(buf, synced));
            return opened.isPresent();
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not open Storage Drawers menu on chain, using fallback", t);
            return false;
        }
    }
}
