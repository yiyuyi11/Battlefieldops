package cn.bfops.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="xaero.common.minimap.render.MinimapRenderer",remap=false)
public abstract class XaeroSelfMixin {
 @Inject(method="drawArrow",at=@At("HEAD"),cancellable=true,require=0)
 private void bfops$self(CallbackInfo ci){if(cn.bfops.NativeXaero.available()&&cn.bfops.NativeXaero.show())ci.cancel();}
}
