package com.chainchests.chainchests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chainchests.chainchests.inventory.HangingChestContainer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import com.chainchests.chainchests.compat.DrawerCompat;
import com.chainchests.chainchests.compat.SophisticatedCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ChainChestDrops {
    private ChainChestDrops() {}

    public static void dropAll(ChainConveyorBlockEntity be) {
        List<ChainConveyorPackage> boxes = new ArrayList<>();
        for (ChainConveyorPackage p : be.getLoopingPackages())
            if (ChainChests.isHangable(p.item))
                boxes.add(p);
        for (List<ChainConveyorPackage> list : be.getTravellingPackages().values())
            for (ChainConveyorPackage p : list)
                if (ChainChests.isHangable(p.item))
                    boxes.add(p);
        dropBoxes(be, boxes);
    }

    public static void dropOnConnection(ChainConveyorBlockEntity be, BlockPos target) {
        List<ChainConveyorPackage> list = be.getTravellingPackages().get(target.subtract(be.getBlockPos()));
        if (list == null)
            return;
        List<ChainConveyorPackage> boxes = new ArrayList<>();
        for (ChainConveyorPackage p : list)
            if (ChainChests.isHangable(p.item))
                boxes.add(p);
        dropBoxes(be, boxes);
    }

    public static void dropOrphaned(ChainConveyorBlockEntity be) {
        List<ChainConveyorPackage> boxes = new ArrayList<>();
        for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : be.getTravellingPackages().entrySet()) {
            if (be.connections.contains(entry.getKey()))
                continue;
            for (ChainConveyorPackage p : entry.getValue())
                if (ChainChests.isHangable(p.item))
                    boxes.add(p);
        }
        dropBoxes(be, boxes);
    }

    private static void dropBoxes(ChainConveyorBlockEntity be, List<ChainConveyorPackage> boxes) {
        if (boxes.isEmpty())
            return;
        Level level = be.getLevel();
        for (ChainConveyorPackage box : boxes) {
            be.getLoopingPackages().remove(box);
            for (List<ChainConveyorPackage> list : be.getTravellingPackages().values())
                list.remove(box);
        }
        if (level == null || level.isClientSide())
            return;

        for (ChainConveyorPackage box : boxes) {
            Vec3 at = box.worldPosition != null ? box.worldPosition : Vec3.atCenterOf(be.getBlockPos());
            ItemStack result = box.item.copy();
            HangableKind kind = HangableKind.of(box.item);
            if (kind == HangableKind.ENDER_CHEST) {
                HangingChestContainer.forget(box);
            } else if (kind != null && kind.dropsContentsOnPickup) {
                NonNullList<ItemStack> contents = NonNullList.withSize(HangingChestContainer.SIZE, ItemStack.EMPTY);
                box.item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents);
                HangingChestContainer.discard(box);
                result.remove(DataComponents.CONTAINER);
                for (ItemStack stack : contents)
                    if (!stack.isEmpty())
                        Containers.dropItemStack(level, at.x, at.y, at.z, stack);
            } else {
                dropSophisticatedContents(level, at, result);
                if (kind == HangableKind.DRAWER)
                    result = DrawerCompat.applyDropMode(level, at, result);
                HangingChestContainer.forget(box);
            }
            Containers.dropItemStack(level, at.x, at.y, at.z, result);
        }
    }

    public static void dropSophisticatedContents(Level level, Vec3 at, ItemStack stack) {
        if (HangableKind.of(stack) != HangableKind.SOPHISTICATED)
            return;
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("shulker_box"))
            return;
        for (ItemStack s : SophisticatedCompat.drainLoose(level.registryAccess(), stack))
            while (!s.isEmpty())
                Containers.dropItemStack(level, at.x, at.y, at.z, s.split(Math.min(s.getCount(), s.getMaxStackSize())));
    }
}
