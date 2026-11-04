package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Squad browser: inspect membership before committing a server-authoritative join. */
final class SquadSelectScreen extends FrontScreen {
    private final Screen parent;
    private Form list, bar, members;
    private int selected, poll;
    private String snapshot="";
    SquadSelectScreen(Screen parent){super("小队 · SQUADS");this.parent=parent;selected=currentSquad();}
    protected boolean actionIcons(){return false;}
    protected Layout layoutFor(int w,int h){return Layout.bf1Lobby(w,h);}
    protected void paintBackdrop(GuiGraphics g){super.paintBackdrop(g);Bf1Ui.backdrop(g,width,height);}
    protected String subtitle(){return "选择小队，查看成员后加入";}
    protected void poll(){
        // Room snapshots already refresh in Client; update both occupancy and the join gate.
        if(++poll<10)return;poll=0;
        String next=String.valueOf(Client.state.get("squads"))+String.valueOf(Client.state.get("squadRoster"))+currentSquad();
        if(!snapshot.equals(next)){snapshot=next;rebuild();}
    }
    private JsonObject chosen(){
        if(Client.state.has("squads"))for(var item:Client.state.getAsJsonArray("squads")){
            var entry=item.getAsJsonObject();if(Engine.num(entry,"id",0)==selected)return entry;
        }
        return new JsonObject();
    }
    protected void rebuildContent(){
        int scroll=list==null?0:list.scroll();clearWidgets();
        list=newForm(Ui.headerBody(layout.left));members=newForm(Ui.headerBody(layout.right));
        int current=currentSquad();
        var solo=place(list,list.take(26),"不加入小队",current==0?"当前":"","","",true,()->{selected=0;rebuild();});solo.selected=selected==0;
        if(Client.state.has("squads"))for(var item:Client.state.getAsJsonArray("squads")){
            JsonObject one=item.getAsJsonObject();int id=Engine.num(one,"id",0), count=Engine.num(one,"count",0);
            String occupancy=count+" / "+SquadRules.CAPACITY+(id==current?" · 当前":count>=SquadRules.CAPACITY?" · 已满":"");
            var row=place(list,list.take(30),SquadRules.label(id),occupancy,"bf1","squadleader_hud_default",true,()->{selected=id;rebuild();});row.selected=id==selected;
        }
        list.scrollTo(scroll);list.finish();
        var chosen=chosen();int count=Engine.num(chosen,"count",0);
        members.caption(selected==0?"单独部署":SquadRules.label(selected),Ui.TEXT);
        if(selected==0)members.paragraph("加入小队后可查看队员状态，并使用符合部署条件的小队部署点。",Ui.TEXT_DIM);
        else {
            String names=Engine.str(chosen,"members","");String[] entries=names.isBlank()?new String[0]:names.split("、");
            var roster=SquadRoster.rows(Client.state);
            if(layout.right.w()>=144 && layout.right.h()>=160)members.paint(82,(g,r)->{
                var cards=Layout.columns(r,SquadRules.CAPACITY,3);
                for(int i=0;i<cards.length;i++){
                    var card=cards[i];g.fill(card.x(),card.y(),card.right(),card.bottom()-2,0x40202428);
                    String name=i<entries.length?entries[i]:"";
                    var player=minecraft.level==null?null:minecraft.level.players().stream()
                        .filter(p->p.getGameProfile().getName().equals(name)).findFirst().orElse(null);
                    if(player!=null)Bf1Ui.icon(g,"player_hud_default",card.cx()-9,card.cy()-9,18);
                    else Bf1Ui.icon(g,"player_hud_default",card.cx()-9,card.cy()-9,18);
                }
            });
            for(int i=0;i<SquadRules.CAPACITY;i++){
                String name=i<entries.length?entries[i]:"空闲位置";
                var member=selected==current?roster.stream().filter(r->r.name().equals(name)).findFirst().orElse(null):null;
                String status=i>=entries.length?"—":member==null?"队员":member.downed()?"等待救援":member.alive()?"战斗中":"待部署";
                String icon=member!=null&&member.leader()?"squadleader_hud_default":"player_hud_default";
                Deploy.Site target=selected==current&&i<entries.length&&parent instanceof BattleScreen battle
                    ?battle.sites().stream().filter(site->site.kind().equals("TEAMMATE")&&site.label().contains(name)).findFirst().orElse(null):null;
                place(members,members.take(30),name,target==null?status:"选择小队部署点","bf1",icon,target!=null,()->{if(parent instanceof BattleScreen battle&&target!=null){battle.selectSite(target.id());minecraft.setScreen(parent);}});
            }
            members.paragraph(count>=SquadRules.CAPACITY&&selected!=current?"小队已满，请选择其他小队。":"小队部署是否可用，由队员状态与地图规则决定。",Ui.TEXT_DIM);
        }
        members.finish();bar=newForm(layout.bar.inset(1));var cells=Layout.columns(layout.bar.inset(1),2,8);
        place(bar,cells[0],"返回部署","","","",true,this::onClose);
        boolean canJoin=selected==0||selected==current||chosen.has("id")&&count<SquadRules.CAPACITY;
        var join=place(bar,cells[1],selected==current?"当前小队":selected==0?"退出小队":"加入小队","","","",canJoin&&selected!=current,()->join(selected));join.primary=true;bar.finish();
    }
    private static int currentSquad(){
        return Client.state.has("me")?Engine.num(Client.state.getAsJsonObject("me"),"squad",0):0;
    }
    private void join(int id){
        var request=Client.request("squad",Client.currentMap());request.addProperty("squad",id);Client.send(request);minecraft.setScreen(parent);
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){
        Ui.header(g,font,layout.left,"选择小队");Ui.header(g,font,layout.right,"小队成员");
        list.render(g,x,y,dt);members.render(g,x,y,dt);bar.render(g,x,y,dt);
    }
    public boolean mouseScrolled(double x, double y, double v){
        if(dropdownOpen())return super.mouseScrolled(x, y, v);
        return list.wheel(x,y,v)||members.wheel(x,y,v)||super.mouseScrolled(x, y, v);
    }
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
