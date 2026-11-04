package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Explicitly labelled server career view; never mixes another room into the live scoreboard. */
final class CareerScreen extends FrontScreen {
    private final Screen parent;
    private JsonObject data=new JsonObject();
    private Form ranking,personal;
    private int tab;
    private static final String[] KEYS={"score","kills","assists","revives","captures","honours"};
    protected String[] tabs(){return new String[]{"得分","击杀","助攻","救援","占领","荣誉"};}
    protected int activeTab(){return tab;}
    protected boolean onTab(int index){tab=index;refresh();return true;}
    private void refresh(){var q=Client.request("career","");q.addProperty("sort",KEYS[tab]);Client.send(q);}
    CareerScreen(Screen parent){super("服务器总榜 · 个人荣誉");this.parent=parent;}
    static void open(Screen parent){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new CareerScreen(parent));Client.send(Client.request("career",""));}
    void receive(JsonObject value){data=value;rebuild();}
    protected void rebuildContent(){
        int scroll=ranking==null?0:ranking.scroll();clearWidgets();
        ranking=newForm(Ui.headerBody(layout.left));personal=newForm(Ui.headerBody(layout.right));
        ranking.section("本世界累计排行前 100 名");
        if(data.has("rows")){int rank=0;for(var element:data.getAsJsonArray("rows")){
            JsonObject r=element.getAsJsonObject();final JsonObject selected=r;
            row(ranking,"#"+(++rank)+" "+Engine.str(r,"name",""),metric(r)+" · "+Engine.num(r,"score",0)+" 分",()->show(selected));}}
        else ranking.paragraph("等待服务器战绩…",Ui.TEXT_DIM);
        row(personal,"刷新总榜","服务端记录 · 每分钟保存及退房保存",this::refresh);
        if(data.has("self"))details(data.getAsJsonObject("self"));
        ranking.scrollTo(scroll);ranking.finish();personal.finish();
    }
    private String metric(JsonObject r){if(tab==5){int n=0;for(var h:r.getAsJsonObject("honours").entrySet())n+=h.getValue().getAsInt();return n+" 次荣誉";}return Engine.num(r,KEYS[tab],0)+" "+tabs()[tab];}
    private void show(JsonObject r){data.add("self",r);rebuild();}
    private void details(JsonObject r){
        personal.section(Engine.str(r,"name","个人记录"));
        for(String key:new String[]{"score","kills","deaths","assists","revives","captures"})personal.fact(switch(key){case "score"->"累计得分";case "kills"->"击杀";case "deaths"->"阵亡";case "assists"->"助攻";case "revives"->"救援";default->"占领";},""+Engine.num(r,key,0),Ui.ACCENT);
        personal.section("已获得的 656 荣誉");
        if(r.has("honours"))for(var h:r.getAsJsonObject("honours").entrySet())personal.fact(net.minecraft.network.chat.Component.translatable("battlefield_ops.honor."+h.getKey()+".name").getString(),h.getValue().getAsString()+" 次",Ui.TEXT);
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){
        // Solid content backdrop, same rule as the lobby: near-transparent panels are unreadable over a world.
        int top=layout.title.h()>0?layout.title.bottom():layout.tabs.bottom();
        int bottom=showBottomBar()&&layout.bar.h()>0?layout.bar.y():height;
        if(bottom>top)g.fill(0,top,width,bottom,0xe0101820);
        ranking.render(g,x,y,dt);personal.render(g,x,y,dt);
    }
    public boolean mouseScrolled(double x, double y, double v){if(dropdownOpen())return super.mouseScrolled(x, y, v);return ranking.wheel(x,y,v)||personal.wheel(x,y,v)||super.mouseScrolled(x, y, v);}
    public boolean isPauseScreen(){return false;}
    public void onClose(){minecraft.setScreen(parent);}
}
