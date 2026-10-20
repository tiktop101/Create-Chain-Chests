package com.chainchests.chainchests;

import java.util.List;

import com.chainchests.chainchests.net.OpenChestPayload;
import com.chainchests.chainchests.net.PickUpChestPayload;
import com.chainchests.chainchests.net.PlaceChestPayload;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(ChainChests.MOD_ID)
public class ChainChests {
    public static final String MOD_ID = "chainchests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChainChests(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event ->
                event.registrar("1")
                        .playToServer(PlaceChestPayload.TYPE, PlaceChestPayload.STREAM_CODEC,
                                PlaceChestPayload::handle)
                        .playToServer(OpenChestPayload.TYPE, OpenChestPayload.STREAM_CODEC,
                                OpenChestPayload::handle)
                        .playToServer(com.chainchests.chainchests.net.DrawerClickPayload.TYPE,
                                com.chainchests.chainchests.net.DrawerClickPayload.STREAM_CODEC,
                                com.chainchests.chainchests.net.DrawerClickPayload::handle)
                        .playToServer(PickUpChestPayload.TYPE, PickUpChestPayload.STREAM_CODEC,
                                PickUpChestPayload::handle));

        com.chainchests.chainchests.gui.GuiMenus.register(modBus);

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class,
                    com.chainchests.chainchests.gui.GuiClient::register);
            com.chainchests.chainchests.client.ChestModels.register();
            modBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class,
                    e -> e.enqueueWork(com.chainchests.chainchests.client.ChestModels::registerModded));
            modBus.addListener(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted.class,
                    e -> com.chainchests.chainchests.client.HangingVariants.onReload());
        }

        LOGGER.info("Chain Chests loaded");
    }

    public static boolean isHangable(ItemStack stack) {
        return HangableKind.of(stack) != null;
    }

    public static ChainConveyorPackage findPackage(ChainConveyorBlockEntity be, int netId) {
        for (ChainConveyorPackage p : be.getLoopingPackages())
            if (p.netId == netId)
                return p;
        for (List<ChainConveyorPackage> list : be.getTravellingPackages().values())
            for (ChainConveyorPackage p : list)
                if (p.netId == netId)
                    return p;
        return null;
    }
}
