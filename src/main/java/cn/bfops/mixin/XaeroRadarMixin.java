package cn.bfops.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="xaero.hud.minimap.element.render.MinimapElementRendererHandler",remap=false)
public abstract class XaeroRadarMixin {
    @Inject(method="renderForRenderer",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$roomOnly(CallbackInfoReturnable<Integer> ci){if(cn.bfops.NativeXaero.room())ci.setReturnValue(0);}
}
