package com.chainchests.chainchests.client;

import com.chainchests.chainchests.ChainChests;
import com.simibubi.create.AllPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ChestModels {
    private static final Set<ResourceLocation> CHEST_KEYS = new HashSet<>();

    private ChestModels() {}

    public static boolean isChestKey(ResourceLocation key) {
        return CHEST_KEYS.contains(key);
    }

    private static final String[] COLORS = { "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black" };

    public static void registerModded() {
        PartialModel rigging = model("item/hanging_chest_rigging");
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!(item instanceof net.minecraft.world.item.BlockItem))
                continue;
            if (com.chainchests.chainchests.HangableKind.isSophisticated(id)) {
                String p = id.getPath();
                String m = p.contains("barrel") ? "hanging_barrel"
                        : p.contains("shulker_box") ? "hanging_ss_shulker_box" : "hanging_chest";
                add(id, model("item/" + m), rigging);
            } else if (com.chainchests.chainchests.HangableKind.isDrawer(id)) {
                add(id, drawerModel(id), rigging);
            }
        }
    }

    private static Set<String> staticDrawers;

    private static PartialModel drawerModel(ResourceLocation id) {
        if (staticDrawers == null) {
            staticDrawers = new HashSet<>();
            try (InputStream in = ChestModels.class.getResourceAsStream("/assets/chainchests/hanging_drawers.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String line; (line = r.readLine()) != null;)
                        if (!line.isBlank())
                            staticDrawers.add(line.trim());
                }
            } catch (Exception e) {
                ChainChests.LOGGER.warn("Could not read hanging drawer list", e);
            }
        }
        return model(staticDrawers.contains(id.getPath()) ? "item/hanging_drawers/" + id.getPath() : "item/hanging_drawer");
    }

    public static void markChestKey(ResourceLocation key) {
        CHEST_KEYS.add(key);
    }

    public static void register() {
        PartialModel rigging = model("item/hanging_chest_rigging");
        add(Items.CHEST, "hanging_chest", rigging);
        add(Items.TRAPPED_CHEST, "hanging_trapped_chest", rigging);
        add(Items.ENDER_CHEST, "hanging_ender_chest", rigging);
        add(Items.BARREL, "hanging_barrel", rigging);
        add(Items.SHULKER_BOX, "hanging_shulker_box", rigging);
        for (String color : COLORS)
            add(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color + "_shulker_box")),
                    "hanging_" + color + "_shulker_box", rigging);
    }

    private static void add(Item item, String modelName, PartialModel rigging) {
        add(BuiltInRegistries.ITEM.getKey(item), model("item/" + modelName), rigging);
    }

    private static void add(ResourceLocation key, PartialModel body, PartialModel rigging) {
        CHEST_KEYS.add(key);
        AllPartialModels.PACKAGES.put(key, body);
        AllPartialModels.PACKAGE_RIGGING.put(key, rigging);
    }

    private static PartialModel model(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID, path));
    }
}
