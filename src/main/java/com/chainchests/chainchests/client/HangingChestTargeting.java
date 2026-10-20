package com.chainchests.chainchests.client;

import java.util.Map;

import com.chainchests.chainchests.ChainChests;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.foundation.utility.RaycastHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HangingChestTargeting {
    private HangingChestTargeting() {}

    public record Hit(BlockPos conveyor, int netId, double distSqr, Vec3 point, Vec3 center, Vec3 target,
            boolean drawer, float yaw) {}

    public static Hit find(Minecraft mc) {
        if (mc.level == null || mc.player == null)
            return null;

        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = RaycastHelper.getTraceTarget(mc.player, range, from);

        double blockDist = Double.MAX_VALUE;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK
                && !(mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof ChainConveyorBlock))
            blockDist = bhr.getLocation().distanceToSqr(from);

        Hit best = null;
        for (Map.Entry<Integer, ChainConveyorPackage.ChainConveyorPackagePhysicsData> e : ChainConveyorPackage.physicsDataCache
                .get(mc.level).asMap().entrySet()) {
            var data = e.getValue();
            if (data == null || data.targetPos == null || data.beReference == null)
                continue;
            ChainConveyorBlockEntity be = data.beReference.get();
            if (be == null || be.isRemoved())
                continue;
            ChainConveyorPackage box = ChainChests.findPackage(be, e.getKey());
            if (box == null || !ChainChests.isHangable(box.item))
                continue;

            Vec3 t = data.targetPos;
            AABB bounds = new AABB(t.x - 0.5, t.y - 1.0, t.z - 0.5, t.x + 0.5, t.y + 0.6, t.z + 0.5);
            var clip = bounds.clip(from, to);
            if (clip.isEmpty())
                continue;
            double dist = clip.get().distanceToSqr(from);
            if (dist > blockDist)
                continue;
            if (best == null || dist < best.distSqr())
                best = new Hit(be.getBlockPos(), e.getKey(), dist, clip.get(), data.pos != null ? data.pos : t, t,
                        com.chainchests.chainchests.HangableKind.of(box.item) == com.chainchests.chainchests.HangableKind.DRAWER,
                        data.yaw);
        }
        return best;
    }
}
