package com.chainchests.chainchests.inventory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.chainchests.chainchests.HangableKind;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HangingChestContainer extends SimpleContainer {
    public static final int SIZE = 27;

    private static final Map<ChainConveyorPackage, HangingChestContainer> OPEN = new IdentityHashMap<>();

    private final ChainConveyorBlockEntity be;
    private final ChainConveyorPackage box;
    private int openers;
    private boolean loading;

    private HangingChestContainer(ChainConveyorBlockEntity be, ChainConveyorPackage box) {
        super(SIZE);
        this.be = be;
        this.box = box;

        NonNullList<ItemStack> stored = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        box.item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(stored);
        loading = true;
        for (int i = 0; i < SIZE; i++)
            super.setItem(i, stored.get(i));
        loading = false;
    }

    public static HangingChestContainer forPackage(ChainConveyorBlockEntity be, ChainConveyorPackage box) {
        return OPEN.computeIfAbsent(box, b -> new HangingChestContainer(be, b));
    }

    public static void discard(ChainConveyorPackage box) {
        HangingChestContainer open = OPEN.remove(box);
        if (open == null)
            return;
        open.loading = true;
        open.clearContent();
        open.loading = false;
    }

    public void releaseIfUnused() {
        if (openers <= 0)
            OPEN.remove(box, this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (loading)
            return;
        writeBack();
        be.setChanged();
    }

    private void writeBack() {
        List<ItemStack> all = new ArrayList<>(SIZE);
        boolean empty = true;
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = getItem(i);
            all.add(s);
            if (!s.isEmpty())
                empty = false;
        }

        if (empty)
            box.item.remove(DataComponents.CONTAINER);
        else
            box.item.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(all));
    }

    @Override
    public boolean stillValid(Player player) {
        if (be.isRemoved() || be.getLevel() != player.level() || !stillOnConveyor())
            return false;
        Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(be.getBlockPos());
        return player.distanceToSqr(at) <= 10 * 10;
    }

    private boolean stillOnConveyor() {
        return isOnConveyor(be, box);
    }

    public static boolean isOnConveyor(ChainConveyorBlockEntity be, ChainConveyorPackage box) {
        for (ChainConveyorPackage p : be.getLoopingPackages())
            if (p == box)
                return true;
        for (List<ChainConveyorPackage> list : be.getTravellingPackages().values())
            for (ChainConveyorPackage p : list)
                if (p == box)
                    return true;
        return false;
    }

    public static void forget(ChainConveyorPackage box) {
        OPEN.remove(box);
    }

    @Override
    public void startOpen(Player player) {
        openers++;
        if (openers == 1)
            sound(true);
    }

    @Override
    public void stopOpen(Player player) {
        openers--;
        if (openers > 0)
            return;
        openers = 0;
        OPEN.remove(box, this);
        if (stillOnConveyor()) {
            writeBack();
            be.notifyUpdate();
        }
        sound(false);
    }

    private void sound(boolean open) {
        Level level = be.getLevel();
        Vec3 at = box.worldPosition;
        HangableKind kind = HangableKind.of(box.item);
        if (level == null || at == null || kind == null)
            return;
        level.playSound(null, at.x, at.y, at.z, open ? kind.openSound : kind.closeSound,
                SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
    }
}
