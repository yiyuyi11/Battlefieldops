package cn.bfops.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class SupportItemMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$supportItem(ItemStack stack,ItemDisplayContext context,boolean leftHand,PoseStack poses,
            MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo ci){
        if(cn.bfops.ReferenceProps.renderItem(stack,context,poses,buffers,light))ci.cancel();
    }
}
