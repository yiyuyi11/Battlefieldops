package cn.bfops.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DownedJumpMixin {
    @Inject(method={"jumpFromGround","jumpInLiquid"},at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$downedJump(CallbackInfo ci){
        if((Object)this instanceof Player p&&cn.bfops.BattleActions.downed(p))ci.cancel();
    }
}
