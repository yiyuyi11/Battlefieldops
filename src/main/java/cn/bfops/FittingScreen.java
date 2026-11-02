package cn.bfops;
import java.util.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Equipment-specific workbench: compatible slots, large native preview, explicit apply/cancel. */
final class FittingScreen extends FrontScreen {
    private final Screen parent;
    private final String map,id,kind,kit;
    private final Map<String,String> locks=new HashMap<>();
    private int level;
    private final Map<String,String> target,working;
    private List<Fittings.Group> groups=List.of();
    private boolean requested,loaded;
    private Form slots,bar;
    private long sentAt;
    /** Drag-controlled rotation for the gun model on the bench. */
    private float modelYaw=30,modelPitch=-15;
    FittingScreen(Screen parent,String map,String id,String kind,Map<String,String> target){
        this(parent,map,id,kind,target,Client.state.has("me")?Engine.str(Client.state.getAsJsonObject("me"),"kit",""):"");
    }
    FittingScreen(Screen parent,String map,String id,String kind,Map<String,String> target,String kit){
        super(kind.equals("gun")?"武器改装台":"载具弹药配置");this.parent=parent;this.map=map;this.id=id;this.kind=kind;this.kit=kit;
        this.target=target;working=new LinkedHashMap<>(target);
    }
    protected String subtitle(){return "兼容项自动筛选 · 部署时应用";}
    private void fetch(){
        requested=true;sentAt=System.currentTimeMillis();
        var req=Client.request("fittings",map);req.addProperty("id",id);req.addProperty("kind",kind);req.addProperty("kit",kit);Client.send(req);say("正在读取装备兼容项…");
    }
    void receive(JsonObject data){
        if(!map.equals(Engine.str(data,"map",map)))return;
        if(!id.equals(Engine.str(data,"id",""))||!kind.equals(Engine.str(data,"kind","")))return;
        if(!kit.equals(Engine.str(data,"kit",kit)))return;
        locks.clear();if(data.has("locks"))data.getAsJsonObject("locks").entrySet().forEach(e->locks.put(e.getKey(),e.getValue().getAsString()));level=Engine.num(data,"level",0);
        groups=Arrays.asList(BattlefieldOps.GSON.fromJson(data.get("groups"),Fittings.Group[].class));loaded=true;
        ok(groups.isEmpty()?"此装备没有可配置项目":"选择槽位查看兼容配件；完成后点击「应用配装」");rebuild();
    }
    void error(String text){fail(text);}
    protected void poll(){if(!loaded&&requested&&System.currentTimeMillis()-sentAt>8000){requested=false;warn("未收到兼容列表，请点击重试；现有配装未改变");rebuild();}}
    protected void rebuildContent(){
        int offset=slots==null?0:slots.scroll();clearWidgets();slots=newForm(Ui.headerBody(layout.left));
        if(!requested&&!loaded){primary(slots,"读取 / 重试",true,this::fetch);}
        if(!requested&&!loaded&&sentAt==0)fetch();
        for(var group:groups){
            String selected=working.getOrDefault(group.id(),"");
            var option=group.options().stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);
            row(slots,group.name(),option==null?"默认":GearNames.display(option.kind(),option.icon(),option.name()),option==null?"":option.kind(),option==null?"":option.icon(),()->{
                List<ChoiceScreen.Choice> choices=new ArrayList<>();choices.add(new ChoiceScreen.Choice("","默认 / 不加装","item",""));
                for(var entry:group.options())choices.add(new ChoiceScreen.Choice(entry.id(),GearNames.display(entry.kind(),entry.icon(),entry.name())+(locks.containsKey(entry.id())?" · "+locks.get(entry.id()):""),entry.kind(),entry.icon()));
                minecraft.setScreen(new ChoiceScreen(this,group.name(),choices,value->{if(locks.containsKey(value)){fail(locks.get(value));return;}if(value.isEmpty())working.remove(group.id());else working.put(group.id(),value);},(g,body,choice)->{
                    var candidate=new LinkedHashMap<>(working);if(choice.id().isEmpty())candidate.remove(group.id());else candidate.put(group.id(),choice.id());
                    EquipmentStatPanel.preview(g,font,body,kind,id,choice.name(),candidate);
                }));
            });
        }
        slots.scrollTo(offset);slots.finish();bar=newForm(layout.bar.inset(2));var cells=Layout.columns(layout.bar.inset(2),4,3);
        place(bar,cells[0],"取消","","","",true,this::onClose);
        place(bar,cells[1],"恢复默认","","","",true,()->{working.clear();rebuild();say("已恢复默认；点击应用后生效");});
        place(bar,cells[2],"完整属性图表","","","",loaded,()->minecraft.setScreen(new EquipmentStatsScreen(this,kind,id,working)));
        var apply=place(bar,cells[3],"应用配装","","","",loaded,()->{
            for(String id:working.values())if(locks.containsKey(id)){fail(locks.get(id)+"；请移除该配件");return;}
            Fittings.validate(groups,working);target.clear();target.putAll(working);LoadoutProfiles.save();minecraft.setScreen(parent);
        });apply.primary=true;bar.finish();
    }
    protected void paint(GuiGraphics g,int mx,int my,float dt){
        Ui.header(g,font,layout.left,(kind.equals("gun")?"配件槽位 ":"武器站弹种 ")+working.size()+"/"+groups.size());
        var pane=Ui.header(g,font,layout.right,GearNames.display(kind,id,id)+" · 预览");
        EquipmentStatPanel.preview(g,font,new Layout.Rect(pane.x(),pane.y(),pane.w(),Math.max(32,pane.h()-18)),kind,id,GearNames.display(kind,id,id),working,modelYaw,modelPitch);
        TacticalColors.label(g,font,(map.isEmpty()?"大厅全解锁":"等级 "+level)+" · 已选 "+working.size()+" 项",pane.cx(),pane.bottom()-12,Ui.ACCENT);
        slots.render(g,mx,my,dt);bar.render(g,mx,my,dt);
    }
    public boolean mouseScrolled(double x,double y,double h,double v){if(dropdownOpen())return super.mouseScrolled(x,y,h,v);return slots.wheel(x,y,v)||super.mouseScrolled(x,y,h,v);}
    @Override
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        // Drag the gun model: horizontal turns it, vertical tilts it. Same feel as TaCZ's refit screen.
        if(button==0&&!dropdownOpen()){
            if(x>=layout.right.x()&&x<layout.right.right()&&y>=layout.right.y()&&y<layout.right.bottom()){
                modelYaw+=(float)(dx*0.6);
                modelPitch=(float)Math.max(-80,Math.min(80,modelPitch+dy*0.4));
                return true;
            }
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
