package com.chainchests.chainchests.gui;

import com.jaquadro.minecraft.storagedrawers.inventory.ContainerDrawers;
import com.jaquadro.minecraft.storagedrawers.inventory.DrawerScreen;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

final class DrawerClient {
    private DrawerClient() {}

    @SuppressWarnings("unchecked")
    static void register(RegisterMenuScreensEvent event) {
        event.<ContainerDrawers, DrawerScreen>register((MenuType<ContainerDrawers>) GuiMenus.DRAWERS.get(),
                (menu, inv, title) -> new DrawerScreen(menu, inv, title, ((HungDrawerMenu) menu).texture()));
    }
}
