package cn.bfops.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MatchDropMixin {
    @Inject(method="drop",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$keepEquipment(boolean all,CallbackInfoReturnable<Boolean> cir){
        if(cn.bfops.Client.inBattleRoom())cir.setReturnValue(false);
    }
}
