package com.chainchests.chainchests.client;

import java.util.HashMap;
import java.util.Map;

import com.chainchests.chainchests.ChainChests;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ChainChests.MOD_ID, value = Dist.CLIENT)
public final class ChestSwingHandler {
    private static final double GRAVITY = 0.08;
    private static final double LENGTH = 0.85;
    private static final double DAMPING = 0.10;
    private static final double ACCEL_GAIN = 2.0;
    private static final double MAX_ACCEL = 0.06;
    private static final double MAX_LEAN = 0.32;
    private static final double LEAN_SPEED = 0.20;
    private static final double SWAY = 0.05;
    private static final double SWAY_RATE = 0.28;
    private static final double MAX_OFFSET = 0.5;

    private static final double SLOW_SPEED = 0.002;

    private static final double SNAP_SPEED = 1.5;
    private static final double YAW_LERP = 0.25;
    private static final float MAX_YAW_STEP = 45f;

    private static final Map<Integer, State> STATES = new HashMap<>();

    private static final class State {
        double dx, dz;
        double vx, vz;
        double hvx, hvz;
        double phase;
        Vec3 lastTarget;
        long lastSeen;
        boolean primed;
        float heldYaw;
        boolean hasYaw;
    }

    private ChestSwingHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            STATES.clear();
            return;
        }
        long now = mc.level.getGameTime();

        for (Map.Entry<Integer, ChainConveyorPackage.ChainConveyorPackagePhysicsData> e : ChainConveyorPackage.physicsDataCache
                .get(mc.level).asMap().entrySet()) {
            var data = e.getValue();
            if (data == null || data.modelKey == null || !ChestModels.isChestKey(data.modelKey))
                continue;
            if (data.targetPos == null || data.prevTargetPos == null || data.pos == null)
                continue;

            State s = STATES.computeIfAbsent(e.getKey(), id -> {
                State st = new State();
                st.phase = id * 1.7;
                return st;
            });
            s.lastSeen = now;

            if (data.targetPos == s.lastTarget)
                continue;
            s.lastTarget = data.targetPos;

            step(s, data);
        }

        STATES.values().removeIf(s -> now - s.lastSeen > 40);
    }

    private static void step(State s, ChainConveyorPackage.ChainConveyorPackagePhysicsData data) {
        Vec3 t = data.targetPos;
        Vec3 pt = data.prevTargetPos;

        double vx = t.x - pt.x, vz = t.z - pt.z;
        double ax = s.primed ? vx - s.hvx : 0, az = s.primed ? vz - s.hvz : 0;
        s.hvx = vx;
        s.hvz = vz;
        s.primed = true;

        double aLen = Math.hypot(ax, az);
        if (aLen > MAX_ACCEL) {
            ax *= MAX_ACCEL / aLen;
            az *= MAX_ACCEL / aLen;
        }

        double speed = Math.hypot(vx, vz);

        if (!s.hasYaw) {
            s.heldYaw = data.yaw;
            s.hasYaw = true;
        }
        float prevYaw = s.heldYaw;
        if (speed > SLOW_SPEED && speed < SNAP_SPEED) {
            float want = Mth.wrapDegrees((float) Mth.atan2(vx, vz) * Mth.RAD_TO_DEG - 90f);
            float diff = Mth.wrapDegrees(want - s.heldYaw);
            s.heldYaw = Mth.wrapDegrees(s.heldYaw + Mth.clamp(diff * (float) YAW_LERP, -MAX_YAW_STEP, MAX_YAW_STEP));
        }
        data.prevYaw = prevYaw;
        data.yaw = s.heldYaw;
        double ex = 0, ez = 0;
        if (speed > 1e-4) {
            double sat = 1 - Math.exp(-speed / LEAN_SPEED);
            double dirX = vx / speed, dirZ = vz / speed;
            s.phase += SWAY_RATE;
            double sway = SWAY * sat * Math.sin(s.phase);
            ex = -dirX * MAX_LEAN * sat - dirZ * sway;
            ez = -dirZ * MAX_LEAN * sat + dirX * sway;
        }

        double k = GRAVITY / LENGTH;
        s.vx += -k * (s.dx - ex) - DAMPING * s.vx - ACCEL_GAIN * ax;
        s.vz += -k * (s.dz - ez) - DAMPING * s.vz - ACCEL_GAIN * az;
        s.dx += s.vx;
        s.dz += s.vz;

        double len = Math.hypot(s.dx, s.dz);
        if (len > MAX_OFFSET) {
            s.dx *= MAX_OFFSET / len;
            s.dz *= MAX_OFFSET / len;
        }

        data.pos = new Vec3(t.x + s.dx, data.pos.y, t.z + s.dz);
    }
}
