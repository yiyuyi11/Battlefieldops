package cn.bfops.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="xaero.hud.minimap.info.render.InfoDisplayRenderer",remap=false)
public abstract class XaeroInfoMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$rosterSpace(CallbackInfo ci){if(cn.bfops.NativeXaero.hideNativeInfo())ci.cancel();}
}
