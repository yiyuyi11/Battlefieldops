package cn.bfops.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source-pack policy: vanilla music waits while a match soundtrack is active. */
@Mixin(MusicManager.class)
public abstract class MatchMusicMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$music(CallbackInfo ci){if(cn.bfops.Client.matchMusicActive())ci.cancel();}
}
