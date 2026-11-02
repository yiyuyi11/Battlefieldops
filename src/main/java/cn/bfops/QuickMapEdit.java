package cn.bfops;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="battlefield_ops",value=Dist.CLIENT)
public final class QuickMapEdit {
    private static OpsScreen editor;private static boolean active,boundaryStarted;
    private static int tool;
    private record Undo(String arena,boolean boundary){}
    private static final Deque<Undo> undo=new ArrayDeque<>();
    private static final String[] TOOLS={"据点","机械轴心出生点","格雷同盟出生点","补给点","精英点","新边界顶点"};
    static void start(OpsScreen parent){editor=parent;tool=0;boundaryStarted=false;undo.clear();Client.send(Client.request("editorStart",parent.quickDraft().id));}
    static void receive(boolean value){
        if(!value||editor==null)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().toString().equals(editor.quickDraft().dimension)){end(false);return;}
        active=true;Cinematic.clear();mc.setScreen(null);
    }
    static void end(boolean save){
        if(editor==null)return;var parent=editor;active=false;editor=null;undo.clear();
        Client.send(Client.request("editorEnd",""));Minecraft.getInstance().setScreen(parent);if(save)parent.quickSave();
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
        if(!active)return;var mc=Minecraft.getInstance();
        if(mc.player==null||!Client.serverSupported()){active=false;editor=null;undo.clear();return;}
        if(mc.screen!=null&&!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen))end(false);
    }
    @SubscribeEvent public static void key(net.neoforged.neoforge.client.event.InputEvent.Key e){
        if(!active||e.getAction()!=GLFW.GLFW_PRESS||Minecraft.getInstance().screen!=null)return;
        if(e.getKey()>=GLFW.GLFW_KEY_1&&e.getKey()<=GLFW.GLFW_KEY_6)tool=e.getKey()-GLFW.GLFW_KEY_1;
        if(e.getKey()==GLFW.GLFW_KEY_S&&(e.getModifiers()&GLFW.GLFW_MOD_CONTROL)!=0)end(true);
        if(e.getKey()==GLFW.GLFW_KEY_M){var parent=editor;end(false);parent.chooseEditingMap();}
    }
    @SubscribeEvent public static void mouse(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre e){
        var mc=Minecraft.getInstance();if(!active||mc.screen!=null)return;
        if(e.getButton()>2)return;e.setCanceled(true);if(e.getAction()!=GLFW.GLFW_PRESS)return;
        if(e.getButton()==2){tool=(tool+1)%TOOLS.length;return;}
        if(e.getButton()==1){if(!undo.isEmpty()){var before=undo.removeLast();editor.quickRestore(before.arena);boundaryStarted=before.boundary;}return;}
        Vec3 start=mc.player.getEyePosition();var hit=mc.level.clip(new ClipContext(start,start.add(mc.player.getLookAngle().scale(192)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.BLOCK){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("请瞄准已加载的地面或方块"),true);return;}
        try{
            String before=BattlefieldOps.GSON.toJson(editor.quickDraft());boolean old=boundaryStarted;
            var pos=hit.getBlockPos();var shape=mc.level.getBlockState(pos).getCollisionShape(mc.level,pos);
            double top=shape.isEmpty()?1:shape.max(net.minecraft.core.Direction.Axis.Y);
            editor.quickPlace(tool,new Vec3(pos.getX()+.5,pos.getY()+top+.02,pos.getZ()+.5),tool==5&&!boundaryStarted);
            undo.addLast(new Undo(before,old));while(undo.size()>32)undo.removeFirst();if(tool==5)boundaryStarted=true;
        }catch(RuntimeException ex){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(FrontScreen.describe(ex)),true);}
    }
    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event){
        if(!active||editor==null)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;
        GuiGraphics g=event.getGuiGraphics();var map=editor.quickDraft();var camera=Projector.camera(mc);if(camera==null)return;
        var lines=new ArrayList<int[]>();for(int i=0;i<map.boundary.size();i++){var a=map.boundary.get(i);var b=map.boundary.get((i+1)%map.boundary.size());var p=Projector.project(mc,camera,a.x(),mc.player.getY()-2,a.z(),1);var q=Projector.project(mc,camera,b.x(),mc.player.getY()-2,b.z(),1);if(p!=null&&q!=null&&Math.abs(p.x())<g.guiWidth()*3&&Math.abs(q.x())<g.guiWidth()*3&&Math.abs(p.y())<g.guiHeight()*3&&Math.abs(q.y())<g.guiHeight()*3)lines.add(new int[]{(int)p.x(),(int)p.y(),(int)q.x(),(int)q.y()});}BatchLine.polyline(g,lines,2,Ui.ACCENT);
        for(var point:map.points){var at=Projector.project(mc,camera,point.x,point.y+2,point.z,1);if(at!=null)MapIcons.capture(g,mc.font,point.id,(int)at.x(),(int)at.y(),50,-1,0,false,0);}
        for(var spawn:map.spawns){var at=Projector.project(mc,camera,spawn.x,spawn.y+2,spawn.z,1);if(at!=null){MapIcons.site(g,mc.font,spawn.air()?"AIR":"BASE",(int)at.x(),(int)at.y(),false,0);g.drawCenteredString(mc.font,spawn.id,(int)at.x(),(int)at.y()+9,spawn.team==0?Ui.BLUE:Ui.RED);}}
        for(var pad:map.supplies){var at=Projector.project(mc,camera,pad.x(),pad.y()+2,pad.z(),1);if(at!=null)SupportMarkers.drawIcon(g,"ammo_box",(int)at.x(),(int)at.y(),7);}
        for(var elite:map.eliteSpawns){var at=Projector.project(mc,camera,elite.x,elite.y+2,elite.z,1);if(at!=null)HeroIcons.draw(g,elite.type,at.x(),at.y(),12,1);}
        g.fill(8,8,Math.min(g.guiWidth()-8,480),43,0xb010181f);
        g.drawString(mc.font,"编辑 "+map.name+" · "+TOOLS[tool],14,13,Ui.ACCENT,true);
        g.drawString(mc.font,"1–6 选工具 · 左键放置 · 右键撤销 · 中键切换",14,25,Ui.TEXT,true);
        g.fill(8,g.guiHeight()-22,g.guiWidth()-8,g.guiHeight()-7,0xb010181f);
        g.drawString(mc.font,"M 选择地图 · Ctrl+S 返回并保存 · Esc 返回编辑器 · 草稿尚未自动保存",14,g.guiHeight()-19,Ui.TEXT,true);
    }
    private QuickMapEdit(){}
}
