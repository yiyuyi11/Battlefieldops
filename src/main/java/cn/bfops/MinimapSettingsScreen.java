package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
/** Transactional local settings: no server permissions and no scroll-wheel hotbar interception. */
final class MinimapSettingsScreen extends FrontScreen {
    private final Screen parent;
    private MinimapOptions working=ClientPrefs.minimap().copy();
    private boolean rotate=ClientPrefs.followHeading(),hidden=ClientPrefs.minimapHidden();
    private String corner=ClientPrefs.data().minimapCorner;
    private String span=""+working.span,size=""+working.size,opacity=""+working.terrainOpacity;
    private Form left,right,bar;
    private boolean nativeOpened;
    @Override protected void init(){if(nativeOpened){nativeOpened=false;nativeSettings=new XaeroSettingsBridge();nativeNumbers.clear();nativeX=""+nativeSettings.offset("x");nativeY=""+nativeSettings.offset("y");}super.init();}
    private XaeroSettingsBridge nativeSettings=new XaeroSettingsBridge();
    private String nativeX=""+nativeSettings.offset("x"),nativeY=""+nativeSettings.offset("y");
    private final java.util.Map<String,String> nativeNumbers=new java.util.LinkedHashMap<>();
    MinimapSettingsScreen(Screen parent){super("小地图设置");this.parent=parent;}
    private java.util.List<ChoiceScreen.Choice> cornerChoices(boolean inherit){var list=new java.util.ArrayList<ChoiceScreen.Choice>();if(inherit)list.add(new ChoiceScreen.Choice("","跟随服务器",""));for(var c:java.util.List.of("TOP_LEFT","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_RIGHT"))list.add(new ChoiceScreen.Choice(c,Briefing.minimapCornerName(c),""));return list;}
    protected String subtitle(){return "本地保存 · 立即生效 · 敌方信息范围仍由服务器决定";}
    private void toggle(String name,boolean value,Runnable action,Form form){row(form,name,value?"开":"关",()->{action.run();rebuild();});}
    protected void rebuildContent(){
        int l=left==null?0:left.scroll(),r=right==null?0:right.scroll();clearWidgets();left=newForm(Ui.headerBody(layout.left));right=newForm(Ui.headerBody(layout.right));
        {
            String current = ClientPrefs.screenBackground();
            String label = current.isBlank() ? "内置（BF1）" : current;
            row(left, "界面背景图", label, () -> {
                java.util.List<String> options = new java.util.ArrayList<>();
                options.add("");
                options.addAll(ScreenBackground.names());
                int index = options.indexOf(current);
                ClientPrefs.setScreenBackground(options.get((index + 1) % options.size()));
                rebuild();
            });
            row(left, "重新扫描背景文件夹", "config/battlefield_ops/backgrounds", () -> {
                ScreenBackground.refresh();
                ClientPrefs.setScreenBackground("");
                rebuild();
            });
        }
        toggle("倒地显示死亡界面", ClientPrefs.deathOverDowned(), () -> ClientPrefs.toggleDeathOverDowned(), left);
        toggle("对局外死亡界面与音乐", ClientPrefs.deathScreenOutsideMatch(), () -> ClientPrefs.toggleDeathScreenOutsideMatch(), left);
        toggle("击杀图标", ClientPrefs.killIcons(), () -> ClientPrefs.toggleKillIcons(), left);
        toggle("显示小地图",!hidden,()->hidden=!hidden,left);
        if(nativeSettings.ready){
            left.section("Xaero 当前配置档");
            String[][] fields={{"SIZE","地图尺寸"},{"ZOOM","缩放等级"},{"UI_SCALE","界面比例"},{"OPACITY","地形透明度"}};
            for(var field:fields){String key=field[0];nativeNumbers.putIfAbsent(key,nativeSettings.value(key));
                field(left,field[1],nativeNumbers.get(key),6,v->nativeNumbers.put(key,v));
                var valid=nativeSettings.valid.get(key);if(valid!=null&&!valid.isEmpty())left.caption("原生范围："+valid.getFirst()+" ～ "+valid.getLast(),Ui.TEXT_FAINT);
            }
            dropdown(left,"地图形状",nativeSettings.value("SHAPE"),nativeSettings.shapeChoices(),v->{nativeSettings.values.put("SHAPE",nativeSettings.valid.get("SHAPE").stream().filter(n->String.valueOf(n).equals(v)).findFirst().orElseThrow());rebuild();});
            row(left,"北方朝上",Boolean.TRUE.equals(nativeSettings.get("NORTH_LOCKED"))?"开":"关",()->{nativeSettings.cycle("NORTH_LOCKED");rebuild();});
            dropdown(left,"屏幕锚点",nativeSettings.corner(),cornerChoices(false),v->{nativeSettings.corner(v);rebuild();});
            field(left,"水平偏移",nativeX,6,v->nativeX=v);field(left,"垂直偏移",nativeY,6,v->nativeY=v);
            row(left,"更多 Xaero 原生配置","打开",()->{nativeOpened=true;NativeXaero.settings(this);});
        }else{
            if(!nativeSettings.error.isEmpty())left.paragraph(nativeSettings.error,Ui.TEXT_WARN);
            field(left,"可视范围（64～2048 格）",span,4,v->span=v);
            field(left,"尺寸（64～220）",size,3,v->size=v);
            field(left,"地貌不透明度（25～100%）",opacity,3,v->opacity=v);
            dropdown(left,"屏幕位置",corner,cornerChoices(true),v->{corner=v;rebuild();});
            row(left,"自身箭头大小",Math.round(ClientPrefs.selfArrowSize())+"px",()->{
            float size=ClientPrefs.selfArrowSize();
            float next=size<7?9.0F:size<12?14.0F:size<16?18.0F:5.0F;
            ClientPrefs.setSelfArrowSize(next);rebuild();
        });
        toggle("朝向跟随旋转",rotate,()->rotate=!rotate,left);
            toggle("平滑跟随",working.smooth,()->working.smooth=!working.smooth,left);
            toggle("地貌图层",working.terrain,()->working.terrain=!working.terrain,right);
        }
        toggle("坐标网格",working.grid,()->working.grid=!working.grid,right);
        toggle(NativeXaero.available()?"方向标识（回退地图）":"方向标识",working.compass,()->working.compass=!working.compass,right);
        toggle("坐标与范围文字",working.coordinates,()->working.coordinates=!working.coordinates,right);
        toggle("下方小队名单（含自己）",working.squad,()->working.squad=!working.squad,right);
        toggle("地图上成员名称",working.names,()->working.names=!working.names,right);
        toggle("载具标记",working.vehicles,()->working.vehicles=!working.vehicles,right);
        toggle("补给与道具标记",working.support,()->working.support=!working.support,right);
        toggle("据点与基地标记",working.objectives,()->working.objectives=!working.objectives,right);
        row(right,"远处 TaCZ 枪械简化",ClientPrefs.data().distantGunCull?"开启 · 保留角色、光晕和标记":"关闭",()->{ClientPrefs.toggleDistantGunCull();rebuild();});
        toggle("视野扇形",working.viewCone,()->working.viewCone=!working.viewCone,right);
        left.scrollTo(l);right.scrollTo(r);left.finish();right.finish();bar=newForm(layout.bar.inset(2));var cells=Layout.columns(layout.bar.inset(2),3,4);
        place(bar,cells[0],"取消","","","",true,this::onClose);
        place(bar,cells[1],"恢复默认","","","",true,()->{working=new MinimapOptions();if(nativeSettings.ready){nativeSettings.defaults();nativeNumbers.clear();nativeX=""+nativeSettings.offset("x");nativeY=""+nativeSettings.offset("y");}span=""+working.span;size=""+working.size;opacity=""+working.terrainOpacity;rotate=true;hidden=false;corner="";rebuild();});
        place(bar,cells[2],"应用并保存","","","",true,()->{
            try{working.span=Integer.parseInt(span);working.size=Integer.parseInt(size);working.terrainOpacity=Integer.parseInt(opacity);Model.range(working.span,64,2048,"范围");Model.range(working.size,64,220,"尺寸");Model.range(working.terrainOpacity,25,100,"不透明度");}
            catch(RuntimeException ex){fail("请输入有效整数：范围 64～2048，尺寸 64～220，不透明度 25～100");return;}
            try{
                if(nativeSettings.ready){for(var e:nativeNumbers.entrySet())nativeSettings.values.put(e.getKey(),Integer.parseInt(e.getValue()));nativeSettings.offset("x",Integer.parseInt(nativeX));nativeSettings.offset("y",Integer.parseInt(nativeY));nativeSettings.apply();}
                ClientPrefs.saveMinimap(working,rotate,hidden,corner);minecraft.setScreen(parent);
            }catch(ReflectiveOperationException|RuntimeException ex){fail("保存失败："+FrontScreen.describe(ex));}
        });bar.finish();say("F7 打开设置；小键盘 + / − 缩放。可在按键设置中修改，不占用滚轮。");
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){Ui.header(g,font,layout.left,"范围与布局");Ui.header(g,font,layout.right,"显示图层");left.render(g,x,y,dt);right.render(g,x,y,dt);bar.render(g,x,y,dt);}
    public boolean mouseScrolled(double x,double y,double h,double v){if(dropdownOpen())return super.mouseScrolled(x,y,h,v);return left.wheel(x,y,v)||right.wheel(x,y,v)||super.mouseScrolled(x,y,h,v);}
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
