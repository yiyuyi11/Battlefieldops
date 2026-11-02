package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

/** An independent world-only post pass; never replaces vanilla or shader-pack post chains. */
public final class BoundaryEffect {
    private static PostChain chain;
    private static int width,height;
    private static boolean failed;
    private static float strength;
    private static long lastFrame;
    public static void render(){
        Minecraft mc=Minecraft.getInstance();long now=System.nanoTime();
        float dt=lastFrame==0?0:(float)Math.min(.1,(now-lastFrame)*1e-9);lastFrame=now;
        float desired=0;
        if(mc.level!=null&&mc.player!=null&&Client.state.has("me")&&Client.state.has("arena")&&Client.state.has("inMatch")&&Client.state.get("inMatch").getAsBoolean()){
            var me=Client.state.getAsJsonObject("me");int outside=Engine.num(me,"outside",0);
            if(outside>0&&me.has("alive")&&me.get("alive").getAsBoolean()){
                int seconds=Engine.num(Client.state.getAsJsonObject("arena"),"outsideSeconds",10);
                desired=Math.min(1,.45f+.55f*outside/Math.max(1,seconds));
            }
        }
        strength+=(desired-strength)*(1-(float)Math.exp(-dt*(desired>strength?6:9)));
        if(strength<.002f&&desired==0){strength=0;return;}
        if(failed||mc.level==null)return;
        var target=mc.getMainRenderTarget();
        try{
            if(chain==null){chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),target,ResourceLocation.fromNamespaceAndPath("battlefield_ops","shaders/post/boundary.json"));width=0;}
            if(width!=target.width||height!=target.height){width=target.width;height=target.height;chain.resize(width,height);}
            chain.setUniform("Saturation",1-strength);
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();
            chain.process(0);
        }catch(Exception|LinkageError ex){failed=true;if(chain!=null){chain.close();chain=null;}BattlefieldOps.LOG.warn("Boundary monochrome pass unavailable",ex);}
        finally{target.bindWrite(false);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);}
    }
    public static void reset(){if(chain!=null)chain.close();chain=null;failed=false;strength=0;lastFrame=0;width=height=0;}
}
