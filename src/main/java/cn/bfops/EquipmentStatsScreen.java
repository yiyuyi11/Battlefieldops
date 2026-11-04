package cn.bfops;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
final class EquipmentStatsScreen extends FrontScreen {
    private final Screen parent;private final String kind,id;private final Map<String,String> selected;private final VehicleArmament armament;
    private Form form,bar;
    private long balanceRevision=Long.MIN_VALUE;
    @Override protected void poll(){if(balanceRevision!=BalanceDisplay.revision()){balanceRevision=BalanceDisplay.revision();rebuild();}}
    EquipmentStatsScreen(Screen parent,String kind,String id,Map<String,String> selected){this(parent,kind,id,selected,null);}
    EquipmentStatsScreen(Screen parent,String kind,String id,Map<String,String> selected,VehicleArmament armament){super("装备属性 · "+GearNames.display(kind,id,id));this.parent=parent;this.kind=kind;this.id=id;this.selected=Map.copyOf(selected);this.armament=armament;}
    protected String subtitle(){return "原始武器 → 当前配装 · 上灰条为原始，下彩条为当前 · 不改变实际装备";}
    protected void rebuildContent(){
        int scroll=form==null?0:form.scroll();clearWidgets();form=newForm(Ui.headerBody(layout.content));var report=EquipmentStatistics.read(kind,id,selected,armament);
        form.section(report.source().isEmpty()?"属性读取":report.source());
        if(report.rows().isEmpty())form.paragraph(report.warning(),Ui.TEXT_WARN);
        else{for(var row:report.rows())form.paint(36,(g,rect)->EquipmentStatPanel.row(g,font,rect,row,false));form.paragraph(report.warning(),Ui.TEXT_DIM);}
        form.paragraph("条形只用于同一属性的改装前后对比，不代表不同武器之间的性能评分。",Ui.TEXT_FAINT);form.scrollTo(scroll);form.finish();
        bar=newForm(layout.bar.inset(2));place(bar,layout.bar.inset(2),"返回","","","",true,this::onClose);bar.finish();
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){Ui.header(g,font,layout.content,"原生属性对比");form.render(g,x,y,dt);bar.render(g,x,y,dt);}
    public boolean mouseScrolled(double x, double y, double v){if(dropdownOpen())return super.mouseScrolled(x, y, v);return form.wheel(x,y,v)||super.mouseScrolled(x, y, v);}
    public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
}
