package cn.bfops.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="xaero.common.minimap.MinimapProcessor",remap=false)
public abstract class XaeroVisibilityMixin {
    @Inject(method="onRender",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$visibility(CallbackInfo ci){if(cn.bfops.NativeXaero.room()&&!cn.bfops.NativeXaero.show())ci.cancel();}
}
