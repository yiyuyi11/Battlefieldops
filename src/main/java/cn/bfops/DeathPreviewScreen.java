package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** A local display preview; never sends a death, inventory, respawn or deployment request. */
@EventBusSubscriber(modid="battlefield_ops",value=Dist.CLIENT)
public final class DeathPreviewScreen extends Screen {
    private static String pending;
    private final JsonObject snapshot;
    private final JsonObject card;
    private boolean started;
    private boolean skipped, overview;
    private final boolean downed;
    private final boolean wasDead;

    private DeathPreviewScreen(boolean downed){
        super(Component.literal("死亡界面预览"));
        this.downed=downed;
        var mc=Minecraft.getInstance();
        var current=Client.state.has("me")?Client.state.getAsJsonObject("me"):new JsonObject();
        wasDead=current.has("alive")&&!current.get("alive").getAsBoolean();
        snapshot=sample(downed);
        card=new JsonObject();card.addProperty("killer","示例敌方士兵");card.addProperty("kit","侦察兵");
        card.addProperty("type",downed?"downedCard":"deathStats");
        card.addProperty("weapon",Engine.str(current,"lastPrimary",Engine.str(current,"primary","")));
        card.addProperty("distance",24);card.addProperty("killerHealth",13);card.addProperty("killerMaxHealth",20);
    }
    static JsonObject sample(boolean downed){
        var state=new JsonObject();state.addProperty("inMatch",true);state.addProperty("downLeft",24);state.addProperty("reviveProgress",20);
        var me=new JsonObject();me.addProperty("name","预览玩家");me.addProperty("alive",downed);me.addProperty("downed",downed);
        me.addProperty("team",0);me.addProperty("x",0);me.addProperty("y",64);me.addProperty("z",0);state.add("me",me);
        var mates=new JsonArray();
        for(int i=0;i<3;i++){
            var mate=new JsonObject();mate.addProperty("name","示例医疗兵 "+(i+1));mate.addProperty("kit","支援兵");
            mate.addProperty("alive",true);mate.addProperty("team",0);mate.addProperty("x",6+i*8);mate.addProperty("y",64);mate.addProperty("z",0);mates.add(mate);
        }
        state.add("mates",mates);return state;
    }
    static boolean active(){return Minecraft.getInstance().screen instanceof DeathPreviewScreen;}
    @SubscribeEvent public static void hideHud(RenderGuiEvent.Pre event){if(active())event.setCanceled(true);}
    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("bfpreview")
            .executes(c->request(false))
            .then(Commands.literal("death").executes(c->request(false)))
            .then(Commands.literal("downed").executes(c->request(true))));
    }
    private static int request(boolean downed){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return 0;
        pending=downed?"downed":"death";return 1;
    }
    @SubscribeEvent
    public static void tickPending(ClientTickEvent.Post event){
        if(pending==null)return;
        String mode=pending;pending=null;
        var mc=Minecraft.getInstance();
        // Open after ChatScreen has finished closing the command input.
        if(mc.player!=null&&mc.level!=null)mc.setScreen(new DeathPreviewScreen(mode.equals("downed")));
    }
    @Override protected void init(){if(!started){started=true;replay();}}
    private void replay(){
        skipped=overview=false;
        DeathCard.reset();DeathCard.show(card);Cinematic.clear();
        var player=minecraft.player;
        if(player!=null){
            var ahead=player.getLookAngle();
            var target=player.getEyePosition().add(ahead.x*8+ahead.z*6,.5,ahead.z*8-ahead.x*6);
            if(downed)Cinematic.downed(target);
            else Cinematic.startDeath(player.getX(),player.getEyeY(),player.getZ(),player.getYRot(),player.getXRot(),target);
        }
    }
    @Override public void tick(){
        if(minecraft.player==null||minecraft.level==null||minecraft.player.isDeadOrDying()){onClose();return;}
        var me=Client.state.has("me")?Client.state.getAsJsonObject("me"):new JsonObject();
        if(!wasDead&&me.has("alive")&&!me.get("alive").getAsBoolean()){onClose();return;}
        if(skipped&&!overview&&!DeathCard.blockingDeploy()){
            overview=true;
            var player=minecraft.player;
            Cinematic.overview(new DeployView.Placement(player.getX(),player.getY()+24,player.getZ(),
                    player.getYRot(),80,true,0,24));
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        if(!overview)DeathCard.drawPreview(g,minecraft,snapshot);
        g.drawCenteredString(font,overview?"部署镜头预览 · R 重播 · Esc 退出":"死亡界面预览 · 示例数据",width/2,12,0xffeeeeee);
    }
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==82){replay();return true;}
        if(!downed&&minecraft.options.keyJump.matches(key,scan)){skipped=true;DeathCard.release();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose(){minecraft.setScreen(null);}
    @Override public void removed(){Cinematic.clear();DeathCard.reset();Client.Events.previewClosed();}
    @Override public boolean isPauseScreen(){return false;}
}
