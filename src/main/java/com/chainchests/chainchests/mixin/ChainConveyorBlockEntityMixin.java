package com.chainchests.chainchests.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chainchests.chainchests.ChainChestDrops;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;

import net.minecraft.core.BlockPos;

@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorBlockEntityMixin {
    @Inject(method = "destroy()V", at = @At("HEAD"), remap = false)
    private void chainchests$dropHung(CallbackInfo ci) {
        ChainChestDrops.dropAll((ChainConveyorBlockEntity) (Object) this);
    }

    @Inject(method = "removeConnectionTo(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), remap = false)
    private void chainchests$dropOnChainBreak(BlockPos target, CallbackInfoReturnable<Boolean> cir) {
        ChainChestDrops.dropOnConnection((ChainConveyorBlockEntity) (Object) this, target);
    }

    @Inject(method = "removeInvalidConnections()V", at = @At("TAIL"), remap = false)
    private void chainchests$dropOrphaned(CallbackInfo ci) {
        ChainChestDrops.dropOrphaned((ChainConveyorBlockEntity) (Object) this);
    }
}
