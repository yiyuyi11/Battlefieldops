package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Battlefield-style squad picker: a plain list of squads with occupancy, opened from the deploy bar. */
final class SquadSelectScreen extends FrontScreen {
    private final Screen parent;
    private Form list, bar;
    SquadSelectScreen(Screen parent){super("选择小队");this.parent=parent;}
    protected String subtitle(){return "选择后立即加入 · 待部署期间可切换";}
    protected void rebuildContent(){
        clearWidgets();
        list=newForm(Ui.headerBody(layout.left));
        int current=currentSquad();
        list.caption("当前：" + SquadRules.label(current), Ui.ACCENT);
        var leave=place(list, list.take(Layout.ROW), "退出小队", current==0?"当前":"", "", "", current!=0, ()->join(0));
        leave.selected=current==0;
        if(Client.state.has("squads")) for(var item : Client.state.getAsJsonArray("squads")){
            JsonObject one=item.getAsJsonObject();
            int id=Engine.num(one,"id",0), count=Engine.num(one,"count",0);
            String members=Engine.str(one,"members","");
            // Full squads stay visible but disabled, like Battlefield's greyed-out entries.
            var row=place(list, list.take(Layout.ROW), SquadRules.label(id), count+"/4 · "+members, "", "",
                    count<SquadRules.CAPACITY||id==current, ()->join(id));
            row.selected=id==current;
        }
        list.finish();
        bar=newForm(layout.bar.inset(2));
        var cells=Layout.columns(layout.bar.inset(2),2,4);
        place(bar,cells[0],"返回部署","","","",true,this::onClose);
        place(bar,cells[1],"刷新","","","",true,()->Client.send(Client.request("lobby","")));
        bar.finish();
    }
    private static int currentSquad(){
        var state=Client.state;
        if(state==null||!state.has("me"))return 0;
        return Engine.num(state.getAsJsonObject("me"),"squad",0);
    }
    private void join(int id){
        var request=Client.request("squad",Client.currentMap());
        request.addProperty("squad",id);
        Client.send(request);
        minecraft.setScreen(parent);
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){
        Ui.header(g,font,layout.left,"小队列表");
        list.render(g,x,y,dt);bar.render(g,x,y,dt);
    }
    public boolean mouseScrolled(double x,double y,double h,double v){
        if(dropdownOpen())return super.mouseScrolled(x,y,h,v);
        return list.wheel(x,y,v)||super.mouseScrolled(x,y,h,v);
    }
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
