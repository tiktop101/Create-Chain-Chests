package com.chainchests.chainchests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ChainChests.MOD_ID)
public final class ChainRouting {
    private static final int SCAN_INTERVAL = 20;
    private static final int SCAN_RADIUS = 8;
    private static final int KEEP_TICKS = 100;

    private static final Map<ChainConveyorBlockEntity, Long> ACTIVE = new WeakHashMap<>();

    private static final Map<ChainConveyorPackage, BlockPos> CAME_FROM = new WeakHashMap<>();
    private static long tickCounter;

    private ChainRouting() {}

    public static void register(ChainConveyorBlockEntity be) {
        ACTIVE.put(be, tickCounter);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        MinecraftServer server = event.getServer();

        if (tickCounter % SCAN_INTERVAL == 0)
            scanAroundPlayers(server);

        for (ChainConveyorBlockEntity be : new ArrayList<>(ACTIVE.keySet())) {
            if (be.isRemoved() || be.getLevel() == null || be.getLevel().isClientSide()
                    || !be.getLevel().isLoaded(be.getBlockPos())) {
                ACTIVE.remove(be);
                continue;
            }
            if (process(be))
                ACTIVE.put(be, tickCounter);
            else if (tickCounter - ACTIVE.getOrDefault(be, tickCounter) > KEEP_TICKS)
                ACTIVE.remove(be);
        }
    }

    private static void scanAroundPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            ChunkPos center = player.chunkPosition();
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                    if (chunk == null)
                        continue;
                    for (BlockEntity be : chunk.getBlockEntities().values())
                        if (be instanceof ChainConveyorBlockEntity ccbe && holdsStorage(ccbe))
                            ACTIVE.put(ccbe, tickCounter);
                }
        }
    }

    private static boolean holdsStorage(ChainConveyorBlockEntity be) {
        for (ChainConveyorPackage p : be.getLoopingPackages())
            if (ChainChests.isHangable(p.item))
                return true;
        for (List<ChainConveyorPackage> list : be.getTravellingPackages().values())
            for (ChainConveyorPackage p : list)
                if (ChainChests.isHangable(p.item))
                    return true;
        return false;
    }

    private static boolean process(ChainConveyorBlockEntity be) {
        boolean holds = false;

        for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : be.getTravellingPackages().entrySet()) {
            for (ChainConveyorPackage box : entry.getValue()) {
                if (!ChainChests.isHangable(box.item))
                    continue;
                holds = true;
                CAME_FROM.put(box, entry.getKey().multiply(-1));
                if (be.getLevel().getBlockEntity(be.getBlockPos().offset(entry.getKey())) instanceof ChainConveyorBlockEntity next)
                    ACTIVE.put(next, tickCounter);
            }
        }

        List<ChainConveyorPackage> looping = new ArrayList<>();
        for (ChainConveyorPackage box : be.getLoopingPackages())
            if (ChainChests.isHangable(box.item))
                looping.add(box);
        if (looping.isEmpty())
            return holds;
        holds = true;

        float speed = be.getSpeed() / 360f;
        if (speed == 0 || be.getLevel() == null)
            return holds;
        float degreesPerTick = (speed / (Mth.PI * 1.5f)) * 360f;

        be.prepareStats();
        if (be.connections.isEmpty())
            return holds;
        List<BlockPos> connections = new ArrayList<>(be.connections);
        Collections.sort(connections);

        for (ChainConveyorPackage box : looping) {
            float prev = be.wrapAngle(box.chainPosition - degreesPerTick);
            for (BlockPos connection : connections) {
                ConnectionStats stats = be.connectionStats.get(connection);
                if (stats == null)
                    continue;
                BlockEntity other = be.getLevel().getBlockEntity(be.getBlockPos().offset(connection));
                if (other instanceof ChainConveyorBlockEntity ccbe && !ccbe.canAcceptMorePackagesFromOtherConveyor())
                    continue;
                if (!be.loopThresholdCrossed(box.chainPosition, prev, stats.tangentAngle()))
                    continue;
                if (connections.size() > 1 && connection.equals(CAME_FROM.get(box)))
                    continue;

                be.getLoopingPackages().remove(box);
                box.chainPosition = 0;
                if (!be.addTravellingPackage(box, connection)) {
                    be.getLoopingPackages().add(box);
                    continue;
                }
                CAME_FROM.put(box, connection.multiply(-1));
                if (other instanceof ChainConveyorBlockEntity next)
                    ACTIVE.put(next, tickCounter);
                break;
            }
        }
        return holds;
    }
}
