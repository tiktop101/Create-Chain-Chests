package com.chainchests.chainchests.events;

import com.chainchests.chainchests.ChainChests;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = ChainChests.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ChainChestInteraction {
    private ChainChestInteraction() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide())
            return;

        ItemStack held = event.getItemStack();
        if (!(held.is(Items.CHEST) || held.is(Items.TRAPPED_CHEST)))
            return;

        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof ChainConveyorBlockEntity conveyor))
            return;

        ItemStack carriedChest = held.copyWithCount(1);

        conveyor.addLoopingPackage(new ChainConveyorPackage(0, carriedChest));
        if (!event.getEntity().getAbilities().instabuild)
            held.shrink(1);
        event.setCanceled(true);
    }
}
