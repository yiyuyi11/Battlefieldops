package cn.bfops;

import java.nio.file.Files;
import java.util.*;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import cn.bfops.killicon.client.ClientSetup;
import cn.bfops.killicon.client.ClientEvents;
import cn.bfops.killicon.client.config.*;
import cn.bfops.killicon.client.gui.MainConfigScreen;
import cn.bfops.killicon.client.gui.elements.ElementPreview;
import cn.bfops.killicon.client.gui.tabs.ScoreboardTab;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.impl.*;
import cn.bfops.killicon.client.sounds.SoundTriggerManager;
import cn.bfops.killicon.common.bonus.BonusRegistry;
import cn.bfops.killicon.network.PacketContext;
import cn.bfops.killicon.network.packet.*;

/** Client-only adapter: original 656 presentation consumes server-authored room events. */
public final class NativeKillIcons {
    private static boolean ready;
    private static String lastPreset;
    private static long legacyKillAt;private static int legacyCombo;
    private static final PacketContext CONTEXT=new PacketContext(){
        public void enqueueWork(Runnable action){action.run();}
        public void setPacketHandled(boolean value){}
        public net.minecraft.server.level.ServerPlayer getSender(){return null;}
    };
    public static void init(){
        if(ready)return;
        boolean migrate=!Files.exists(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("battlefield_ops/client_config.json"));
        ClientSetup.initializeClient();ready=true;
        if(migrate){ConfigManager.setCurrentPresetId(ClientPrefs.killIconPreset());ClientConfigManager.saveGlobalConfig();}
        lastPreset=ConfigManager.getCurrentPresetId();
    }
    public static boolean inMatch(){return Client.state!=null&&Client.state.has("inMatch")&&Client.state.get("inMatch").getAsBoolean();}
    /**
     * How many rows the current server snapshot is publishing for the room this client is in.
     *
     * <p>The only honest test of "is there a live roster right now". {@code inMatch} is not the same
     * question: a player can be in a room that has not started (so no match, but the roster is pushed),
     * or in a match whose scores array is still empty. The 656 tab needs the row count because a live
     * roster of zero is not evidence of anything and must not overwrite this client's own record.
     */
    public static int liveRosterSize(){
        if(Client.state==null||!Client.state.has("scores"))return 0;
        var scores=Client.state.get("scores");
        return scores.isJsonArray()?scores.getAsJsonArray().size():0;
    }
    public static boolean sfxEnabled(){return ClientPrefs.sfxEnabled();}
    public static void open(Screen parent){init();Minecraft.getInstance().setScreen(new MainConfigScreen(parent));}
    /**
     * Opens the 656 leaderboard, at any time.
     *
     * <p>This used to be wrapped in {@code if (inMatch())}, so the page silently did nothing unless the
     * player was mid-round: the menu entry looked live, was clicked, and no window appeared. The
     * leaderboard is a reference feature available from the lobby, from the deploy screen and from the
     * post-match summary, so the only state it needs is "this client is in a world" — which the caller
     * has already established by reaching this method. Outside a live room it shows this client's own
     * record of the server and says so; see {@link LeaderboardScope}.
     */
    public static void openScoreboard(Screen parent){init();ScoreboardTab.careerScope(!inMatch());Minecraft.getInstance().setScreen(new MainConfigScreen(parent,3,false));}
    public static void deathStats(String killer){init();new DeathPacket("","",killer).handle(CONTEXT);}
    public static void tick(){
        if(!ready)return;
        String chosen=ConfigManager.getCurrentPresetId();
        if(!Objects.equals(lastPreset,chosen)){reset();lastPreset=chosen;ClientPrefs.setKillIconPreset(chosen);}
        ClientEvents.onClientTickEnd();
    }
    public static void preset(String id){init();reset();ConfigManager.setCurrentPresetId(id);ClientConfigManager.saveGlobalConfig();lastPreset=id;}
    public static void reset(){if(ready){HudElementManager.clearAllPreviews();KillIconPacket.clearPending();}}
    public static void logout(){reset();if(ready){ClientEvents.onClientPlayerLogout();ScoreboardTab.clearRoomData();}}
    public static void death(){if(ready)ComboSubtitleRenderer.getInstance().onPlayerDeath();}
    public static void receive(JsonObject event){
        if(!cn.bfops.ClientPrefs.killIcons())return;
        init();
        String icon=Engine.str(event,"icon","kill");
        int kind=FeedbackTypes.killType(icon);
        boolean cosmetic=event.has("cosmetic")&&event.get("cosmetic").getAsBoolean();
        boolean elimination=event.has("elimination")?event.get("elimination").getAsBoolean():cosmetic;
        boolean showIcon=elimination||(event.has("killFeed")&&event.get("killFeed").getAsBoolean());
        int points=Engine.num(event,"points",0),victim=Engine.num(event,"victimId",-1),combo=Engine.num(event,"combo",1);
        if(elimination&&kind<=3&&!event.has("combo")){long now=System.currentTimeMillis();legacyCombo=now-legacyKillAt<=5000?legacyCombo+1:1;legacyKillAt=now;combo=legacyCombo;}
        String detail=Engine.str(event,"detail","");
        if(event.has("honorId")){new HonorPacket(Engine.str(event,"honorId",""),Engine.str(event,"honorStyle","")).handle(CONTEXT);return;}
        if(showIcon){
            double comboWindow=event.has("comboSeconds")?event.get("comboSeconds").getAsDouble():5;
            if(elimination&&kind<=3)cn.bfops.killicon.client.stats.ClientStatsManager.recordGeneralKillStats(detail,!cosmetic);
            for(String element:ConfigManager.getElementIds(ConfigManager.getCurrentPresetId())){
                if(!element.startsWith("kill_icon/")||element.equals("kill_icon/honor"))continue;
                new KillIconPacket("kill_icon",element.substring(10),kind,combo,victim,comboWindow,
                        event.has("helmet")&&event.get("helmet").getAsBoolean(),detail,
                        !cosmetic,false,event.has("distance")?event.get("distance").getAsFloat():0).handle(CONTEXT);

            }
            new KillIconPacket("subtitle","kill_feed",kind,combo,victim,comboWindow,false,detail,!cosmetic,false,
                    event.has("distance")?event.get("distance").getAsFloat():0,points,1).handle(CONTEXT);
            if(elimination||kind==4)HudElementManager.trigger("subtitle","combo",cn.bfops.killicon.client.render.IHudRenderer.TriggerContext.of(kind,victim,combo));
            if(elimination){HitInfoRenderer.getInstance().markKill(victim);if(event.has("distance"))cn.bfops.killicon.client.stats.ClientStatsManager.recordKillDistance(event.get("distance").getAsDouble());}
        }
        if(!cosmetic){
            String id=Engine.str(event,"bonusId",FeedbackTypes.bonusId(Engine.str(event,"label",""),icon));
            var definition=BonusRegistry.get(id);
            if(points==0){
                if(definition!=null)switch(definition.statCategory()){
                    case ASSIST -> cn.bfops.killicon.client.stats.ClientStatsManager.recordAssist();
                    case REVIVE -> cn.bfops.killicon.client.stats.ClientStatsManager.recordRevive();
                    default -> {}
                }
            }else if(definition!=null)new BonusScorePacket(definition.type(),points,Engine.str(event,"extra",id.equals("KILL_COMBO")?Integer.toString(combo):""),victim,detail).handle(CONTEXT);
            else ScoreSubtitleRenderer.getInstance().addScore(points);
        }
    }
    public static void hit(JsonObject event){
        init();if(event.has("damage"))cn.bfops.killicon.client.stats.ClientStatsManager.recordDamage(Math.max(0,event.get("damage").getAsDouble()));new HitInfoPacket(event.has("damage")?event.get("damage").getAsFloat():0,false,Engine.num(event,"victimId",-1)).handle(CONTEXT);
        if(!event.has("nativeMarker")||!event.get("nativeMarker").getAsBoolean())SoundTriggerManager.playHitSound();
    }
    /**
     * Saves the current room's roster to this client's own record, just before the room is thrown away.
     *
     * <p>Without this, the record only existed if the player had opened the leaderboard during the
     * round: {@link #scoreboard} is what populated it, and a player who never opened the tab arrived at
     * the post-match summary with nothing saved. The roster is in the last state push, so it is read
     * from there.
     *
     * <p>Called from {@code Client.receive} <em>before</em> the new state replaces the old one — after
     * that point the scores are gone and there is nothing left to save.
     */
    public static void archiveRoom(){
        try{
            if(liveRosterSize()<=0)return;
            var rows=new ArrayList<ScoreboardSyncPacket.Entry>();
            String map=Engine.str(Client.state,"selected","");
            for(var item:Client.state.getAsJsonArray("scores")){
                var row=item.getAsJsonObject();String name=Engine.str(row,"name","");
                var connection=Minecraft.getInstance().getConnection();var info=connection==null?null:connection.getPlayerInfo(name);
                UUID uuid=info==null?UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)):info.getProfile().getId();
                rows.add(new ScoreboardSyncPacket.Entry(uuid,name,name,Board.teamName(map,Engine.num(row,"team",0)),SquadRules.label(Engine.num(row,"squad",0)),
                        Engine.num(row,"score",0),Engine.num(row,"kills",0),Engine.num(row,"deaths",0),Engine.num(row,"assists",0),Engine.num(row,"revives",0),info==null?0:info.getLatency(),true,false));
            }
            ScoreboardTab.archive(rows);
        }catch(RuntimeException|LinkageError error){
            cn.bfops.BattlefieldOps.LOG.warn("Cannot archive the room roster",error);
        }
    }
    public static void careerLeaderboard(JsonObject payload){
        if(inMatch())return;
        var rows=new ArrayList<ScoreboardSyncPacket.Entry>();
        for(var item:payload.getAsJsonArray("rows")){
            var e=item.getAsJsonObject();String name=Engine.str(e,"name","");boolean online=e.get("online").getAsBoolean();
            rows.add(new ScoreboardSyncPacket.Entry(UUID.fromString(Engine.str(e,"uuid","")),name,name,online?"在线玩家":"离线玩家","",bounded(e,"score"),bounded(e,"kills"),bounded(e,"deaths"),bounded(e,"assists"),bounded(e,"revives"),Engine.num(e,"ping",0),online,false));
        }
        ScoreboardTab.careerScope(true);
        ScoreboardTab.updateData(rows,Engine.num(payload,"offset",0),Engine.num(payload,"total",0),payload.get("requestId").getAsLong(),2,new String[]{"在线玩家","离线玩家","",""});
    }
    private static int bounded(JsonObject e,String key){return (int)cn.bfops.Java17.clamp(e.get(key).getAsLong(),0,Integer.MAX_VALUE);}
    public static void scoreboard(ScoreboardRequestPacket request){
        ScoreboardTab.careerScope(!inMatch());
        if(!inMatch()){
            var q=Client.request("leaderboard","");q.addProperty("offset",request.offset);q.addProperty("limit",request.limit);q.addProperty("requestId",request.requestId);Client.send(q);return;
        }
        var rows=new ArrayList<ScoreboardSyncPacket.Entry>();String map="";
        // Keyed on "the server is publishing a roster", not on "a match is running". A room fills up and
        // shows standings before the round starts, and gating on inMatch meant those rows existed in the
        // snapshot and were withheld from the leaderboard anyway.
        if(liveRosterSize()>0){
            map=Engine.str(Client.state,"selected","");
            for(var item:Client.state.getAsJsonArray("scores")){
                var row=item.getAsJsonObject();String name=Engine.str(row,"name","");
                var connection=Minecraft.getInstance().getConnection();var info=connection==null?null:connection.getPlayerInfo(name);
                UUID uuid=info==null?UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)):info.getProfile().getId();
                rows.add(new ScoreboardSyncPacket.Entry(uuid,name,name,Board.teamName(map,Engine.num(row,"team",0)),SquadRules.label(Engine.num(row,"squad",0)),
                        Engine.num(row,"score",0),Engine.num(row,"kills",0),Engine.num(row,"deaths",0),Engine.num(row,"assists",0),Engine.num(row,"revives",0),info==null?0:info.getLatency(),true,false));
            }
        }
        int start=Math.min(request.offset,rows.size()),end=Math.min(rows.size(),start+request.limit);
        ScoreboardTab.updateData(rows.subList(start,end),start,rows.size(),request.requestId,2,new String[]{Board.teamName(map,0),Board.teamName(map,1),"",""});
    }
    public static void draw(GuiGraphics graphics){
        if(!ready)return;
        if(!cn.bfops.ClientPrefs.killIcons())return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        boolean vehicle=VehicleUi.active();
        graphics.pose().pushPose();
        try{
            if(vehicle){
                AwardLayout.Rect slot=AwardLayout.place(graphics.guiWidth(),graphics.guiHeight(),true,2,List.of());
                if(slot==null)return;
                int[] bounds=visualBounds(graphics.guiWidth(),graphics.guiHeight());
                float scale=Math.min(1,Math.min(slot.w()/(float)Math.max(1,bounds[1]-bounds[0]),slot.h()/(float)Math.max(1,bounds[3]-bounds[2])));
                graphics.enableScissor(slot.x(),slot.y(),slot.x()+slot.w(),slot.y()+slot.h());
                graphics.pose().translate(slot.x()+slot.w()/2f,slot.y()+slot.h()/2f,0);
                graphics.pose().scale(scale,scale,1);
                graphics.pose().translate(-(bounds[0]+bounds[1])/2f,-(bounds[2]+bounds[3])/2f,0);
            }
            HudElementManager.onRenderGuiOverlay(graphics,mc.getFrameTime(),true);
        }finally{
            graphics.flush();if(vehicle)graphics.disableScissor();graphics.pose().popPose();
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.enableCull();
        }
    }
    private static int boundsHash;private static int[] cachedBounds;
    private static int[] visualBounds(int width,int height){
        String preset=ConfigManager.getCurrentPresetId();int hash=Objects.hash(preset,width,height);var elements=ConfigManager.getElementIds(preset);
        for(String id:elements)hash=hash*31+Objects.hashCode(ElementConfigManager.getElementConfig(preset,id));
        if(cachedBounds!=null&&hash==boundsHash)return cachedBounds;
        int minX=width,maxX=0,minY=height,maxY=0;
        for(String id:elements){var config=ElementConfigManager.getElementConfig(preset,id);if(config==null||(config.has("visible")&&!config.get("visible").getAsBoolean()))continue;
            var preview=new ElementPreview(id);preview.updateConfig(config);preview.updatePosition(width,height);int[] box=preview.visualBounds();
            minX=Math.min(minX,box[0]-48);maxX=Math.max(maxX,box[1]+48);minY=Math.min(minY,box[2]-32);maxY=Math.max(maxY,box[3]+32);
        }
        boundsHash=hash;return cachedBounds=new int[]{minX,maxX,minY,maxY};
    }
}
