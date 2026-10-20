package com.chainchests.chainchests.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

final class QuadModel extends BakedModelWrapper<BakedModel> {
    static final class Quads {
        List<BakedQuad> unculled = List.of();
        final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
    }

    private final Quads quads;

    QuadModel(BakedModel base, Quads quads) {
        super(base);
        this.quads = quads;
    }

    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return side == null ? quads.unculled : quads.culled.getOrDefault(side, List.of());
    }

    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
            RenderType renderType) {
        return getQuads(state, side, rand);
    }

    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }
}
