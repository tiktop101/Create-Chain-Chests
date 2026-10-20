package com.chainchests.chainchests.client;

import java.util.Map;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.HangableKind;
import com.chainchests.chainchests.gui.GuiMenus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = ChainChests.MOD_ID, value = Dist.CLIENT)
public final class HungDrawerLabels {
    private static boolean failed;

    private HungDrawerLabels() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        if (!GuiMenus.drawersLoaded())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;

        try {
            for (Map.Entry<Integer, ChainConveyorPackage.ChainConveyorPackagePhysicsData> e : ChainConveyorPackage.physicsDataCache
                    .get(mc.level).asMap().entrySet()) {
                var data = e.getValue();
                if (data == null || data.beReference == null || data.pos == null || data.prevPos == null
                        || data.targetPos == null || data.prevTargetPos == null)
                    continue;
                ChainConveyorBlockEntity be = data.beReference.get();
                if (be == null || be.isRemoved())
                    continue;
                ChainConveyorPackage box = ChainChests.findPackage(be, e.getKey());
                if (box == null || HangableKind.of(box.item) != HangableKind.DRAWER)
                    continue;
                DrawerLabelRenderer.render(mc, box.item, data, partial, ps, buffers, cam);
                drew = true;
            }
        } catch (Throwable t) {
            failed = true;
            ChainChests.LOGGER.warn("Hung drawer item rendering failed and was turned off", t);
        }
        if (drew)
            buffers.endBatch();
    }
}
