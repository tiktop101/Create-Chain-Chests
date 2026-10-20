package com.chainchests.chainchests.client;

import java.util.List;
import java.util.Map;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.net.OpenChestPayload;
import com.chainchests.chainchests.net.PickUpChestPayload;
import com.chainchests.chainchests.net.PlaceChestPayload;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.simibubi.create.foundation.utility.RaycastHelper;

import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ChainChests.MOD_ID, value = Dist.CLIENT)
public final class ChestPlacementHandler {
    private static BlockPos selectedLift;
    private static BlockPos selectedConnection;
    private static float selectedChainPosition;

    private ChestPlacementHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        selectedLift = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null)
            return;
        if (!mc.player.isShiftKeyDown() || !ChainChests.isHangable(mc.player.getMainHandItem()))
            return;

        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = RaycastHelper.getTraceTarget(mc.player, range, from);
        HitResult hit = mc.hitResult;
        double bestDist = hit != null ? hit.getLocation().distanceToSqr(from) : Double.MAX_VALUE;

        BlockPos bestLift = null;
        BlockPos bestConnection = BlockPos.ZERO;
        float bestPosition = 0;
        ChainConveyorShape bestShape = null;

        for (Map.Entry<BlockPos, List<ChainConveyorShape>> entry : ChainConveyorInteractionHandler.loadedChains
                .get(mc.level).asMap().entrySet()) {
            Vec3 liftVec = Vec3.atLowerCornerOf(entry.getKey());
            for (ChainConveyorShape shape : entry.getValue()) {
                Vec3 intersect = shape.intersect(from.subtract(liftVec), to.subtract(liftVec));
                if (intersect == null)
                    continue;
                double dist = intersect.add(liftVec).distanceToSqr(from);
                if (dist > bestDist)
                    continue;
                bestDist = dist;
                bestLift = entry.getKey();
                bestShape = shape;
                bestPosition = shape.getChainPosition(intersect);
                bestConnection = connectionOf(shape);
            }
        }

        if (bestLift == null || connectionFieldFailed)
            return;

        HangingChestTargeting.Hit chest = HangingChestTargeting.find(mc);
        if (chest != null && chest.distSqr() <= bestDist)
            return;

        selectedLift = bestLift;
        selectedConnection = bestConnection;
        selectedChainPosition = bestPosition;

        Vec3 point = bestShape.getVec(bestLift, bestPosition);
        Outliner.getInstance()
                .chaseAABB("ChainChestPoint", new AABB(point, point))
                .colored(Color.WHITE)
                .lineWidth(1 / 6f)
                .disableLineNormals();
    }

    private static java.lang.reflect.Field connectionField;
    private static boolean connectionFieldFailed;

    private static BlockPos connectionOf(ChainConveyorShape shape) {
        if (!(shape instanceof ChainConveyorShape.ChainConveyorOBB) || connectionFieldFailed)
            return BlockPos.ZERO;
        try {
            if (connectionField == null) {
                connectionField = ChainConveyorShape.ChainConveyorOBB.class.getDeclaredField("connection");
                connectionField.setAccessible(true);
            }
            return (BlockPos) connectionField.get(shape);
        } catch (ReflectiveOperationException | RuntimeException e) {
            connectionFieldFailed = true;
            ChainChests.LOGGER.error("Could not read ChainConveyorOBB.connection; chest placement on chains disabled", e);
            return BlockPos.ZERO;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem())
            return;

        if (selectedLift != null) {
            PacketDistributor.sendToServer(new PlaceChestPayload(selectedLift, selectedConnection, selectedChainPosition));
            event.setSwingHand(true);
            event.setCanceled(true);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        HangingChestTargeting.Hit hit = HangingChestTargeting.find(mc);
        if (hit == null)
            return;
        boolean shift = mc.player.isShiftKeyDown();
        if (hit.drawer()) {
            if (shift && mc.player.getMainHandItem().isEmpty())
                PacketDistributor.sendToServer(new OpenChestPayload(hit.conveyor(), hit.netId()));
            else
                sendDrawerClick(mc, hit, false);
        } else
            PacketDistributor.sendToServer(new OpenChestPayload(hit.conveyor(), hit.netId()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;
        HangingChestTargeting.Hit hit = HangingChestTargeting.find(mc);
        if (hit == null)
            return;
        if (hit.drawer() && hitsFront(mc, hit))
            sendDrawerClick(mc, hit, true);
        else
            PacketDistributor.sendToServer(new PickUpChestPayload(hit.conveyor(), hit.netId()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    private static boolean hitsFront(Minecraft mc, HangingChestTargeting.Hit hit) {
        double yaw = Math.toRadians(hit.yaw());
        Vec3 d = mc.player.getEyePosition().subtract(hit.target());
        double along = Math.sin(yaw) * d.x + Math.cos(yaw) * d.z;
        double across = Math.cos(yaw) * d.x - Math.sin(yaw) * d.z;
        return along > 0 && along >= Math.abs(across);
    }

    private static final double BOX_CENTER_Y = -0.2;
    private static final double BOX_SIZE = 0.875;

    private static void sendDrawerClick(Minecraft mc, HangingChestTargeting.Hit hit, boolean left) {
        double yaw = Math.toRadians(mc.player.getYRot());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double u = ((hit.point().x - hit.center().x) * rx + (hit.point().z - hit.center().z) * rz) / BOX_SIZE;
        double v = (hit.point().y - (hit.target().y + BOX_CENTER_Y - BOX_SIZE / 2)) / BOX_SIZE;
        double x = Math.max(0, Math.min(1, 0.5 - u));
        double y = Math.max(0, Math.min(1, v));
        PacketDistributor.sendToServer(
                new com.chainchests.chainchests.net.DrawerClickPayload(hit.conveyor(), hit.netId(), left, x, y));
    }
}
