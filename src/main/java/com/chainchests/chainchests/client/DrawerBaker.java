package com.chainchests.chainchests.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chainchests.chainchests.ChainChests;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class DrawerBaker {
    static final class Baked {
        final List<BakedQuad> unculled = new ArrayList<>();
        final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);

        boolean isEmpty() {
            return unculled.isEmpty() && culled.isEmpty();
        }
    }

    private static final int[][][] CORNERS = {
             { { 0, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 } },
             { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 } },
             { { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 }, { 0, 1, 0 } },
             { { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 } },
             { { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 } },
             { { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 } } };

    private DrawerBaker() {}

    static Baked bake(ResourceLocation itemId) {
        try {
            return doBake(itemId);
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not read drawer model for {}", itemId, t);
            return null;
        }
    }

    private static Baked doBake(ResourceLocation itemId) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;

        ResourceLocation cur = ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath());
        for (int depth = 0; cur != null && depth < 16; depth++) {
            JsonObject obj = load(rm, cur);
            if (obj == null)
                break;
            if (obj.has("textures") && obj.get("textures").isJsonObject())
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("textures").entrySet())
                    if (e.getValue().isJsonPrimitive())
                        textures.putIfAbsent(e.getKey(), e.getValue().getAsString());
            if (elements == null && obj.has("elements") && obj.get("elements").isJsonArray())
                elements = obj.getAsJsonArray("elements");
            cur = obj.has("parent") ? ResourceLocation.tryParse(obj.get("parent").getAsString()) : null;
        }
        if (elements == null)
            return null;

        Baked out = new Baked();
        for (JsonElement el : elements) {
            if (!el.isJsonObject())
                continue;
            JsonObject eo = el.getAsJsonObject();
            float[] from = floats(eo.getAsJsonArray("from"), 3);
            float[] to = floats(eo.getAsJsonArray("to"), 3);
            boolean shade = !eo.has("shade") || eo.get("shade").getAsBoolean();
            for (Map.Entry<String, JsonElement> fe : eo.getAsJsonObject("faces").entrySet()) {
                Direction dir = Direction.byName(fe.getKey());
                if (dir == null || !fe.getValue().isJsonObject())
                    continue;
                JsonObject fo = fe.getValue().getAsJsonObject();
                TextureAtlasSprite sprite = sprite(resolve(textures, fo.get("texture").getAsString()));
                if (sprite == null)
                    return null;
                float[] uv = fo.has("uv") ? floats(fo.getAsJsonArray("uv"), 4) : defaultUv(dir, from, to);
                int tint = fo.has("tintindex") ? fo.get("tintindex").getAsInt() : -1;
                Direction cull = fo.has("cullface") ? Direction.byName(fo.get("cullface").getAsString()) : null;
                BakedQuad quad = quad(from, to, dir, uv, sprite, tint, shade);
                if (cull == null)
                    out.unculled.add(quad);
                else
                    out.culled.computeIfAbsent(cull, k -> new ArrayList<>()).add(quad);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static JsonObject load(ResourceManager rm, ResourceLocation model) {
        Optional<Resource> res = rm.getResource(model.withPath("models/" + model.getPath() + ".json"));
        if (res.isEmpty())
            return null;
        try (Reader r = res.get().openAsReader()) {
            JsonElement e = JsonParser.parseReader(r);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String resolve(Map<String, String> textures, String ref) {
        for (int i = 0; i < 16 && ref != null && ref.startsWith("#"); i++)
            ref = textures.get(ref.substring(1));
        return ref == null || ref.startsWith("#") ? null : ref;
    }

    private static TextureAtlasSprite sprite(String id) {
        if (id == null)
            return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null)
            return null;
        TextureAtlasSprite s = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(rl);
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : s;
    }

    private static float[] floats(JsonArray a, int n) {
        if (a == null || a.size() < n)
            throw new IllegalArgumentException("bad vector");
        float[] f = new float[n];
        for (int i = 0; i < n; i++)
            f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static float[] defaultUv(Direction d, float[] f, float[] t) {
        return switch (d) {
            case DOWN -> new float[] { f[0], 16f - t[2], t[0], 16f - f[2] };
            case UP -> new float[] { f[0], f[2], t[0], t[2] };
            case NORTH -> new float[] { 16f - t[0], 16f - t[1], 16f - f[0], 16f - f[1] };
            case SOUTH -> new float[] { f[0], 16f - t[1], t[0], 16f - f[1] };
            case WEST -> new float[] { f[2], 16f - t[1], t[2], 16f - f[1] };
            case EAST -> new float[] { 16f - t[2], 16f - t[1], 16f - f[2], 16f - f[1] };
        };
    }

    private static BakedQuad quad(float[] from, float[] to, Direction dir, float[] uv, TextureAtlasSprite sprite,
            int tint, boolean shade) {
        int[] data = new int[32];
        int[][] corners = CORNERS[dir.get3DDataValue()];
        int normal = (((byte) (dir.getStepX() * 127)) & 0xFF) | ((((byte) (dir.getStepY() * 127)) & 0xFF) << 8)
                | ((((byte) (dir.getStepZ() * 127)) & 0xFF) << 16);
        float du = sprite.getU1() - sprite.getU0(), dv = sprite.getV1() - sprite.getV0();
        for (int i = 0; i < 4; i++) {
            int[] c = corners[i];
            float x = (c[0] == 1 ? to[0] : from[0]) / 16f;
            float y = (c[1] == 1 ? to[1] : from[1]) / 16f;
            float z = (c[2] == 1 ? to[2] : from[2]) / 16f;
            float u = i < 2 ? uv[0] : uv[2];
            float v = (i == 0 || i == 3) ? uv[1] : uv[3];
            int b = i * 8;
            data[b] = Float.floatToRawIntBits(x);
            data[b + 1] = Float.floatToRawIntBits(y);
            data[b + 2] = Float.floatToRawIntBits(z);
            data[b + 3] = -1;
            data[b + 4] = Float.floatToRawIntBits(sprite.getU0() + du * (u / 16f));
            data[b + 5] = Float.floatToRawIntBits(sprite.getV0() + dv * (v / 16f));
            data[b + 6] = 0;
            data[b + 7] = normal;
        }
        return new BakedQuad(data, tint, dir, sprite, shade, true);
    }
}
