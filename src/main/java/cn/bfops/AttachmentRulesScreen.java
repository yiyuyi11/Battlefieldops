package cn.bfops;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Administrator edits a draft, then the existing map save commits it to the server. */
final class AttachmentRulesScreen extends FrontScreen {
    private final Screen parent;
    private final Model.Kit kit;
    private final Map<String,InfantryAttachments.Policy> working=new LinkedHashMap<>();
    private String gun="",slot="",attachment="",levelText="0";
    private List<Fittings.Group> groups=List.of();
    private Form list,details,bar;
    private boolean loaded,requested,levelEdited;
    private long sentAt;
    AttachmentRulesScreen(Screen parent,Model.Kit kit){
        super("步兵配件权限 · "+kit.id);this.parent=parent;this.kit=kit;
        if(kit.attachments!=null)kit.attachments.forEach((id,policy)->working.put(id,BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(policy),InfantryAttachments.Policy.class)));
        gun=weapons().stream().findFirst().orElse("");
    }
    private List<String> weapons(){var ids=new LinkedHashSet<>(kit.options(true));ids.addAll(kit.options(false));ids.remove("");return List.copyOf(ids);}
    private InfantryAttachments.Policy policy(){return working.computeIfAbsent(gun,id->new InfantryAttachments.Policy());}
    private InfantryAttachments.Entry entry(){return policy().entries.computeIfAbsent(attachment,id->new InfantryAttachments.Entry());}
    private boolean commitLevel(){
        if(attachment.isEmpty()||!levelEdited)return true;
        try{int level=Integer.parseInt(levelText);Model.range(level,0,100,"解锁等级");entry().unlockLevel=level;levelEdited=false;return true;}
        catch(RuntimeException ex){fail("解锁等级必须是 0～100 的整数；请修正后再切换或应用");return false;}
    }
    private void fetch(){
        if(gun.isEmpty())return;requested=true;loaded=false;sentAt=System.currentTimeMillis();groups=List.of();slot="";attachment="";
        var packet=Client.request("attachmentCatalog","");packet.addProperty("id",gun);Client.send(packet);
    }
    void receive(JsonObject data){
        if(!gun.equals(Engine.str(data,"id","")))return;
        groups=Arrays.asList(BattlefieldOps.GSON.fromJson(data.get("groups"),Fittings.Group[].class));loaded=true;
        slot=groups.isEmpty()?"":groups.get(0).id();ok("编辑的是本兵种这把武器的规则；应用后还需保存地图");rebuild();
    }
    void error(String text){fail(text);}
    protected String subtitle(){return "管理员地图草稿 · 兼容槽位 · 禁用优先于解锁和白名单";}
    protected void poll(){if(requested&&!loaded&&System.currentTimeMillis()-sentAt>8000){requested=false;warn("兼容数据未返回，点击重新读取；草稿已保留");rebuild();}}
    protected void rebuildContent(){
        int offset=list==null?0:list.scroll();clearWidgets();list=newForm(Ui.headerBody(layout.left));details=newForm(Ui.headerBody(layout.right));
        row(list,"武器",gun.isEmpty()?"请先配置兵种武器库":gun,"gun",gun,!weapons().isEmpty(),()->{
            if(!commitLevel())return;
            minecraft.setScreen(new ChoiceScreen(this,"配置哪把武器",weapons().stream().map(id->new ChoiceScreen.Choice(id,id,"gun")).toList(),id->{gun=id;fetch();}));
        });
        if(!gun.isEmpty()){
            if(!requested&&!loaded&&sentAt==0)fetch();
            row(list,"重新读取兼容配件","不会清除规则",()->{if(commitLevel()){fetch();rebuild();}});
            row(list,"授权方式",policy().whitelist?"仅明确授权的白名单":"默认允许所有兼容配件",()->{if(commitLevel()){policy().whitelist=!policy().whitelist;rebuild();}});
            list.paragraph("白名单模式下，未配置的配件一律不可用；默认模式只限制已配置项。等级 0 即直接解锁。",Ui.TEXT_DIM);
            for(var group:groups)row(list,group.name(),group.options().size()+" 项"+(slot.equals(group.id())?" · 当前":""),()->{if(commitLevel()){slot=group.id();attachment="";rebuild();}});
            var group=groups.stream().filter(g->g.id().equals(slot)).findFirst().orElse(null);
            if(group!=null)row(list,"选择配件并编辑","可搜索 @模组名",()->{
                if(!commitLevel())return;
                minecraft.setScreen(new ChoiceScreen(this,group.name(),group.options().stream().map(o->{var e=policy().entries.get(o.id());String status=e==null?(policy().whitelist?"未授权":"直接可用"):e.disabled?"禁用":"等级 "+e.unlockLevel;return new ChoiceScreen.Choice(o.id(),GearNames.display(o.kind(),o.icon(),o.name())+" · "+status,o.kind(),o.icon());}).toList(),id->{attachment=id;var e=policy().entries.get(id);levelText=""+(e==null?0:e.unlockLevel);levelEdited=false;}));
            });
            if(!attachment.isEmpty()){
                selectionDetails(details);
                row(details,"状态",policy().entries.containsKey(attachment)?entry().disabled?"禁用":"允许 / 等级解锁":"未单独配置",()->{if(commitLevel()){entry().disabled=!entry().disabled;rebuild();}});
                field(details,"解锁等级（0～100）",levelText,3,value->{levelText=value;levelEdited=true;});
                row(details,"明确允许此配件","保留所填解锁等级",()->{if(commitLevel()){entry().disabled=false;rebuild();}});
                row(details,"删除此配件规则",policy().whitelist?"将变为未授权":"将恢复默认可用",()->{policy().entries.remove(attachment);levelText="0";levelEdited=false;rebuild();});
            }else details.paragraph("先选槽位，再选择配件。所有配件保留原生图标，只有真正兼容此武器的项目才会出现。",Ui.TEXT_DIM);
            row(details,"恢复此武器全部默认","允许所有兼容配件、无需等级",()->{working.remove(gun);attachment="";levelText="0";levelEdited=false;rebuild();});
            details.paragraph("当前已配置 "+policy().entries.size()+" 项。玩家部署前由服务端再次校验，旧配装和自制请求也不能越过限制。",Ui.TEXT_DIM);
        }
        list.scrollTo(offset);list.finish();details.finish();bar=newForm(layout.bar.inset(2));var cells=Layout.columns(layout.bar.inset(2),2,4);
        place(bar,cells[0],"取消","","","",true,this::onClose);
        place(bar,cells[1],"应用到地图草稿","","","",true,()->{if(!commitLevel())return;working.values().forEach(InfantryAttachments.Policy::validate);kit.attachments.clear();kit.attachments.putAll(working);minecraft.setScreen(parent);});bar.finish();
    }
    private void selectionDetails(Form form){form.section(attachment);form.paragraph(policy().blocker(attachment,0).isEmpty()?"等级 0 已解锁":policy().blocker(attachment,0),Ui.ACCENT);}
    protected void paint(GuiGraphics g,int x,int y,float dt){Ui.header(g,font,layout.left,"武器与配件槽位");Ui.header(g,font,layout.right,"权限与解锁条件");list.render(g,x,y,dt);details.render(g,x,y,dt);bar.render(g,x,y,dt);}
    public boolean mouseScrolled(double x, double y, double v){if(dropdownOpen())return super.mouseScrolled(x, y, v);return list.wheel(x,y,v)||details.wheel(x,y,v)||super.mouseScrolled(x, y, v);}
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
