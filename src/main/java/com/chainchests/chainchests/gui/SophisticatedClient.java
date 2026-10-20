package com.chainchests.chainchests.gui;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

final class SophisticatedClient {
    private SophisticatedClient() {}

    @SuppressWarnings("unchecked")
    static void register(RegisterMenuScreensEvent event) {
        event.register((MenuType<HungStorageMenu>) GuiMenus.SOPHISTICATED.get(), HungStorageScreen::new);
    }
}
