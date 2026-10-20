package com.chainchests.chainchests.gui;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.ISyncedContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.settings.itemdisplay.ItemDisplaySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NoopStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.block.ItemContentsStorage;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;

public class HungStorageMenu extends StorageContainerMenuBase<IStorageWrapper> implements ISyncedContainer {
    @Nullable
    private final ChainConveyorBlockEntity conveyor;
    @Nullable
    private final ChainConveyorPackage box;
    private final Runnable onClosed;

    public HungStorageMenu(MenuType<?> type, int containerId, Player player, IStorageWrapper wrapper,
            @Nullable ChainConveyorBlockEntity conveyor, @Nullable ChainConveyorPackage box, Runnable onClosed) {
        super(type, containerId, player, wrapper, NoopStorageWrapper.INSTANCE, -1, false);
        this.conveyor = conveyor;
        this.box = box;
        this.onClosed = onClosed;
    }

    public static HungStorageMenu fromBuffer(int windowId, Inventory inv, RegistryFriendlyByteBuf buf) {
        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
        UUID uuid = buf.readUUID();
        CompoundTag contents = buf.readNbt();
        if (contents != null)
            ItemContentsStorage.get().setStorageContents(uuid, contents);
        StackStorageWrapper wrapper = StackStorageWrapper.fromStack(inv.player.level().registryAccess(), stack);
        return new HungStorageMenu(GuiMenus.SOPHISTICATED.get(), windowId, inv.player, wrapper, null, null, () -> {});
    }

    @Override
    public Optional<BlockPos> getBlockPosition() {
        return Optional.empty();
    }

    @Override
    public Optional<Entity> getEntity() {
        return Optional.empty();
    }

    @Override
    protected StorageContainerMenuBase<IStorageWrapper>.StorageUpgradeSlot instantiateUpgradeSlot(
            UpgradeHandler upgradeHandler, int slotIndex) {
        return new StorageUpgradeSlot(upgradeHandler, slotIndex) {
            @Override
            protected void onUpgradeChanged() {
                if (player.level().isClientSide())
                    return;
                storageWrapper.getSettingsHandler().getTypeCategory(ItemDisplaySettingsCategory.class).itemsChanged();
            }
        };
    }

    @Override
    public void openSettings() {
    }

    @Override
    protected boolean storageItemHasChanged() {
        return false;
    }

    @Override
    public boolean detectSettingsChangeAndReload() {
        return false;
    }

    @Override
    public boolean stillValid(Player pl) {
        if (conveyor == null || box == null)
            return true;
        Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(conveyor.getBlockPos());
        return !conveyor.isRemoved() && conveyor.getLevel() == pl.level()
                && HangingChestContainer.isOnConveyor(conveyor, box) && pl.distanceToSqr(at) <= 100;
    }

    @Override
    public void removed(Player pl) {
        super.removed(pl);
        if (!pl.level().isClientSide())
            onClosed.run();
    }
}
