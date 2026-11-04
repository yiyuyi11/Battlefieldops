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
    @Inject(method="render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",at=@At("HEAD"),cancellable=true)
    private void bfops$supportItem(ItemStack stack,ItemDisplayContext context,boolean leftHand,PoseStack poses,
            MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo ci){
        if(cn.bfops.ReferenceProps.renderItem(stack,context,poses,buffers,light))ci.cancel();
    }
}
