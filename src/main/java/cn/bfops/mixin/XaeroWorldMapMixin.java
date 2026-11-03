package cn.bfops.mixin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="xaero.map.gui.GuiMap",remap=false)
public abstract class XaeroWorldMapMixin {
 @Shadow private double cameraX,cameraZ,scale,screenScale;
 @Shadow private ResourceKey<Level> lastViewedDimensionId;
 @Inject(method="render",at=@At("TAIL"),require=0)
 private void bfops$overlay(GuiGraphics g,int mx,int my,float dt,CallbackInfo ci){cn.bfops.Bf1WorldMap.draw(g,cameraX,cameraZ,scale,screenScale,lastViewedDimensionId);}
 @Inject(method="drawArrowOnMap",at=@At("HEAD"),cancellable=true,require=0)
 private void bfops$self(CallbackInfo ci){if(cn.bfops.Bf1WorldMap.active())ci.cancel();}
}
