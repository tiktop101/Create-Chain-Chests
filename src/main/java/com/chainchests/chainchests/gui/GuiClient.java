package com.chainchests.chainchests.gui;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class GuiClient {
    private GuiClient() {}

    public static void register(RegisterMenuScreensEvent event) {
        if (GuiMenus.sophisticatedLoaded() && GuiMenus.SOPHISTICATED != null)
            SophisticatedClient.register(event);
        if (GuiMenus.drawersLoaded() && GuiMenus.DRAWERS != null)
            DrawerClient.register(event);
    }
}
