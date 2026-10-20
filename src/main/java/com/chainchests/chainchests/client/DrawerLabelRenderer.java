package com.chainchests.chainchests.client;

import java.util.Map;

import com.chainchests.chainchests.ChainChests;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.block.BlockDrawers;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.config.ModClientConfig;
import com.jaquadro.minecraft.storagedrawers.util.CountFormatter;
import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class DrawerLabelRenderer {
    private static final class Cached {
        final CustomData source;
        final BlockEntityDrawers tile;

        Cached(CustomData source, BlockEntityDrawers tile) {
            this.source = source;
            this.tile = tile;
        }
    }

    private static final Map<ItemStack, Cached> TILES = new MapMaker().weakKeys().makeMap();

    private static final Matrix3f LIGHT_3D = new Matrix3f().rotationYXZ(.36f, -.36f, -.014f);
    private static final Matrix3f LIGHT_FLAT = new Matrix3f().rotationYXZ(0, -.785398f, 0);
    private static final int TEXT_COLOR = FastColor.ARGB32.color(0, 255, 255, 255);

    private DrawerLabelRenderer() {}

    static void render(Minecraft mc, ItemStack stack, ChainConveyorPackage.ChainConveyorPackagePhysicsData data,
            float partial, PoseStack ps, MultiBufferSource buffers, Vec3 cam) {
        Level level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || !(stack.getItem() instanceof BlockItem bi)
                || !(bi.getBlock() instanceof BlockDrawers block))
            return;

        Vec3 position = data.prevPos.lerp(data.pos, partial);
        Vec3 target = data.prevTargetPos.lerp(data.targetPos, partial);
        float yaw = AngleHelper.angleLerp(partial, data.prevYaw, data.yaw);

        double distance = target.distanceTo(cam);
        double labelRange = ModClientConfig.INSTANCE.RENDER.labelRenderDistance.get();
        if (labelRange > 0 && distance > labelRange)
            return;

        double yawRad = Math.toRadians(yaw);
        if (Math.sin(yawRad) * (cam.x - target.x) + Math.cos(yawRad) * (cam.z - target.z) < 0)
            return;

        BlockEntityDrawers tile = tile(level, stack, block);
        if (tile == null || tile.getDrawerAttributes().isConcealed())
            return;

        Vec3 dangle = VecHelper.rotate(target.add(0, 0.5, 0).subtract(position), -yaw, net.minecraft.core.Direction.Axis.Y);
        float zRot = Mth.clamp(Mth.wrapDegrees((float) Mth.atan2(-dangle.x, dangle.y) * Mth.RAD_TO_DEG) / 2f, -25f, 25f);
        float xRot = Mth.clamp(Mth.wrapDegrees((float) Mth.atan2(dangle.z, dangle.y) * Mth.RAD_TO_DEG) / 2f, -25f, 25f);

        BlockPos at = BlockPos.containing(position);
        int light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.SKY, at));

        ps.pushPose();
        ps.translate(target.x - cam.x, target.y - cam.y, target.z - cam.z);
        ps.translate(0, 0.625, 0);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(zRot));
        ps.mulPose(Axis.XP.rotationDegrees(xRot));
        ps.translate(-0.5, -0.5, -0.5);
        ps.translate(0, -PackageItem.getHookDistance(stack) + 0.4375, 0);

        ps.translate(0.5, 0.5 - 9f / 16f, 0.5);
        ps.scale(0.875f, 0.875f, 0.875f);
        ps.translate(-0.5, -0.5, -0.5);

        try {
            drawItems(mc, tile, block, ps, buffers, light);
            if (tile.getDrawerAttributes().isShowingQuantity())
                drawCounts(mc, tile, block, ps, buffers, light, (float) distance);
        } finally {
            ps.popPose();
        }
    }

    private static void drawItems(Minecraft mc, BlockEntityDrawers tile, BlockDrawers block, PoseStack ps,
            MultiBufferSource buffers, int light) {
        ItemRenderer itemRenderer = mc.getItemRenderer();
        int count = Math.min(tile.getGroup().getDrawerCount(), block.labelGeometry.length);
        for (int i = 0; i < count; i++) {
            IDrawer drawer = tile.getGroup().getDrawer(i);
            if (!drawer.isEnabled() || drawer.isEmpty())
                continue;
            ItemStack item = drawer.getStoredItemPrototype();
            if (item.isEmpty())
                continue;

            AABB g = block.labelGeometry[i];
            float scaleX = (float) g.getXsize() / 16;
            float scaleY = (float) g.getYsize() / 16;
            float moveX = (float) g.minX + (8 * scaleX);
            float moveY = 16f - (float) g.maxY + (8 * scaleY);
            float moveZ = (float) g.minZ * .0625f - 0.0025f;

            ps.pushPose();
            ps.translate(moveX / 16, 1 - moveY / 16, 1 - moveZ);
            ps.mulPose(new Matrix4f().scale(scaleX, scaleY, 0.001f));
            try {
                BakedModel model = itemRenderer.getModel(item, null, null, 0);
                if (model.isGui3d())
                    ps.last().normal().mul(LIGHT_3D);
                else
                    ps.last().normal().mul(LIGHT_FLAT);
                ItemDisplayContext ctx = model.isCustomRenderer() ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI;
                itemRenderer.render(item, ctx, false, ps, buffers, light, OverlayTexture.NO_OVERLAY, model);
            } catch (Exception ignored) {
            }
            ps.popPose();
        }
    }

    private static void drawCounts(Minecraft mc, BlockEntityDrawers tile, BlockDrawers block, PoseStack ps,
            MultiBufferSource buffers, int light, float distance) {
        float alpha = 1;
        double fade = ModClientConfig.INSTANCE.RENDER.quantityFadeDistance.get();
        if (fade == 0 || distance > fade)
            alpha = Math.max(1f - ((distance - 4) / 6), 0.05f);
        double range = ModClientConfig.INSTANCE.RENDER.quantityRenderDistance.get();
        if (range != 0 && distance >= range)
            return;

        Font font = mc.font;
        int count = Math.min(tile.getGroup().getDrawerCount(), block.countGeometry.length);
        for (int i = 0; i < count; i++) {
            String text = CountFormatter.format(font, tile.getGroup().getDrawer(i));
            if (text == null || text.isEmpty())
                continue;
            AABB g = block.countGeometry[i];
            float x = (float) (g.minX + g.getXsize() / 2);
            float y = 16f - (float) g.minY - (float) g.getYsize();
            float z = (float) g.minZ * .0625f - .01f;

            ps.pushPose();
            ps.translate(x / 16, 1 - y / 16, 1 - z);
            ps.scale(1 / 128f, -1 / 128f, 1);
            int color = (int) (255 * alpha) << 24 | TEXT_COLOR;
            font.drawInBatch(text, -font.width(text) / 2f, 0, color, false, ps.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, light);
            ps.popPose();
        }
    }

    private static BlockEntityDrawers tile(Level level, ItemStack stack, BlockDrawers block) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        Cached c = TILES.get(stack);
        if (c != null && (c.source == data || (data != null && data.equals(c.source))))
            return c.tile;
        try {
            if (!(((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof BlockEntityDrawers tile))
                return null;
            if (data != null) {
                CompoundTag tag = data.copyTag();
                tile.readPortable(level.registryAccess(), tag);
            }
            TILES.put(stack, new Cached(data, tile));
            return tile;
        } catch (Throwable t) {
            ChainChests.LOGGER.warn("Could not read hung drawer contents for rendering", t);
            TILES.put(stack, new Cached(data, null));
            return null;
        }
    }
}
