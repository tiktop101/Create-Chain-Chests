package com.chainchests.chainchests.compat;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

import com.chainchests.chainchests.ChainChests;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.inventory.DrawerInventoryHelper;
import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class DrawerCompat {
    private static final int HOPPER_SLOTS = 5;

    private static final Map<ChainConveyorPackage, State> OPEN = new IdentityHashMap<>();

    private DrawerCompat() {}

    public static ItemStack applyDropMode(Level level, Vec3 at, ItemStack stack) {
        try {
            ModCommonConfig.DropMode mode = ModCommonConfig.INSTANCE.DRAWERS.storage.dropMode.get();
            if (mode == ModCommonConfig.DropMode.KEEP)
                return stack;

            ItemStack plain = new ItemStack(stack.getItem());
            if (stack.has(DataComponents.CUSTOM_NAME))
                plain.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));

            if (mode == ModCommonConfig.DropMode.DROP) {
                CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                if (data != null) {
                    Block block = ((BlockItem) stack.getItem()).getBlock();
                    BlockEntity entity = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                    if (entity instanceof BlockEntityDrawers drawers) {
                        drawers.readPortable(level.registryAccess(), data.copyTag());
                        BlockPos pos = BlockPos.containing(at);
                        DrawerInventoryHelper.dropUpgradeItems(level, pos, drawers.upgrades());
                        if (!drawers.getDrawerAttributes().isUnlimitedVending())
                            DrawerInventoryHelper.dropInventoryItems(level, pos, drawers.getGroup());
                    }
                }
            }
            return plain;
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not apply drawer drop mode, keeping contents in item", t);
            return stack;
        }
    }

    public static void open(ServerPlayer player, ChainConveyorBlockEntity conveyor, ChainConveyorPackage box) {
        State state;
        try {
            state = OPEN.get(box);
            if (state == null) {
                state = new State(player.level(), conveyor, box);
                OPEN.put(box, state);
            }
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not open hung drawer", t);
            return;
        }

        if (com.chainchests.chainchests.gui.GuiMenus.drawersLoaded()
                && com.chainchests.chainchests.gui.GuiMenus.DRAWERS != null) {
            State real = state;
            real.openers++;
            boolean ok = com.chainchests.chainchests.gui.DrawerGui.open(player, (com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers) real.be,
                    conveyor, box, real::changed, () -> real.release(box));
            if (ok)
                return;
            real.release(box);
        }

        int drawers = Math.min(HOPPER_SLOTS - 1, state.slots() - 1);
        if (drawers <= 0)
            return;

        State st = state;
        st.openers++;
        Level level = player.level();
        Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(conveyor.getBlockPos());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.5f,
                level.random.nextFloat() * 0.1f + 0.9f);

        var opened = player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new DrawerMenu(id, inv, new DrawerContainer(st, drawers), st, conveyor, box, at),
                box.item.getHoverName()));
        if (opened.isEmpty())
            st.release(box);
    }

    public static void interact(ServerPlayer player, ChainConveyorBlockEntity conveyor, ChainConveyorPackage box,
            boolean left, double nx, double ny) {
        State st;
        try {
            st = OPEN.get(box);
            if (st == null) {
                st = new State(player.level(), conveyor, box);
                OPEN.put(box, st);
            }
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not use hung drawer", t);
            return;
        }
        try {
            var block = (com.jaquadro.minecraft.storagedrawers.block.BlockDrawers) ((BlockItem) box.item.getItem())
                    .getBlock();
            var tile = (com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers) st.be;
            var state = block.defaultBlockState().setValue(
                    net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                    net.minecraft.core.Direction.NORTH);
            int slot = block.getFaceSlot(state, new net.minecraft.world.phys.BlockHitResult(new Vec3(nx, ny, 0),
                    net.minecraft.core.Direction.NORTH, BlockPos.ZERO, false));
            ItemStack held = player.getMainHandItem();

            if (left) {
                if (slot >= 0) {
                    var drawer = tile.getGroup().getDrawer(slot);
                    ItemStack out = tile.takeItemsFromSlot(slot,
                            player.isShiftKeyDown() ? drawer.getStoredItemStackSize() : 1, player);
                    if (!out.isEmpty()) {
                        if (!player.getInventory().add(out))
                            player.drop(out, false);
                        else
                            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                                    SoundSource.PLAYERS, 0.2f, 1.6f);
                    }
                }
            } else if (held.getItem() instanceof com.jaquadro.minecraft.storagedrawers.item.ItemUpgrade
                    && !player.isShiftKeyDown()) {
                var upgrades = tile.upgrades();
                if (!upgrades.canAddUpgrade(held)) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.storagedrawers.cannot_add_upgrade"),
                            true);
                } else if (!upgrades.addUpgrade(held)) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.storagedrawers.max_upgrades"),
                            true);
                } else if (!player.isCreative()) {
                    held.shrink(1);
                }
            } else if (slot >= 0) {
                long now = player.level().getGameTime();
                if (now - st.lastClick < 10 && player.getUUID().equals(st.lastClickBy))
                    tile.interactPutCurrentInventoryIntoSlot(slot, player);
                else
                    tile.interactPutCurrentItemIntoSlot(slot, player);
                st.lastClick = now;
                st.lastClickBy = player.getUUID();
            }
            st.changed();
            conveyor.notifyUpdate();
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Hung drawer click failed", t);
        } finally {
            if (st.openers <= 0)
                OPEN.remove(box, st);
        }
    }

    private static final class State {
        final Level level;
        final ChainConveyorBlockEntity conveyor;
        final ChainConveyorPackage box;
        final BlockEntity be;
        final Object handler;
        final Method getSlots, getStack, insert, extract;
        int openers;
        long lastClick = -100;
        java.util.UUID lastClickBy;

        State(Level level, ChainConveyorBlockEntity conveyor, ChainConveyorPackage box) throws Exception {
            this.level = level;
            this.conveyor = conveyor;
            this.box = box;
            HolderLookup.Provider reg = level.registryAccess();
            Block block = ((BlockItem) box.item.getItem()).getBlock();
            BlockEntity entity = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            Class<?> beClass = Class.forName("com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers");
            if (entity == null || !beClass.isInstance(entity))
                throw new IllegalStateException("not a drawer: " + box.item);
            this.be = entity;

            CustomData data = box.item.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data != null)
                beClass.getMethod("readPortable", HolderLookup.Provider.class, CompoundTag.class).invoke(be, reg,
                        data.copyTag());

            Object group = beClass.getMethod("getGroup").invoke(be);
            Class<?> groupIface = Class.forName("com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup");
            Class<?> handlerClass = Class
                    .forName("com.jaquadro.minecraft.storagedrawers.capabilities.DrawerItemHandler");
            this.handler = handlerClass.getConstructor(groupIface).newInstance(group);
            this.getSlots = handlerClass.getMethod("getSlots");
            this.getStack = handlerClass.getMethod("getStackInSlot", int.class);
            this.insert = handlerClass.getMethod("insertItem", int.class, ItemStack.class, boolean.class);
            this.extract = handlerClass.getMethod("extractItem", int.class, int.class, boolean.class);
        }

        int slots() {
            try {
                return (Integer) getSlots.invoke(handler);
            } catch (Throwable t) {
                return 0;
            }
        }

        ItemStack get(int idx) {
            try {
                return (ItemStack) getStack.invoke(handler, idx + 1);
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        }

        ItemStack insert(int idx, ItemStack stack) {
            return insertAt(idx + 1, stack);
        }

        ItemStack insertAnywhere(ItemStack stack) {
            return insertAt(0, stack);
        }

        private ItemStack insertAt(int slot, ItemStack stack) {
            if (stack.isEmpty())
                return stack;
            try {
                ItemStack rem = (ItemStack) insert.invoke(handler, slot, stack.copy(), false);
                if (rem.getCount() != stack.getCount())
                    changed();
                return rem;
            } catch (Throwable t) {
                ChainChests.LOGGER.warn("Hung drawer insert failed", t);
                return stack;
            }
        }

        ItemStack extract(int idx, int amount) {
            ItemStack in = get(idx);
            if (in.isEmpty() || amount <= 0)
                return ItemStack.EMPTY;
            int n = Math.min(Math.min(amount, in.getCount()), in.getMaxStackSize());
            try {
                ItemStack out = (ItemStack) extract.invoke(handler, idx + 1, n, false);
                if (!out.isEmpty())
                    changed();
                return out;
            } catch (Throwable t) {
                ChainChests.LOGGER.warn("Hung drawer extract failed", t);
                return ItemStack.EMPTY;
            }
        }

        boolean onConveyor() {
            return !conveyor.isRemoved() && HangingChestContainer.isOnConveyor(conveyor, box);
        }

        void changed() {
            if (!onConveyor())
                return;
            try {
                CompoundTag saved = be.saveWithId(level.registryAccess());
                box.item.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(saved));
                box.item.set(DataComponents.MAX_STACK_SIZE, 1);
                conveyor.setChanged();
            } catch (Throwable t) {
                ChainChests.LOGGER.warn("Could not save hung drawer", t);
            }
        }

        void release(ChainConveyorPackage key) {
            openers = Math.max(0, openers - 1);
            if (openers > 0)
                return;
            changed();
            if (onConveyor())
                conveyor.notifyUpdate();
            OPEN.remove(key, this);
        }
    }

    private static final class DrawerContainer extends SimpleContainer {
        private final State state;
        private final int drawers;

        DrawerContainer(State state, int drawers) {
            super(HOPPER_SLOTS);
            this.state = state;
            this.drawers = drawers;
        }

        @Override
        public ItemStack getItem(int i) {
            return i >= 0 && i < drawers ? state.get(i) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            return i >= 0 && i < drawers ? state.extract(i, count) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return i >= 0 && i < drawers ? state.extract(i, Integer.MAX_VALUE) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
        }

        @Override
        public boolean canPlaceItem(int i, ItemStack stack) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < drawers; i++)
                if (!state.get(i).isEmpty())
                    return false;
            return true;
        }

        @Override
        public void clearContent() {}

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private static final class DrawerMenu extends HopperMenu {
        private final State state;
        private final ChainConveyorBlockEntity conveyor;
        private final Vec3 at;
        private final ChainConveyorPackage box;
        private final int drawers;
        private boolean released;

        DrawerMenu(int id, Inventory inv, DrawerContainer container, State state, ChainConveyorBlockEntity conveyor,
                ChainConveyorPackage box, Vec3 at) {
            super(id, inv, container);
            this.state = state;
            this.conveyor = conveyor;
            this.box = box;
            this.at = at;
            this.drawers = container.drawers;
        }

        @Override
        public boolean stillValid(Player player) {
            return state.onConveyor() && conveyor.getLevel() == player.level() && player.distanceToSqr(at) <= 100;
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, Player player) {
            if (slotId >= 0 && slotId < HOPPER_SLOTS && type != ClickType.QUICK_CRAFT
                    && type != ClickType.PICKUP_ALL) {
                if (slotId < drawers) {
                    if (type == ClickType.PICKUP)
                        pickup(slotId, button);
                    else if (type == ClickType.QUICK_MOVE)
                        moveOut(slotId);
                }
                broadcastChanges();
                return;
            }
            super.clicked(slotId, button, type, player);
        }

        private void pickup(int idx, int button) {
            ItemStack carried = getCarried();
            if (carried.isEmpty()) {
                ItemStack in = state.get(idx);
                if (in.isEmpty())
                    return;
                int want = Math.min(in.getCount(), in.getMaxStackSize());
                if (button == 1)
                    want = (want + 1) / 2;
                ItemStack out = state.extract(idx, want);
                if (!out.isEmpty())
                    setCarried(out);
            } else {
                int n = button == 1 ? 1 : carried.getCount();
                ItemStack rem = state.insert(idx, carried.copyWithCount(n));
                carried.shrink(n - rem.getCount());
                if (carried.isEmpty())
                    setCarried(ItemStack.EMPTY);
            }
        }

        private void moveOut(int idx) {
            ItemStack out = state.extract(idx, Integer.MAX_VALUE);
            if (out.isEmpty())
                return;
            moveItemStackTo(out, HOPPER_SLOTS, slots.size(), true);
            if (!out.isEmpty())
                state.insert(idx, out);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index < HOPPER_SLOTS || index >= slots.size())
                return ItemStack.EMPTY;
            Slot slot = slots.get(index);
            if (slot == null || !slot.hasItem())
                return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack before = stack.copy();
            ItemStack rem = state.insertAnywhere(stack);
            int moved = stack.getCount() - rem.getCount();
            if (moved <= 0)
                return ItemStack.EMPTY;
            stack.shrink(moved);
            slot.setChanged();
            return before.copyWithCount(moved);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (released)
                return;
            released = true;
            Level level = conveyor.getLevel();
            if (level != null)
                level.playSound(null, at.x, at.y, at.z, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f,
                        level.random.nextFloat() * 0.1f + 0.9f);
            state.release(box);
        }
    }
}
