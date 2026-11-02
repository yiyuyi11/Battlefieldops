package cn.bfops.mixin;
import cn.bfops.TeamOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class)
public abstract class TeamOutlineMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$outline(Entity entity,CallbackInfoReturnable<Boolean> result){
        if(TeamOutline.color(entity)!=0)result.setReturnValue(true);
    }
}
