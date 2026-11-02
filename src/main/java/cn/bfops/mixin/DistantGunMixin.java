package cn.bfops.mixin;

import cn.bfops.TaczGunCull;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class DistantGunMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$cull(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(TaczGunCull.cull(entity,stack))ci.cancel();
    }
}
