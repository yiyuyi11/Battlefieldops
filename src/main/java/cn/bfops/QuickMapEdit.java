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
    private static OpsScreen editor;
    private static boolean active, moving;
    private static int tool, selected = -1;
    private record Marker(int index, String name, Vec3 position) {}
    private static final Deque<String> undo=new ArrayDeque<>();
    private static final String[] TOOLS={"据点","蓝方出生点","红方出生点","补给点","精英点","边界顶点"};
    static void start(OpsScreen parent){editor=parent;tool=0;moving=false;selected=-1;undo.clear();Client.send(Client.request("editorStart",parent.quickDraft().id));}
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
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        if(!active)return;var mc=Minecraft.getInstance();
        if(mc.player==null||!Client.serverSupported()){active=false;editor=null;undo.clear();return;}
        if(mc.screen!=null&&!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen))end(false);
    }
    @SubscribeEvent public static void key(net.neoforged.neoforge.client.event.InputEvent.Key e){
        if(!active||e.getAction()!=GLFW.GLFW_PRESS||Minecraft.getInstance().screen!=null)return;
        if(e.getKey()>=GLFW.GLFW_KEY_1&&e.getKey()<=GLFW.GLFW_KEY_6){tool=e.getKey()-GLFW.GLFW_KEY_1;selected=-1;}
        if(e.getKey()==GLFW.GLFW_KEY_V){moving=!moving;selected=-1;}
        if(e.getKey()==GLFW.GLFW_KEY_S&&(e.getModifiers()&GLFW.GLFW_MOD_CONTROL)!=0)end(true);
        if(e.getKey()==GLFW.GLFW_KEY_M){var parent=editor;end(false);parent.chooseEditingMap();}
    }
    @SubscribeEvent public static void mouse(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre e){
        var mc=Minecraft.getInstance();if(!active||editor==null||mc.screen!=null||mc.player==null||mc.level==null)return;
        if(e.getButton()>2)return;e.setCanceled(true);if(e.getAction()!=GLFW.GLFW_PRESS)return;
        if(e.getButton()==2){tool=(tool+1)%TOOLS.length;selected=-1;return;}
        if(e.getButton()==1){
            if(selected>=0)selected=-1;
            else if(!undo.isEmpty())editor.quickRestore(undo.removeLast());
            return;
        }
        if(moving&&selected<0){
            Marker target=aimed(mc, markers(mc, editor.quickDraft()));
            if(target!=null)selected=target.index();
            else mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("请用准星对准同类点位图标"),true);
            return;
        }
        Vec3 start=mc.player.getEyePosition();var hit=mc.level.clip(new ClipContext(start,start.add(mc.player.getLookAngle().scale(192)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.BLOCK){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("请瞄准已加载的地面或方块"),true);return;}
        try{
            String before=BattlefieldOps.GSON.toJson(editor.quickDraft());
            var pos=hit.getBlockPos();var shape=mc.level.getBlockState(pos).getCollisionShape(mc.level,pos);
            double top=shape.isEmpty()?1:shape.max(net.minecraft.core.Direction.Axis.Y);
            var destination=new Vec3(pos.getX()+.5,pos.getY()+top+.02,pos.getZ()+.5);
            if(moving)editor.quickMove(tool,selected,destination);
            else editor.quickPlace(tool,destination);
            selected=-1;
            undo.addLast(before);while(undo.size()>32)undo.removeFirst();
        }catch(RuntimeException ex){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(FrontScreen.describe(ex)),true);}
    }
    private static Vec3 boundaryAnchor(Minecraft mc, Model.Vec point) {
        int x=net.minecraft.util.Mth.floor(point.x()), z=net.minecraft.util.Mth.floor(point.z());
        double y=mc.level.hasChunk(x>>4,z>>4)
                ? mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)
                : mc.player.getY()-2;
        return new Vec3(point.x(),y+.3,point.z());
    }
    private static List<Marker> markers(Minecraft mc, Model.Arena map) {
        var result=new ArrayList<Marker>();
        switch(tool) {
            case 0 -> {for(int i=0;i<map.points.size();i++){var p=map.points.get(i);result.add(new Marker(i,p.id,new Vec3(p.x,p.y+2,p.z)));}}
            case 1,2 -> {for(int i=0;i<map.spawns.size();i++){var p=map.spawns.get(i);if(p.team==tool-1)result.add(new Marker(i,p.id,new Vec3(p.x,p.y+2,p.z)));}}
            case 3 -> {for(int i=0;i<map.supplies.size();i++){var p=map.supplies.get(i);result.add(new Marker(i,p.id(),new Vec3(p.x(),p.y()+2,p.z())));}}
            case 4 -> {for(int i=0;i<map.eliteSpawns.size();i++){var p=map.eliteSpawns.get(i);result.add(new Marker(i,p.id,new Vec3(p.x,p.y+2,p.z)));}}
            case 5 -> {for(int i=0;i<map.boundary.size();i++)result.add(new Marker(i,"顶点 "+(i+1),boundaryAnchor(mc,map.boundary.get(i))));}
        }
        return result;
    }
    private static Marker aimed(Minecraft mc, List<Marker> markers) {
        var camera=Projector.camera(mc);
        double cx=mc.getWindow().getGuiScaledWidth()/2.0,cy=mc.getWindow().getGuiScaledHeight()/2.0;
        double best=144, depth=Double.POSITIVE_INFINITY;
        Marker closest=null;
        for(var marker:markers) {
            var p=marker.position();
            var screen=Projector.project(mc,camera,p.x,p.y,p.z,1);
            if(screen==null||screen.depth()>192)continue;
            double dx=screen.x()-cx,dy=screen.y()-cy,distance=dx*dx+dy*dy;
            if(distance<best||(distance==best&&screen.depth()<depth)){
                best=distance;depth=screen.depth();closest=marker;
            }
        }
        return closest;
    }
    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event){
        if(!active||editor==null)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;
        GuiGraphics g=event.getGuiGraphics();var map=editor.quickDraft();var camera=Projector.camera(mc);if(camera==null)return;
        var lines=new ArrayList<int[]>();
        var boundary=map.boundary.stream().map(p->boundaryAnchor(mc,p))
                .map(p->Projector.project(mc,camera,p.x,p.y,p.z,1)).toList();
        for(int i=0;i<boundary.size();i++){
            var p=boundary.get(i);var q=boundary.get((i+1)%boundary.size());
            if(p!=null&&q!=null)lines.add(new int[]{(int)p.x(),(int)p.y(),(int)q.x(),(int)q.y()});
            if(tool==5&&p!=null)g.fill((int)p.x()-2,(int)p.y()-2,(int)p.x()+3,(int)p.y()+3,Ui.ACCENT);
        }
        BatchLine.polyline(g,lines,2,Ui.ACCENT);
        for(var point:map.points){var at=Projector.project(mc,camera,point.x,point.y+2,point.z,1);if(at!=null)MapIcons.capture(g,mc.font,point.id,(int)at.x(),(int)at.y(),50,-1,0,false,0);}
        for(var spawn:map.spawns){var at=Projector.project(mc,camera,spawn.x,spawn.y+2,spawn.z,1);if(at!=null){MapIcons.site(g,mc.font,spawn.air()?"AIR":"BASE",(int)at.x(),(int)at.y(),false,0);g.drawCenteredString(mc.font,spawn.id,(int)at.x(),(int)at.y()+9,spawn.team==0?Ui.BLUE:Ui.RED);}}
        for(var pad:map.supplies){var at=Projector.project(mc,camera,pad.x(),pad.y()+2,pad.z(),1);if(at!=null)SupportMarkers.drawIcon(g,"ammo_box",(int)at.x(),(int)at.y(),7);}
        for(var elite:map.eliteSpawns){var at=Projector.project(mc,camera,elite.x,elite.y+2,elite.z,1);if(at!=null)HeroIcons.draw(g,elite.type,at.x(),at.y(),12,1);}
        g.fill(8,8,Math.min(g.guiWidth()-8,480),43,0xb010181f);
        var targets=moving?markers(mc,map):List.<Marker>of();
        var target=selected<0?aimed(mc,targets):targets.stream().filter(p->p.index()==selected).findFirst().orElse(null);
        if(target!=null){
            var p=target.position();var screen=Projector.project(mc,camera,p.x,p.y,p.z,1);
            if(screen!=null){
                int x=(int)screen.x(),y=(int)screen.y();
                g.renderOutline(x-10,y-10,21,21,0xffffcf55);
                g.drawCenteredString(mc.font,target.name(),x,y-21,0xffffcf55);
            }
        }
        if(moving){int x=g.guiWidth()/2,y=g.guiHeight()/2;g.fill(x-3,y,x+4,y+1,Ui.TEXT);g.fill(x,y-3,x+1,y+4,Ui.TEXT);}
        g.drawString(mc.font,"编辑 "+map.name+" · "+TOOLS[tool]+" · "+(moving?"移动现有点":"放置新点"),14,13,Ui.ACCENT,true);
        String left=moving?(selected<0?"左键选点":"左键落点"):"左键放置";
        g.drawString(mc.font,"1–6 选工具 · V 切换模式 · "+left+" · 右键"+(selected<0?"撤销":"取消选中"),14,25,Ui.TEXT,true);
        g.fill(8,g.guiHeight()-22,g.guiWidth()-8,g.guiHeight()-7,0xb010181f);
        g.drawString(mc.font,"M 选择地图 · Ctrl+S 返回并保存 · Esc 返回编辑器 · 草稿尚未自动保存",14,g.guiHeight()-19,Ui.TEXT,true);
    }
    private QuickMapEdit(){}
}
