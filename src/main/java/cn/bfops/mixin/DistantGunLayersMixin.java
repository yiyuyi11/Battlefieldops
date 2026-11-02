package cn.bfops.mixin;

import cn.bfops.TaczGunCull;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.tacz.guns.client.renderer.other.HumanoidOffhandRender",remap=false)
public abstract class DistantGunLayersMixin {
    @Inject(method="renderGunItem",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void bfops$cull(LivingEntity owner,PoseStack pose,MultiBufferSource buffers,int light,ItemStack stack,@Coerce Object layer,CallbackInfo ci){
        if(TaczGunCull.cull(owner,stack))ci.cancel();
    }
}
