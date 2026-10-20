package com.chainchests.chainchests.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.StorageTranslationHelper;

public class HungStorageScreen extends StorageScreenBase<HungStorageMenu> {
    public HungStorageScreen(HungStorageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected String getStorageSettingsTabTooltip() {
        return StorageTranslationHelper.INSTANCE.translGui("settings.tooltip");
    }
}
