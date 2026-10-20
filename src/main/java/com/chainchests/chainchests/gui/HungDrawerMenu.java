package com.chainchests.chainchests.gui;

import javax.annotation.Nullable;

import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.jaquadro.minecraft.storagedrawers.block.BlockCompDrawers;
import com.jaquadro.minecraft.storagedrawers.block.BlockDrawers;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.capabilities.DrawerItemHandler;
import com.jaquadro.minecraft.storagedrawers.inventory.ContainerDrawers;
import com.jaquadro.minecraft.storagedrawers.inventory.SlotDrawer;
import com.jaquadro.minecraft.storagedrawers.item.ItemUpgrade;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class HungDrawerMenu extends ContainerDrawers {
    private static final ThreadLocal<int[][]> LAYOUT = new ThreadLocal<>();

    private final ResourceLocation texture;
    @Nullable
    private final ChainConveyorBlockEntity conveyor;
    @Nullable
    private final ChainConveyorPackage box;
    private final Runnable onChanged;
    private final Runnable onClosed;
    private final BlockEntityDrawers tile;
    private DrawerItemHandler handler;

    private HungDrawerMenu(MenuType<?> type, int id, Inventory inv, BlockEntityDrawers tile, ResourceLocation texture,
            @Nullable ChainConveyorBlockEntity conveyor, @Nullable ChainConveyorPackage box, Runnable onChanged,
            Runnable onClosed) {
        super(type, id, inv, tile);
        this.texture = texture;
        this.conveyor = conveyor;
        this.box = box;
        this.onChanged = onChanged;
        this.onClosed = onClosed;
        this.tile = tile;
    }

    public static HungDrawerMenu create(MenuType<?> type, int id, Inventory inv, BlockEntityDrawers tile,
            @Nullable ChainConveyorBlockEntity conveyor, @Nullable ChainConveyorPackage box, Runnable onChanged,
            Runnable onClosed) {
        Block block = tile.getBlockState().getBlock();
        int count = block instanceof BlockDrawers d ? d.getDrawerCount() : 1;
        boolean comp = block instanceof BlockCompDrawers;
        int[][] slots;
        String tex;
        if (comp) {
            slots = count == 2 ? new int[][] { { 80, 23 }, { 67, 49 } } : new int[][] { { 80, 23 }, { 67, 49 }, { 93, 49 } };
            tex = count == 2 ? "drawers_comp_2.png" : "drawers_comp.png";
        } else if (count == 1) {
            slots = new int[][] { { 80, 36 } };
            tex = "drawers_1.png";
        } else if (count == 2) {
            slots = new int[][] { { 80, 23 }, { 80, 49 } };
            tex = "drawers_2.png";
        } else {
            slots = new int[][] { { 67, 23 }, { 93, 23 }, { 67, 49 }, { 93, 49 } };
            tex = "drawers_4.png";
        }
        LAYOUT.set(slots);
        try {
            return new HungDrawerMenu(type, id, inv, tile,
                    ResourceLocation.fromNamespaceAndPath("storagedrawers", "textures/gui/" + tex), conveyor, box,
                    onChanged, onClosed);
        } finally {
            LAYOUT.remove();
        }
    }

    public static BlockEntityDrawers tileFromStack(HolderLookup.Provider registries, ItemStack stack) {
        Block block = ((BlockItem) stack.getItem()).getBlock();
        BlockEntity entity = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        if (!(entity instanceof BlockEntityDrawers tile))
            throw new IllegalStateException("not a drawer: " + stack);
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null)
            tile.readPortable(registries, data.copyTag());
        return tile;
    }

    public static ContainerDrawers fromBuffer(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
        BlockEntityDrawers tile = tileFromStack(inv.player.level().registryAccess(), stack);
        return create(GuiMenus.DRAWERS.get(), id, inv, tile, null, null, () -> {}, () -> {});
    }

    public ResourceLocation texture() {
        return texture;
    }

    @Override
    protected int getStorageSlotX(int slot) {
        return LAYOUT.get()[slot][0];
    }

    @Override
    protected int getStorageSlotY(int slot) {
        return LAYOUT.get()[slot][1];
    }

    private DrawerItemHandler handler() {
        if (handler == null)
            handler = new DrawerItemHandler(tile.getGroup());
        return handler;
    }

    @Override
    public void clicked(int slotId, int button, ClickType click, Player player) {
        boolean handled = false;
        if (slotId >= 0 && slotId < slots.size() && (click == ClickType.PICKUP || click == ClickType.QUICK_MOVE)) {
            var slot = slots.get(slotId);
            if (slot instanceof SlotDrawer sd) {
                handled = true;
                int idx = sd.getContainerSlot() + 1;
                if (click == ClickType.PICKUP) {
                    ItemStack carried = getCarried();
                    if (!carried.isEmpty()) {
                        ItemStack rest = handler().insertItem(idx, carried.copy(), false);
                        setCarried(rest);
                    } else {
                        ItemStack in = handler().getStackInSlot(idx);
                        if (!in.isEmpty()) {
                            int n = Math.min(in.getMaxStackSize(), button == 1 ? (in.getCount() + 1) / 2 : in.getCount());
                            setCarried(handler().extractItem(idx, n, false));
                        }
                    }
                } else {
                    ItemStack in = handler().getStackInSlot(idx);
                    if (!in.isEmpty()) {
                        ItemStack out = handler().extractItem(idx, Math.min(in.getMaxStackSize(), in.getCount()), false);
                        int start = getStorageSlots().size() + getUpgradeSlots().size();
                        moveItemStackTo(out, start, slots.size(), true);
                        if (!out.isEmpty())
                            handler().insertItem(idx, out, false);
                    }
                }
            } else if (click == ClickType.QUICK_MOVE && slot.container instanceof Inventory && slot.hasItem()
                    && !(slot.getItem().getItem() instanceof ItemUpgrade)) {
                ItemStack src = slot.getItem();
                ItemStack rest = handler().insertItem(0, src.copy(), false);
                if (rest.getCount() != src.getCount()) {
                    slot.set(rest.isEmpty() ? ItemStack.EMPTY : rest);
                    handled = true;
                }
            }
        }
        if (!handled)
            super.clicked(slotId, button, click, player);
        else
            broadcastChanges();
        if (!player.level().isClientSide())
            onChanged.run();
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
        if (!pl.level().isClientSide()) {
            onChanged.run();
            onClosed.run();
        }
    }
}
