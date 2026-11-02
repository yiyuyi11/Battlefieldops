package cn.bfops;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** An isolated polygon draft: cancelling never leaves a half-edited point on the parent map. */
final class ShapeScreen extends FrontScreen {
    private final OpsScreen parent;
    private final Model.Arena arena;
    private final Model.Point point;
    private final Model.Spawn spawn;
    private final MapPicker map=new MapPicker();
    private final List<Model.Vec> vertices=new ArrayList<>();
    private Form form,bar;
    private boolean framed;
    private int dragging=-1;
    ShapeScreen(OpsScreen parent,Model.Point point,Model.Spawn spawn){
        super("自定义点位范围 · "+(point!=null?point.id:spawn.id));
        this.parent=parent;arena=parent.draft;this.point=point;this.spawn=spawn;
        var original=shape();if(original.boundary()!=null)vertices.addAll(original.boundary());
    }
    private AreaShape shape(){return point!=null?AreaShape.of(point):AreaShape.of(spawn);}
    private Layout.Rect body(){return Ui.headerBody(layout.right);}
    protected String subtitle(){return "点位范围先应用到草稿，最后在地图编辑器保存到服务器";}
    protected void rebuildContent(){
        clearWidgets();form=newForm(Ui.headerBody(layout.left));
        form.section("绘制范围 · "+vertices.size()+" / 64 顶点");
        form.paragraph("左键添加顶点，自动闭合。Shift＋左键拖动顶点；右键撤销；中键平移；滚轮缩放。",Ui.TEXT_DIM);
        row(form,"矩形起点","生成后可拖动四角",()->preset(false));
        row(form,"圆形轮廓起点","转为可编辑的 16 边形",()->preset(true));
        row(form,"撤销一个顶点","",!vertices.isEmpty(),()->{vertices.removeLast();rebuild();});
        row(form,"清空当前草稿","原范围在应用前不变",!vertices.isEmpty(),()->{vertices.clear();rebuild();});
        String error=validation();
        form.paragraph(error.isEmpty()?"范围有效，可应用后返回保存。":error,error.isEmpty()?Ui.TEXT_OK:Ui.TEXT_WARN);
        primary(form,"应用范围并返回",error.isEmpty(),this::apply);
        form.finish();
        bar=newForm(layout.bar.inset(2));place(bar,layout.bar.inset(2),"取消并返回","","","",true,this::onClose);bar.finish();
        if(!framed){
            map.frame(arena,shape().x(),shape().z());map.cx=shape().x();map.cz=shape().z();
            double[] b=shape().bounds();map.span=Math.max(48,Math.max(b[2]-b[0],b[3]-b[1])*1.6);framed=true;
        }
    }
    private void preset(boolean circle){
        vertices.clear();var s=shape();double r=Math.max(8,s.radius());int n=circle?16:4;
        if(circle)for(int i=0;i<n;i++){double a=i*2*Math.PI/n;vertices.add(new Model.Vec(Math.rint(s.x()+Math.cos(a)*r),Math.rint(s.z()+Math.sin(a)*r)));}
        else vertices.addAll(List.of(new Model.Vec(s.x()-r,s.z()-r),new Model.Vec(s.x()+r,s.z()-r),new Model.Vec(s.x()+r,s.z()+r),new Model.Vec(s.x()-r,s.z()+r)));
        rebuild();
    }
    private String validation(){
        try{
            AreaShape.validatePolygon(vertices);var s=shape();var at=AreaShape.anchor(vertices,s.x(),s.z());
            new AreaShape(at.x(),s.y(),at.z(),s.radius(),s.height(),vertices).validate(arena,"当前点位");return "";
        }catch(IllegalArgumentException ex){return ex.getMessage();}
    }
    private void apply(){
        String error=validation();Model.require(error.isEmpty(),error);
        var at=AreaShape.anchor(vertices,shape().x(),shape().z());
        if(point!=null){point.boundary=new ArrayList<>(vertices);point.x=at.x();point.z=at.z();}
        else{spawn.boundary=new ArrayList<>(vertices);spawn.x=at.x();spawn.z=at.z();}
        parent.say("点位范围已应用到草稿，记得保存地图；请检查中心高度 Y");onClose();
    }
    protected void poll(){Client.wantTerrain(arena.id);}
    protected void paint(GuiGraphics g,int mx,int my,float dt){
        Ui.header(g,font,layout.left,"点位范围");Ui.header(g,font,layout.right,"自定义轮廓 · 地形预览");
        map.showPlayers=false;map.pendingBoundary=vertices;
        var player=minecraft.player;
        map.draw(g,font,body(),arena,Client.state,-1,player==null?0:player.getX(),player==null?0:player.getZ(),0);
        for(int i=0;i<vertices.size();i++){var v=vertices.get(i);int x=map.sx(body(),v.x()),y=map.sz(body(),v.z());
            if(body().inside(x,y)){Ui.handle(g,x,y,3,Ui.GOLD);g.drawString(font,Integer.toString(i+1),x+5,y-4,Ui.GOLD,true);}}
        form.render(g,mx,my,dt);bar.render(g,mx,my,dt);
    }
    public boolean mouseClicked(double x,double y,int button){if(dropdownOpen())return super.mouseClicked(x,y,button);
        if(body().inside(x,y)){
            if(button==0){
                if(hasShiftDown())for(int i=0;i<vertices.size();i++){var v=vertices.get(i);if(Math.hypot(map.sx(body(),v.x())-x,map.sz(body(),v.z())-y)<=10){dragging=i;return true;}}
                if(vertices.size()<64){var v=new Model.Vec(Math.rint(map.worldX(body(),x)),Math.rint(map.worldZ(body(),y)));if(!vertices.contains(v))vertices.add(v);}
                rebuild();return true;
            }
            if(button==1){if(!vertices.isEmpty())vertices.removeLast();rebuild();return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dropdownOpen())return super.mouseDragged(x,y,button,dx,dy);
        if(button==0&&dragging>=0&&body().inside(x,y)){vertices.set(dragging,new Model.Vec(Math.rint(map.worldX(body(),x)),Math.rint(map.worldZ(body(),y))));return true;}
        if(button==2&&body().inside(x,y)){map.pan(body(),dx,dy);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    public boolean mouseReleased(double x,double y,int button){if(dropdownOpen())return super.mouseReleased(x,y,button);if(dragging>=0){dragging=-1;rebuild();return true;}return super.mouseReleased(x,y,button);}
    public boolean mouseScrolled(double x,double y,double h,double v){if(dropdownOpen())return super.mouseScrolled(x,y,h,v);if(body().inside(x,y)&&v!=0){map.zoom(body(),x,y,v>0);return true;}return form.wheel(x,y,v)||super.mouseScrolled(x,y,h,v);}
    public void removed(){map.close();}
    public void onClose(){minecraft.setScreen(parent);}
}
