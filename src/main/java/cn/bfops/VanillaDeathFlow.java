package cn.bfops;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ScreenEvent;

/** Ordinary single-player/PvE deaths also get the camera, then return to vanilla respawn controls. */
@EventBusSubscriber(modid="battlefield_ops",value=Dist.CLIENT)
public final class VanillaDeathFlow {
    private static boolean restoring;
    static boolean active(){return Minecraft.getInstance().screen instanceof View;}
    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event){
        if(!restoring && event.getNewScreen() instanceof DeathScreen original && !DeploymentLock.room(Client.state))
            event.setNewScreen(new View(original));
    }
    private static final class View extends Screen {
        private final Screen original;
        private final JsonObject snapshot=new JsonObject();
        private long started;
        private boolean skipped;
        View(Screen original){
            super(net.minecraft.network.chat.Component.literal("阵亡"));this.original=original;
            var me=new JsonObject();me.addProperty("alive",false);me.addProperty("downed",false);
            snapshot.add("me",me);snapshot.addProperty("inMatch",true);
        }
        @Override protected void init(){
            if(started!=0)return;
            started=System.currentTimeMillis();
            DeathCard.reset();DeathCard.show(new JsonObject());
            var player=minecraft.player;
            if(player!=null){
                var source=player.getLastDamageSource();
                var killer=source==null?player.getKillCredit():source.getEntity();
                var target=killer==null||killer==player?null:killer.position().add(0,killer.getBbHeight()*.72,0);
                Cinematic.startDeath(player.getX(),player.getEyeY(),player.getZ(),player.getYRot(),player.getXRot(),target);
                MusicDirector.death();
            }
        }
        @Override public void tick(){
            if(minecraft.player==null||minecraft.player.isAlive()){finish(null);return;}
            if(skipped?!DeathCard.blockingDeploy():System.currentTimeMillis()-started>=2600)finish(original);
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers){
            if(minecraft.options.keyJump.matches(key,scan)){skipped=true;DeathCard.release();return true;}
            return super.keyPressed(key,scan,modifiers);
        }
        private void finish(Screen next){
            Cinematic.clear();DeathCard.reset();
            restoring=true;
            try{minecraft.setScreen(next);}finally{restoring=false;}
        }
        @Override public void render(GuiGraphics g,int mx,int my,float dt){DeathCard.drawLocal(g,minecraft,snapshot);}
        @Override public void renderBackground(GuiGraphics g){}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean shouldCloseOnEsc(){return false;}
        @Override public void onClose(){}
        @Override public void removed(){Cinematic.clear();DeathCard.reset();}
    }
}
