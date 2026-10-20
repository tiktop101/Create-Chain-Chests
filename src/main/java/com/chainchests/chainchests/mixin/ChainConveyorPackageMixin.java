package com.chainchests.chainchests.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chainchests.chainchests.client.HangingVariants;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

@Mixin(value = ChainConveyorPackage.class, remap = false)
public abstract class ChainConveyorPackageMixin {
    @Shadow
    public ItemStack item;

    @Inject(method = "physicsData(Lnet/minecraft/world/level/LevelAccessor;)Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage$ChainConveyorPackagePhysicsData;", at = @At("RETURN"), remap = false)
    private void chainchests$pickModel(LevelAccessor level,
            CallbackInfoReturnable<ChainConveyorPackage.ChainConveyorPackagePhysicsData> cir) {
        ChainConveyorPackage.ChainConveyorPackagePhysicsData data = cir.getReturnValue();
        if (data != null && level.isClientSide())
            HangingVariants.apply(this.item, data);
    }
}
