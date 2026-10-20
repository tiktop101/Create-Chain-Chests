package com.chainchests.chainchests.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.chainchests.chainchests.ChainChests;
import com.chainchests.chainchests.HangableKind;
import com.mojang.math.Transformation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;

final class HangingModelFactory {
    private static final String SS = "sophisticatedstorage";

    private static final Set<String> VISUAL = Set.of("wood_type", "shows_tier", "packed", "flat_top",
            "barrel_materials", "simple_material");
    private static final String[] TIERS = { "copper", "iron", "gold", "diamond", "netherite" };

    private static final IQuadTransformer HANG = QuadTransformers.applying(
            new Transformation(new Vector3f(0.5f, 0.5f - 9f / 16f, 0.5f), null, null, null)
                    .compose(new Transformation(null, new Quaternionf().rotateY((float) Math.PI),
                            new Vector3f(0.875f, 0.875f, 0.875f), null))
                    .compose(new Transformation(new Vector3f(-0.5f, -0.5f, -0.5f), null, null, null)));

    private static final IQuadTransformer INFLATE = QuadTransformers.applying(
            new Transformation(new Vector3f(0.5f, -1f / 16f, 0.5f), null, null, null)
                    .compose(new Transformation(null, null, new Vector3f(1.003f, 1.003f, 1.003f), null))
                    .compose(new Transformation(new Vector3f(-0.5f, 1f / 16f, -0.5f), null, null, null)));

    private interface QuadSource {
        List<BakedQuad> get(Direction side);
    }

    private HangingModelFactory() {}

    static String signature(ItemStack stack, HangableKind kind) {
        if (kind == HangableKind.DRAWER)
            return drawerSignature(stack);
        if (kind != HangableKind.SOPHISTICATED)
            return "";
        StringBuilder sb = new StringBuilder();
        for (TypedDataComponent<?> c : stack.getComponents()) {
            ResourceLocation k = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
            if (k != null && k.getNamespace().equals(SS) && VISUAL.contains(k.getPath())) {
                Object v = c.value();
                sb.append(k.getPath()).append('=').append(v instanceof WoodType wt ? wt.name() : String.valueOf(v))
                        .append(';');
            }
        }
        sb.append("main=").append(color(stack, true)).append(";accent=").append(color(stack, false)).append(';');
        return sb.toString();
    }

    private static String drawerSignature(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        for (TypedDataComponent<?> c : stack.getComponents()) {
            ResourceLocation k = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
            if (k != null && k.getNamespace().equals("storagedrawers") && k.getPath().equals("frame_data"))
                sb.append(k.getPath()).append('=').append(c.value()).append(';');
        }
        return sb.toString();
    }

    static BakedModel create(ItemStack stack, ResourceLocation id, HangableKind kind) {
        ModelManager mm = Minecraft.getInstance().getModelManager();
        String path = id.getPath();
        if (kind == HangableKind.DRAWER)
            return drawer(mm, id, stack);
        if (kind == HangableKind.SOPHISTICATED) {
            if (path.contains("barrel"))
                return barrel(mm, id, stack);
            if (path.contains("shulker_box"))
                return shulker(mm, stack, path);
            if (path.contains("chest"))
                return chest(mm, stack, path);
        }
        return null;
    }

    private static BakedModel drawer(ModelManager mm, ResourceLocation id, ItemStack stack) {
        BakedModel base = geometry(mm, "item/hanging_drawer");
        if (base == null)
            return null;
        DrawerBaker.Baked baked = DrawerBaker.bake(id);
        if (baked == null)
            return null;
        return new QuadModel(base, hang(side -> side == null ? baked.unculled
                : baked.culled.getOrDefault(side, List.of())));
    }

    private static BakedModel barrel(ModelManager mm, ResourceLocation id, ItemStack stack) {
        BakedModel src = mm.getModel(ModelResourceLocation.inventory(id));
        if (src == null || src == mm.getMissingModel())
            return null;

        BakedModel resolved = src.getOverrides().resolve(src, stack, null, null, 0);
        BakedModel model = resolved != null ? resolved : src;
        RandomSource rand = RandomSource.create(42);
        int main = color(stack, true), accent = color(stack, false);
        return new QuadModel(src, hang(side -> {
            List<BakedQuad> raw = new ArrayList<>(model.getQuads(null, side, rand, ModelData.EMPTY, RenderType.cutout()));
            raw.addAll(model.getQuads(null, side, rand, ModelData.EMPTY, RenderType.translucent()));

            List<BakedQuad> out = new ArrayList<>(raw.size());
            for (BakedQuad q : raw) {
                int ti = q.getTintIndex();
                int c = ti == 1000 ? main : ti == 1001 ? accent : -1;
                out.add(c == -1 ? q : colored(List.of(q), c).get(0));
            }
            return out;
        }));
    }

    private static List<BakedQuad> transformed(IQuadTransformer transformer, List<BakedQuad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            BakedQuad copy = new BakedQuad(q.getVertices().clone(), q.getTintIndex(), q.getDirection(),
                    q.getSprite(), q.isShade(), q.hasAmbientOcclusion());
            transformer.processInPlace(copy);
            out.add(copy);
        }
        return out;
    }

    private static QuadModel.Quads hang(QuadSource src) {
        QuadModel.Quads q = new QuadModel.Quads();
        q.unculled = transformed(HANG, src.get(null));
        for (Direction d : Direction.values())
            q.culled.put(d, transformed(HANG, src.get(turn(d))));
        return q;
    }

    private static Direction turn(Direction d) {
        return switch (d) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> d;
        };
    }

    private static BakedModel chest(ModelManager mm, ItemStack stack, String path) {
        BakedModel base = geometry(mm, "item/hanging_chest");
        if (base == null)
            return null;
        TextureAtlasSprite wood = sprite(SS + ":entity/chest/" + woodName(stack));
        if (wood == null)
            wood = sprite(SS + ":entity/chest/acacia");
        String tierName = tier(path);
        TextureAtlasSprite tier = sprite(SS + ":entity/chest/" + (tierName == null ? "wood" : tierName) + "_tier");
        if (wood == null)
            return null;

        List<BakedQuad> body = new ArrayList<>();
        List<BakedQuad> lock = new ArrayList<>();
        for (BakedQuad q : allQuads(base))
            (isLock(q) ? lock : body).add(q);

        int main = color(stack, true), accent = color(stack, false);
        TextureAtlasSprite mainTex = sprite(SS + ":entity/chest/tintable_main");
        TextureAtlasSprite accentTex = sprite(SS + ":entity/chest/tintable_accent");
        boolean hasWood = component(stack, "wood_type") != null;
        List<BakedQuad> out = new ArrayList<>();
        if (hasWood || !(main != -1 && accent != -1) || mainTex == null || accentTex == null)
            out.addAll(retexture(body, wood));
        if (main != -1 && mainTex != null)
            out.addAll(colored(retexture(body, mainTex), main));
        if (accent != -1 && accentTex != null)
            out.addAll(transformed(inflate(1.0015f), colored(retexture(body, accentTex), accent)));
        if (tier != null) {
            if (showsTier(stack))
                out.addAll(transformed(INFLATE, retexture(body, tier)));
            out.addAll(retexture(lock, tier));
        } else {
            out.addAll(retexture(lock, wood));
        }
        QuadModel.Quads q = new QuadModel.Quads();
        q.unculled = out;
        return new QuadModel(base, q);
    }

    private static BakedModel shulker(ModelManager mm, ItemStack stack, String path) {
        BakedModel base = geometry(mm, "item/hanging_ss_shulker_box");
        if (base == null)
            return null;
        TextureAtlasSprite plain = sprite(SS + ":entity/shulker_box/no_tint");
        TextureAtlasSprite mainTex = sprite(SS + ":entity/shulker_box/tintable_main");
        TextureAtlasSprite accentTex = sprite(SS + ":entity/shulker_box/tintable_accent");
        String tierName = tier(path);
        TextureAtlasSprite tier = sprite(SS + ":entity/shulker_box/" + (tierName == null ? "base" : tierName) + "_tier");
        if (plain == null)
            return null;
        int main = color(stack, true), accent = color(stack, false);

        if (main == -1 && accent != -1)
            main = accent;
        else if (accent == -1 && main != -1)
            accent = main;
        List<BakedQuad> quads = allQuads(base);
        List<BakedQuad> out = new ArrayList<>();

        out.addAll(transformed(inflate(0.998f), retexture(quads, plain)));
        if (main != -1 && mainTex != null)
            out.addAll(colored(retexture(quads, mainTex), main));
        if (accent != -1 && accentTex != null)
            out.addAll(transformed(inflate(1.0015f), colored(retexture(quads, accentTex), accent)));
        if (tier != null && showsTier(stack))
            out.addAll(transformed(INFLATE, retexture(quads, tier)));
        QuadModel.Quads q = new QuadModel.Quads();
        q.unculled = out;
        return new QuadModel(base, q);
    }

    private static java.lang.reflect.Method mainGetter, accentGetter;
    private static boolean gettersResolved;

    static int color(ItemStack stack, boolean main) {
        try {
            if (!gettersResolved) {
                gettersResolved = true;
                Class<?> c = Class.forName("net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem");
                for (java.lang.reflect.Method m : c.getMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1)
                        continue;
                    if (m.getName().equals("getMainColorFromComponentHolder"))
                        mainGetter = m;
                    else if (m.getName().equals("getAccentColorFromComponentHolder"))
                        accentGetter = m;
                }
            }
            java.lang.reflect.Method m = main ? mainGetter : accentGetter;
            if (m != null && m.invoke(null, stack) instanceof java.util.Optional<?> o && o.isPresent()
                    && o.get() instanceof Integer i)
                return i & 0xFFFFFF;
        } catch (Throwable t) {
            ChainChests.LOGGER.debug("Could not read storage colour", t);
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("sophisticatedcore", main ? "main_color" : "accent_color"));
        Object v = type == null ? null : stack.get(type);
        return v instanceof Integer i && i != -1 ? i & 0xFFFFFF : -1;
    }

    private static List<BakedQuad> colored(List<BakedQuad> quads, int rgb) {
        int abgr = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            int[] v = q.getVertices().clone();
            for (int i = 0; i < 4; i++)
                v[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = abgr;
            out.add(new BakedQuad(v, -1, q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
        }
        return out;
    }

    private static IQuadTransformer inflate(float scale) {
        return QuadTransformers.applying(
                new Transformation(new Vector3f(0.5f, -1f / 16f, 0.5f), null, null, null)
                        .compose(new Transformation(null, null, new Vector3f(scale, scale, scale), null))
                        .compose(new Transformation(new Vector3f(-0.5f, 1f / 16f, -0.5f), null, null, null)));
    }

    private static BakedModel geometry(ModelManager mm, String path) {
        BakedModel m = mm.getModel(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(ChainChests.MOD_ID, path)));
        return m == null || m == mm.getMissingModel() ? null : m;
    }

    private static String woodName(ItemStack stack) {
        Object w = component(stack, "wood_type");
        if (w instanceof WoodType wt) {
            String n = wt.name();
            return n.replace(':', '_');
        }
        return "acacia";
    }

    private static boolean showsTier(ItemStack stack) {
        Object v = component(stack, "shows_tier");
        return !(v instanceof Boolean b) || b;
    }

    private static Object component(ItemStack stack, String path) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ResourceLocation.fromNamespaceAndPath(SS, path));
        return type == null ? null : stack.get(type);
    }

    private static String tier(String path) {
        for (String t : TIERS)
            if (path.startsWith(t + "_"))
                return t;
        return null;
    }

    private static TextureAtlasSprite sprite(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null)
            return null;
        TextureAtlasSprite s = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(rl);
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : s;
    }

    private static List<BakedQuad> allQuads(BakedModel model) {
        RandomSource rand = RandomSource.create(42);
        List<BakedQuad> out = new ArrayList<>(model.getQuads(null, null, rand));
        for (Direction d : Direction.values())
            out.addAll(model.getQuads(null, d, rand));
        return out;
    }

    private static boolean isLock(BakedQuad q) {
        int[] v = q.getVertices();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(v[i * 8]);
            float z = Float.intBitsToFloat(v[i * 8 + 2]);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
        }
        return minX >= 6.9f / 16f && maxX <= 9.1f / 16f && minZ >= 14.9f / 16f;
    }

    private static List<BakedQuad> retexture(List<BakedQuad> quads, TextureAtlasSprite to) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            TextureAtlasSprite from = q.getSprite();
            int[] v = q.getVertices().clone();
            float du = from.getU1() - from.getU0(), dv = from.getV1() - from.getV0();
            for (int i = 0; i < 4; i++) {
                int b = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                float fu = (Float.intBitsToFloat(v[b]) - from.getU0()) / du;
                float fv = (Float.intBitsToFloat(v[b + 1]) - from.getV0()) / dv;
                v[b] = Float.floatToRawIntBits(to.getU0() + fu * (to.getU1() - to.getU0()));
                v[b + 1] = Float.floatToRawIntBits(to.getV0() + fv * (to.getV1() - to.getV0()));
            }
            out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), to, q.isShade(), q.hasAmbientOcclusion()));
        }
        return out;
    }
}
