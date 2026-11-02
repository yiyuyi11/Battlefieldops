package cn.bfops.mixin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler",remap=false)
public abstract class XaeroOverlayMixin {
    @Shadow private double ps,pc,zoom;
    @Shadow private int halfViewW,halfViewH;
    @Shadow private boolean circle;
    @Shadow private float optionalScale;
    @Inject(method="afterRender",at=@At("TAIL"),require=0)
    private void bfops$overlay(GuiGraphics graphics,@Coerce Object info,MultiBufferSource.BufferSource buffers,CallbackInfo ci){
        cn.bfops.NativeXaero.draw(graphics,info,ps,pc,zoom,halfViewW,halfViewH,circle,optionalScale);
    }
}
