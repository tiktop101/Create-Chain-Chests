package com.chainchests.chainchests.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.HangableKind;
import com.google.common.collect.MapMaker;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class HangingVariants {
    private record Cached(int generation, ResourceLocation key) {}

    private static final Map<ItemStack, Cached> BY_STACK = new MapMaker().weakKeys().makeMap();
    private static final Set<ResourceLocation> READY = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> REQUESTED = ConcurrentHashMap.newKeySet();
    private static volatile int generation = 0;

    private HangingVariants() {}

    public static void onReload() {
        generation++;
        READY.clear();
        REQUESTED.clear();
    }

    public static void apply(ItemStack stack, ChainConveyorPackage.ChainConveyorPackagePhysicsData data) {
        if (stack == null || stack.isEmpty())
            return;
        int gen = generation;
        Cached cached = BY_STACK.get(stack);
        if (cached == null || cached.generation() != gen) {
            cached = new Cached(gen, computeKey(stack, gen));
            BY_STACK.put(stack, cached);
        }
        ResourceLocation key = cached.key();
        if (key == null)
            return;
        if (READY.contains(key)) {
            if (!key.equals(data.modelKey))
                data.modelKey = key;
        } else if (REQUESTED.add(key)) {
            ItemStack copy = stack.copyWithCount(1);
            Minecraft.getInstance().execute(() -> build(copy, key));
        }
    }

    private static ResourceLocation computeKey(ItemStack stack, int gen) {
        HangableKind kind = HangableKind.of(stack);
        if (kind != HangableKind.SOPHISTICATED)
            return null;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String sig = HangingModelFactory.signature(stack, kind);
        return ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID,
                "dyn/" + id.getNamespace() + "/" + id.getPath() + "/" + Integer.toHexString(sig.hashCode()) + "_" + gen);
    }

    private static void build(ItemStack stack, ResourceLocation key) {
        try {
            HangableKind kind = HangableKind.of(stack);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            BakedModel model = HangingModelFactory.create(stack, id, kind);
            PartialModel rigging = AllPartialModels.PACKAGE_RIGGING.get(id);
            if (model == null || rigging == null)
                return;
            PartialModel partial = newPartial(key, model);
            AllPartialModels.PACKAGES.put(key, partial);
            AllPartialModels.PACKAGE_RIGGING.put(key, rigging);
            ChestModels.markChestKey(key);
            READY.add(key);
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not build hanging model for {}", stack, t);
        }
    }

    private static PartialModel newPartial(ResourceLocation key, BakedModel model) throws ReflectiveOperationException {
        Constructor<PartialModel> ctor = PartialModel.class.getDeclaredConstructor(ResourceLocation.class);
        ctor.setAccessible(true);
        PartialModel partial = ctor.newInstance(key);
        Field baked = PartialModel.class.getDeclaredField("bakedModel");
        baked.setAccessible(true);
        baked.set(partial, model);
        return partial;
    }
}
