package com.chainchests.chainchests.gui;

import com.chainchests.chainchests.ChainChests;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GuiMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
            ChainChests.MOD_ID);

    public static DeferredHolder<MenuType<?>, ? extends MenuType<?>> SOPHISTICATED;
    public static DeferredHolder<MenuType<?>, ? extends MenuType<?>> DRAWERS;

    private GuiMenus() {}

    public static boolean sophisticatedLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded("sophisticatedstorage") && list.isLoaded("sophisticatedcore");
    }

    public static boolean drawersLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded("storagedrawers");
    }

    public static void register(IEventBus modBus) {
        if (sophisticatedLoaded())
            SOPHISTICATED = MENUS.register("hung_sophisticated", SophisticatedGui::createType);
        if (drawersLoaded())
            DRAWERS = MENUS.register("hung_drawers", DrawerGui::createType);
        MENUS.register(modBus);
    }
}
