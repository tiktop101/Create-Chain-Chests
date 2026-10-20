package com.chainchests.chainchests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public enum HangableKind {
    CHEST(true, true, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE),
    TRAPPED_CHEST(true, true, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE),
    BARREL(true, true, SoundEvents.BARREL_OPEN, SoundEvents.BARREL_CLOSE),

    SHULKER_BOX(true, false, SoundEvents.SHULKER_BOX_OPEN, SoundEvents.SHULKER_BOX_CLOSE),

    ENDER_CHEST(false, false, SoundEvents.ENDER_CHEST_OPEN, SoundEvents.ENDER_CHEST_CLOSE),

    SOPHISTICATED(true, false, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE),

    DRAWER(true, false, SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_DOOR_CLOSE);

    public final boolean storesItems;

    public final boolean dropsContentsOnPickup;
    public final SoundEvent openSound;
    public final SoundEvent closeSound;

    HangableKind(boolean storesItems, boolean dropsContentsOnPickup, SoundEvent openSound, SoundEvent closeSound) {
        this.storesItems = storesItems;
        this.dropsContentsOnPickup = dropsContentsOnPickup;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    public static HangableKind of(ItemStack stack) {
        if (stack.isEmpty())
            return null;
        if (stack.is(Items.CHEST))
            return CHEST;
        if (stack.is(Items.TRAPPED_CHEST))
            return TRAPPED_CHEST;
        if (stack.is(Items.BARREL))
            return BARREL;
        if (stack.is(Items.ENDER_CHEST))
            return ENDER_CHEST;
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock)
            return SHULKER_BOX;
        if (stack.getItem() instanceof BlockItem) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (isSophisticated(id))
                return SOPHISTICATED;
            if (isDrawer(id))
                return DRAWER;
        }
        return null;
    }

    public static boolean isSophisticated(ResourceLocation id) {
        if (!id.getNamespace().equals("sophisticatedstorage"))
            return false;
        String p = id.getPath();
        return p.contains("chest") || p.contains("barrel") || p.contains("shulker_box");
    }

    public static boolean isDrawer(ResourceLocation id) {
        return id.getNamespace().equals("storagedrawers") && id.getPath().contains("drawers");
    }
}
