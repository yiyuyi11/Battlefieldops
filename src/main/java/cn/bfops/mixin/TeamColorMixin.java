package cn.bfops.mixin;
import cn.bfops.TeamOutline;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class TeamColorMixin {
    @Inject(method="getTeamColor",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$color(CallbackInfoReturnable<Integer> result){
        int color=TeamOutline.color((Entity)(Object)this);
        if(color!=0)result.setReturnValue(color&0xffffff);
    }
}
