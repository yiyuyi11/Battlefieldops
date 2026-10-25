package cn.bfops;
import java.util.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Equipment-specific workbench: compatible slots, large native preview, explicit apply/cancel. */
final class FittingScreen extends FrontScreen {
    private final Screen parent;
    private final String map,id,kind;
    private final Map<String,String> target,working;
    private List<Fittings.Group> groups=List.of();
    private boolean requested,loaded;
    private Form slots,bar;
    private long sentAt;
    FittingScreen(Screen parent,String map,String id,String kind,Map<String,String> target){
        super(kind.equals("gun")?"武器改装台":"载具弹药配置");this.parent=parent;this.map=map;this.id=id;this.kind=kind;
        this.target=target;working=new LinkedHashMap<>(target);
    }
    protected String subtitle(){return "兼容项自动筛选 · 部署时应用";}
    private void fetch(){
        requested=true;sentAt=System.currentTimeMillis();
        var req=Client.request("fittings",map);req.addProperty("id",id);req.addProperty("kind",kind);Client.send(req);say("正在读取装备兼容项…");
    }
    void receive(JsonObject data){
        if(!id.equals(Engine.str(data,"id",""))||!kind.equals(Engine.str(data,"kind","")))return;
        groups=Arrays.asList(BattlefieldOps.GSON.fromJson(data.get("groups"),Fittings.Group[].class));loaded=true;
        ok(groups.isEmpty()?"此装备没有可配置项目":"选择槽位查看兼容配件；完成后点击「应用配装」");rebuild();
    }
    void error(String text){fail(text);}
    protected void poll(){if(!loaded&&requested&&System.currentTimeMillis()-sentAt>8000){requested=false;warn("未收到兼容列表，请点击重试；现有配装未改变");rebuild();}}
    protected void rebuild(){
        int offset=slots==null?0:slots.scroll();clearWidgets();slots=newForm(Ui.headerBody(layout.left));
        if(!requested&&!loaded){primary(slots,"读取 / 重试",true,this::fetch);}
        if(!requested&&!loaded&&sentAt==0)fetch();
        for(var group:groups){
            String selected=working.getOrDefault(group.id(),"");
            var option=group.options().stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);
            row(slots,group.name(),option==null?"默认":option.name(),option==null?"":option.kind(),option==null?"":option.icon(),()->{
                List<ChoiceScreen.Choice> choices=new ArrayList<>();choices.add(new ChoiceScreen.Choice("","默认 / 不加装","item",""));
                for(var entry:group.options())choices.add(new ChoiceScreen.Choice(entry.id(),entry.name(),entry.kind(),entry.icon()));
                minecraft.setScreen(new ChoiceScreen(this,group.name(),choices,value->{if(value.isEmpty())working.remove(group.id());else working.put(group.id(),value);}));
            });
        }
        slots.scrollTo(offset);slots.finish();bar=newForm(layout.bar.inset(2));var cells=Layout.columns(layout.bar.inset(2),3,3);
        place(bar,cells[0],"取消","","","",true,this::onClose);
        place(bar,cells[1],"恢复默认","","","",true,()->{working.clear();rebuild();say("已恢复默认；点击应用后生效");});
        var apply=place(bar,cells[2],"应用配装","","","",loaded,()->{
            Fittings.validate(groups,working);target.clear();target.putAll(working);minecraft.setScreen(parent);
        });apply.primary=true;bar.finish();
    }
    protected void paint(GuiGraphics g,int mx,int my,float dt){
        Ui.header(g,font,layout.left,kind.equals("gun")?"配件槽位":"各武器站弹种");
        var pane=Ui.header(g,font,layout.right,"装备预览");
        AssetIcons.draw(g,kind,id,pane.x()+10,pane.y()+12,pane.w()-20,Math.max(24,pane.h()-112));
        int tile=26,step=30,shown=0;
        for(var group:groups){
            String selected=working.getOrDefault(group.id(),"");
            var option=group.options().stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);
            if(option==null||shown>=Math.max(1,(pane.w()-16)/step))continue;
            int x=pane.x()+8+shown++*step,y=pane.bottom()-80;
            Ui.sunken(g,new Layout.Rect(x,y,tile,tile));AssetIcons.draw(g,option.kind(),option.icon(),x+3,y+3,20,20);
            if(mx>=x&&mx<x+tile&&my>=y&&my<y+tile)TacticalColors.label(g,font,option.name(),pane.cx(),y-12,Ui.ACCENT);
        }
        TacticalColors.label(g,font,"已选 "+working.size()+" 项 · 下次部署应用",pane.cx(),pane.bottom()-44,Ui.ACCENT);
        if(kind.equals("vehicle"))TacticalColors.label(g,font,"各武器站备弹自动补齐",pane.cx(),pane.bottom()-28,Ui.TEXT_DIM);
        slots.render(g,mx,my,dt);bar.render(g,mx,my,dt);
    }
    public boolean mouseScrolled(double x,double y,double h,double v){return slots.wheel(x,y,v)||super.mouseScrolled(x,y,h,v);}
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
