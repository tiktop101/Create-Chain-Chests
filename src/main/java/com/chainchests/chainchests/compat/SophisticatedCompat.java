package com.chainchests.chainchests.compat;

import java.lang.reflect.Method;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class SophisticatedCompat {
    private static final int MAX_SLOTS = 54;

    private SophisticatedCompat() {}

    public static void open(ServerPlayer player, ChainConveyorBlockEntity be, ChainConveyorPackage box) {
        if (com.chainchests.chainchests.gui.GuiMenus.sophisticatedLoaded()
                && com.chainchests.chainchests.gui.GuiMenus.SOPHISTICATED != null
                && com.chainchests.chainchests.gui.SophisticatedGui.open(player, be, box))
            return;
        openFallback(player, be, box);
    }

    private static void openFallback(ServerPlayer player, ChainConveyorBlockEntity be, ChainConveyorPackage box) {
        IItemHandler handler;
        try {
            Class<?> wrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper");
            Method from = wrapperClass.getMethod("fromStack", net.minecraft.core.HolderLookup.Provider.class,
                    ItemStack.class);
            Object wrapper = from.invoke(null, player.level().registryAccess(), box.item);
            wrapperClass.getMethod("ensureContentsUuid").invoke(wrapper);
            handler = (IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not open Sophisticated Storage item on chain", t);
            return;
        }
        if (handler == null || handler.getSlots() <= 0)
            return;

        int rows = Math.min(6, (Math.min(handler.getSlots(), MAX_SLOTS) + 8) / 9);
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        boolean paged = handler.getSlots() > MAX_SLOTS;
        HandlerContainer view = new HandlerContainer(handler, rows * 9, be, paged);
        Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(be.getBlockPos());
        var level = be.getLevel();
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f,
                level.random.nextFloat() * 0.1f + 0.9f);

        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(type, id, inv, view, rows) {
            @Override
            public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType click, Player pl) {
                if (view.paged && slotId >= HandlerContainer.PAGE_SLOTS && slotId < rows * 9) {
                    if (click == net.minecraft.world.inventory.ClickType.PICKUP
                            || click == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                        if (slotId == HandlerContainer.PREV_SLOT)
                            view.turnPage(-1);
                        else if (slotId == HandlerContainer.NEXT_SLOT)
                            view.turnPage(1);
                        broadcastFullState();
                    }
                    return;
                }
                super.clicked(slotId, button, click, pl);
            }

            @Override
            public boolean stillValid(Player pl) {
                return !be.isRemoved() && be.getLevel() == pl.level()
                        && HangingChestContainer.isOnConveyor(be, box)
                        && pl.distanceToSqr(box.worldPosition != null ? box.worldPosition
                                : Vec3.atCenterOf(be.getBlockPos())) <= 100;
            }

            @Override
            public void removed(Player pl) {
                super.removed(pl);
                be.setChanged();
                be.notifyUpdate();
                level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                        SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
            }
        }, box.item.getHoverName()));
    }

    public static java.util.List<ItemStack> drainLoose(net.minecraft.core.HolderLookup.Provider provider,
            ItemStack stack) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        try {
            Class<?> wrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper");
            Method from = wrapperClass.getMethod("fromStack", net.minecraft.core.HolderLookup.Provider.class,
                    ItemStack.class);
            Object wrapper = from.invoke(null, provider, stack);
            Object h = wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
            if (!(h instanceof IItemHandler handler))
                return out;
            for (int i = 0; i < handler.getSlots(); i++) {
                for (int guard = 0; guard < 1000; guard++) {
                    ItemStack in = handler.getStackInSlot(i);
                    if (in.isEmpty())
                        break;
                    ItemStack taken = handler.extractItem(i, in.getCount(), false);
                    if (taken.isEmpty())
                        break;
                    out.add(taken);
                }
            }
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not empty Sophisticated Storage item", t);
        }
        return out;
    }

    private static final class HandlerContainer extends SimpleContainer {
        static final int PAGE_SLOTS = 45;
        static final int PREV_SLOT = 45;
        static final int INFO_SLOT = 49;
        static final int NEXT_SLOT = 53;

        private final IItemHandler handler;
        private final int size;
        private final ChainConveyorBlockEntity be;
        final boolean paged;
        private int page;

        HandlerContainer(IItemHandler handler, int size, ChainConveyorBlockEntity be, boolean paged) {
            super(size);
            this.handler = handler;
            this.size = size;
            this.be = be;
            this.paged = paged;
        }

        private int pages() {
            return Math.max(1, (handler.getSlots() + PAGE_SLOTS - 1) / PAGE_SLOTS);
        }

        void turnPage(int delta) {
            page = Math.max(0, Math.min(pages() - 1, page + delta));
        }

        private int real(int i) {
            if (i < 0 || i >= size)
                return -1;
            int r;
            if (paged) {
                if (i >= PAGE_SLOTS)
                    return -1;
                r = page * PAGE_SLOTS + i;
            } else {
                r = i;
            }
            return r < handler.getSlots() ? r : -1;
        }

        private ItemStack control(int i) {
            ItemStack s;
            if (i == PREV_SLOT)
                s = new ItemStack(Items.ARROW);
            else if (i == NEXT_SLOT)
                s = new ItemStack(Items.ARROW);
            else if (i == INFO_SLOT)
                s = new ItemStack(Items.PAPER);
            else
                s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            Component name = i == PREV_SLOT ? Component.literal("Previous page")
                    : i == NEXT_SLOT ? Component.literal("Next page")
                    : i == INFO_SLOT ? Component.literal("Page " + (page + 1) + " / " + pages())
                    : Component.literal(" ");
            s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
            return s;
        }

        @Override
        public ItemStack getItem(int i) {
            if (paged && i >= PAGE_SLOTS && i < size)
                return control(i);
            int r = real(i);
            return r < 0 ? ItemStack.EMPTY : handler.getStackInSlot(r);
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            int r = real(i);
            if (r < 0)
                return;
            if (handler instanceof IItemHandlerModifiable mod)
                mod.setStackInSlot(r, stack);
            setChanged();
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            int r = real(i);
            if (r < 0 || count <= 0)
                return ItemStack.EMPTY;
            ItemStack out = handler.extractItem(r, count, false);
            setChanged();
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            int r = real(i);
            if (r < 0)
                return ItemStack.EMPTY;
            ItemStack cur = handler.getStackInSlot(r);
            return handler.extractItem(r, cur.getCount(), false);
        }

        @Override
        public boolean canPlaceItem(int i, ItemStack stack) {
            int r = real(i);
            return r >= 0 && handler.isItemValid(r, stack);
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < handler.getSlots(); i++)
                if (!handler.getStackInSlot(i).isEmpty())
                    return false;
            return true;
        }

        @Override
        public void clearContent() {}

        @Override
        public void setChanged() {
            be.setChanged();
        }
    }
}
