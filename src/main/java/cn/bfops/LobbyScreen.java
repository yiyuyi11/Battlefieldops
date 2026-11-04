package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import java.util.*;

/** Server-wide browser: public room metadata is separate from the current match snapshot. */
final class LobbyScreen extends FrontScreen {
    private Form rooms,details,actions,joinActions;
    private EditBox search;
    private String selected="",query="",status="全部状态";
    private int poll;
    private boolean filterDirty,pendingJoin,transferring;
    private long actionAt;
    @Override protected boolean actionIcons(){return false;}
    private FrontButton roomRow(String label,String value,Runnable action){return place(rooms,rooms.take(17,1),label,value,"","",true,action);}
    @Override protected Layout layoutFor(int width,int height){return Layout.bf1Lobby(width,height);}
    @Override protected boolean horizontalTabs(){return true;}
    @Override protected void paintBackdrop(GuiGraphics g){Bf1Ui.lobbyBackground(g,width,height);}
    private String lastDirectory="";
    private final Set<String> collapsed=new HashSet<>();
    LobbyScreen(){
        super("多人游戏 · 服务器大厅");
        String notice=Engine.str(Client.lobby,"message","");
        if(notice.startsWith("操作失败"))fail(notice);else if(!notice.isEmpty())ok(notice);
    }
    private JsonObject data(){return Client.lobby;}
    private boolean admin(){return data().has("admin")&&data().get("admin").getAsBoolean();}
    private String membership(){return Engine.str(data(),"membership","");}
    protected String[] tabs(){
        return admin() ? new String[]{"游戏大厅","配装配置","地图设置","地图编辑"}
                       : new String[]{"游戏大厅","配装配置","地图设置"};
    }
    protected int activeTab(){return 0;}
    protected boolean onTab(int index){
        switch(index){
            case 0 -> request("lobby");
            case 1 -> minecraft.setScreen(new LobbyLoadoutScreen(this));
            case 2 -> minecraft.setScreen(new MinimapSettingsScreen(this));
            default -> { if(admin()) minecraft.setScreen(new OpsScreen()); }
        }
        return true;
    }
    protected String subtitle(){
        String backend=Engine.str(data(),"backend","");
        String local=Engine.num(data(),"online",0)+" 人在线 · "+Engine.num(data(),"activeRooms",0)+" 个房间";
        return backend.isEmpty()?local:backend+" · 本服 "+local+" · 跨服 "+Engine.num(data(),"remoteRooms",0);
    }
    private JsonObject chosenFor(String id){return entries().stream().filter(e->Engine.str(e,"id","").equals(id)).findFirst().orElse(new JsonObject());}
    private List<JsonObject> entries(){
        return LobbyDirectory.visible(data().has("rooms")?data().getAsJsonArray("rooms"):new JsonArray(),query,"",status);
    }
    private static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    /** 656-conquest style group header: localised name plus the gameplay id. */
    private static String modeFullName(String modeName){
        return switch(modeName){
            case "征服"->"征服-Conquest";
            case "突破"->"突破-Breakthrough";
            case "突袭"->"突袭-Rush";
            case "团队死斗"->"团队死斗-TDM";
            default->modeName;
        };
    }
    private void request(String op){
        var packet=Client.request(op,selected);Client.send(packet);
        if(!op.equals("lobby")){actionAt=System.currentTimeMillis();say("请求已发送，等待服务器确认…");}
    }
    static void open(){
        if(DeploymentLock.room(Client.state)&&!(net.minecraft.client.Minecraft.getInstance().screen instanceof ResultScreen)){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new ConfirmActionScreen(mc.screen,"退出房间并进入大厅？","进入大厅需要退出当前房间，当前部署状态将结束。","退出并返回大厅",()->Client.send(Client.request("lobbyReturn",Client.currentMap()))));return;}
        var packet=Client.request("lobby","");packet.addProperty("open",true);Client.send(packet);}
    void received(JsonObject payload){
        String msg=Engine.str(payload,"message","");
        if(!msg.isEmpty()){
            if(!msg.equals("正在切换子服…")){pendingJoin=false;transferring=false;}
            if(msg.startsWith("操作失败"))fail(msg);else ok(msg);
        }
        // Ignore the one-shot notification itself when deciding whether to rebuild the widgets.
        JsonObject stable=payload.deepCopy();stable.remove("message");stable.remove("open");String directory=stable.toString();
        if(!directory.equals(lastDirectory)||!msg.isEmpty()){lastDirectory=directory;rebuild();}
    }
    void roomUpdated(JsonObject payload){
        if(pendingJoin&&payload.has("me")){
            pendingJoin=false;minecraft.setScreen(new BattleScreen());return;
        }
        String msg=Engine.str(payload,"message","");
        if(msg.startsWith("操作失败")){pendingJoin=false;fail(msg);}
    }
    protected void poll(){
        if(filterDirty){filterDirty=false;rebuild();}
        if(++poll>=24){poll=0;request("lobby");}
        if(pendingJoin&&System.currentTimeMillis()-actionAt>(transferring?15000:5000)){
            pendingJoin=false;
            warn(transferring?"切服尚未完成，请检查代理连接后稍候重试":"服务器尚未确认加入，请刷新后重试");
            transferring=false;rebuild();
        }
    }
    private int bannerHeight(){return layout.right.h()>=200&&layout.right.w()>=190?64:0;}
    protected void rebuildContent(){
        int leftScroll=rooms==null?0:rooms.scroll(),rightScroll=details==null?0:details.scroll();
        boolean focused=search!=null&&search.isFocused();int cursor=search==null?0:search.getCursorPosition();
        clearWidgets();rooms=newForm(layout.left.inset(4));details=newForm(new Layout.Rect(layout.right.x()+6,layout.right.y()+6+bannerHeight(),layout.right.w()-12,Math.max(24,layout.right.h()-40-bannerHeight())));joinActions=newForm(new Layout.Rect(layout.right.x()+4,layout.right.bottom()-28,layout.right.w()-8,28));
        if(layout.left.h()>=120)rooms.text("搜索地图 / ID / 模式 / 子服",Ui.TEXT_FAINT);
        search=edit(rooms.take(16,3),query,80,value->{query=value;filterDirty=true;});rooms.add(search);
        dropdown(rooms,"筛选",status,List.of("全部状态","可加入","等待开始","对局进行中").stream().map(v->new ChoiceScreen.Choice(v,v,"")).toList(),v->{status=v;rebuild();});
        List<JsonObject> list=entries();
        if(list.stream().noneMatch(o->Engine.str(o,"id","").equals(selected)))selected=list.isEmpty()?"":Engine.str(list.get(0),"id","");
        if(!list.isEmpty()&&layout.left.h()>=120)rooms.fact("房间","人数",Ui.TEXT_DIM);
        String group="";
        JsonObject chosen=list.stream().filter(entry->Engine.str(entry,"id","").equals(selected)).findFirst().orElse(null);
        for(var entry:list){
            String id=Engine.str(entry,"id","");String mode=Engine.str(entry,"modeName","");
            if(!mode.equals(group)&&layout.left.h()>=100){
                group=mode;boolean shut=collapsed.contains(mode)&&query.isBlank();
                long count=list.stream().filter(e->Engine.str(e,"modeName","").equals(mode)).count();
                FrontButton groupEntry=roomRow((shut?"▶ ":"▼ ")+modeFullName(mode),count+" 张地图",()->{if(!collapsed.remove(mode))collapsed.add(mode);rebuild();});
                groupEntry.header=true;
            }
            if(layout.left.h()>=100&&collapsed.contains(mode)&&query.isBlank())continue;
            String backend=Engine.str(entry,"backend","");
            String label=(backend.isEmpty()?"":"["+backend+"] ")+Engine.str(entry,"name",id);
            String icon=bool(entry,"running")?"objectives_capturepoint_map_friendly":
                    bool(entry,"room")?"objectives_spawnable_default_default":"objectives_capturepoint_map_default";
            FrontButton roomEntry=place(rooms,rooms.take(19,2),label,
                    Engine.num(entry,"players",0)+"/"+Engine.num(entry,"capacity",64),"bf1",icon,true,
                    ()->{selected=id;if(details!=null)details.resetScroll();rebuild();});
            roomEntry.selected=id.equals(selected);
        }
        if(list.isEmpty())rooms.paragraph("没有符合条件的地图。可切换模式或清空搜索。",Ui.TEXT_DIM);
        if(chosen!=null){
            boolean exists=bool(chosen,"room"),running=bool(chosen,"running");
            String backend=Engine.str(chosen,"backend","");
            String mapId=Engine.str(chosen,"mapId",selected);
            boolean remote=!backend.isEmpty();
            details.section(Engine.str(chosen,"name",selected));
            details.fact("所在子服",remote?backend:"本服",remote?Ui.ACCENT:Ui.TEXT_DIM);
            details.paragraph("地图 ID："+mapId,Ui.TEXT_FAINT);
            String description=Engine.str(chosen,"description","");if(!description.isBlank())details.paragraph(description,Ui.TEXT_DIM);
            details.fact("模式",Engine.str(chosen,"modeName",""),Ui.ACCENT);
            details.fact("状态",running?"对局进行中":exists?"等待开始":"尚未创建房间",running?Ui.ACCENT:Ui.TEXT_WARN);
            details.fact("人数",Engine.num(chosen,"players",0)+" / "+Engine.num(chosen,"capacity",64),Ui.TEXT);
            details.fact(Engine.str(chosen,"blueFactionName","机械轴心")+" / "+Engine.str(chosen,"redFactionName","格雷同盟"),Engine.num(chosen,"blue",0)+" / "+Engine.num(chosen,"red",0),Ui.TEXT);
            details.fact("尚未选队",""+Engine.num(chosen,"waiting",0),Ui.TEXT_DIM);
            
            String blocker=exists?RoomPolicy.joinBlocker(!membership().isEmpty(),true,Engine.num(chosen,"players",0),Engine.num(chosen,"capacity",64)):"";
            primary(joinActions,pendingJoin?(transferring?"正在切服…":"正在加入…"):remote?"切服并加入":!exists?"创建所选房间":membership().equals(selected)?"返回当前房间":"加入所选房间",!pendingJoin&&(!exists||membership().equals(selected)||blocker.isEmpty()),()->{
                if(remote){
                    var packet=Client.request("lobbyTransfer",mapId);
                    packet.addProperty("backend",backend);
                    pendingJoin=transferring=true;actionAt=System.currentTimeMillis();
                    Client.send(packet);rebuild();
                }
                else if(!exists)request("lobbyCreate");
                else if(membership().equals(selected)){var p=Client.request("open",selected);Client.send(p);}
                else {pendingJoin=true;request("lobbyJoin");rebuild();}
            });
            if(!blocker.isEmpty()&&!membership().equals(selected))details.paragraph(blocker,Ui.TEXT_WARN);
            if(exists&&!remote)details.paragraph("房主："+Engine.str(chosen,"host",""),Ui.TEXT_DIM);
            if(bool(chosen,"canManage")){
                details.section("房间管理");
                if(exists&&!running)row(details,"开始所选房间","其他房间继续",()->request("lobbyStart"));
                if(exists)row(details,"关闭所选房间","仅结算此房间",()->{String target=selected;String map=Engine.str(chosenFor(target),"name",target);minecraft.setScreen(new ConfirmActionScreen(this,"关闭并结算房间？",map+"：结束并结算此房间，其他房间不受影响。","关闭并结算",()->Client.send(Client.request("lobbyClose",target))));});
            }
        }
        row(details,"656 详细排行榜","本服战绩记录 · 支持对局外查看",()->NativeKillIcons.openScoreboard(this));
        row(details,"服务器总榜与荣誉","长期累计战绩 · 查看个人勋章",()->CareerScreen.open(this));
        row(details,"操作设置","趴下：切换 / 按住",()->minecraft.setScreen(new ControlSettingsScreen(this)));
        details.section("服务器大厅");
        details.paragraph(Engine.str(data(),"hub","等待服务器大厅信息…"),Ui.TEXT_DIM);
        if(!membership().isEmpty()){
            row(details,"返回当前房间","继续选队 / 部署",()->{var p=Client.request("open",membership());Client.send(p);});
            row(details,"退出房间并返回大厅","恢复背包",()->request("lobbyReturn"));
        } else if(bool(data(),"hubEnabled"))row(details,"传送到共享大厅","保留背包",()->request("lobbyReturn"));
        if(admin()){
            row(details,"将当前位置设为大厅","退出房间后可设置",membership().isEmpty(),()->request("lobbySet"));
            if(bool(data(),"hubEnabled"))row(details,"关闭大厅自动传送","恢复原位置",()->request("lobbyDisable"));
        }
        joinActions.finish();rooms.scrollTo(leftScroll);rooms.finish();details.scrollTo(rightScroll);details.finish();
        actions=newForm(layout.bar.inset(2));var cells=Layout.columns(layout.bar.inset(2),2,4);
        place(actions,cells[0],"关闭大厅","","","",true,this::onClose);
        place(actions,cells[1],"刷新房间","","","",true,()->request("lobby"));actions.finish();
        if(focused){setFocused(search);search.setFocused(true);search.moveCursorTo(Math.min(cursor,query.length()),false);}
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){
        // Solid content backdrop. The old pane fills were 19%/25% opaque, so a bright world washed the
        // faint text out; the content band is now opaque enough to read against any map.
        int top=layout.title.h()>0?layout.title.bottom():layout.tabs.bottom();
        int bottom=showBottomBar()&&layout.bar.h()>0?layout.bar.y():height;
        if(bottom>top)g.fill(0,top,width,bottom,0x30101418);
        g.fill(layout.left.x(),layout.left.y(),layout.left.right(),layout.left.bottom(),0xb0101418);
        g.fill(layout.right.x(),layout.right.y(),layout.right.right(),layout.right.bottom(),0x90101418);
        g.fill(layout.left.x(),layout.left.y(),layout.left.right(),layout.left.y()+1,0x60ffffff);
        g.fill(layout.right.x(),layout.right.y(),layout.right.right(),layout.right.y()+1,0x60ffffff);
        g.drawString(font,data().has("backend")?"群组服房间":"房间选择",layout.left.x(),layout.left.y()-10,Ui.ACCENT,false);
        if(bannerHeight()>0)AssetIcons.draw(g,"bf1","server_browser_card",layout.right.x()+6,layout.right.y()+4,layout.right.w()-12,bannerHeight()-4);
        rooms.render(g,x,y,dt);details.render(g,x,y,dt);actions.render(g,x,y,dt);joinActions.render(g,x,y,dt);
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical){if(dropdownOpen())return super.mouseScrolled(x, y, horizontal, vertical);
        if(rooms.wheel(x,y,vertical)||details.wheel(x,y,vertical))return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    public boolean isPauseScreen(){return false;}
}
