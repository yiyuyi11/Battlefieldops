package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

final class ControlSettingsScreen extends FrontScreen {
    private final Screen parent;private Form controls,help;
    ControlSettingsScreen(Screen parent){super("操作设置");this.parent=parent;}
    protected void rebuildContent(){
        clearWidgets();controls=newForm(Ui.headerBody(layout.left));help=newForm(Ui.headerBody(layout.right));
        controls.section("步兵姿态");
        if(MovementHandoff.active()){
            row(controls,"趴下方式","由战地机动接管：Z 冲刺滑铲 / 平时趴下",()->{});
            controls.paragraph("已检测到战地机动（battlefield_movement）。Z 键由该模组接管：冲刺时滑铲，其余情况趴下；本页不再修改 TaCZ 的趴下方式。",Ui.TEXT_DIM);
        }else{
            row(controls,"趴下方式",ProneControls.available()?(ProneControls.hold()?"按住：松开站起":"切换：按一次趴下，再按一次站起"):"需要 TaCZ",ProneControls.available(),()->{try{ProneControls.toggle();ok("已保存到 TaCZ 原生设置");rebuild();}catch(RuntimeException ex){fail("保存失败："+ex.getMessage());}});
            controls.paragraph("使用 TaCZ 的原趴下键（战斗预设为 Z），不注册第二个同键动作。卓越前线武器沿用角色姿态；乘坐载具时保留载具操作。",Ui.TEXT_DIM);
        }
        help.section("按住模式");help.paragraph("打开菜单、切换窗口或松键时会结束本次按住趴下。倒地姿态仍由对局控制，不能松 Z 解除倒地。",Ui.TEXT_DIM);
        help.section("按键修改");help.paragraph("具体按键在 Minecraft → 选项 → 控制 → 按键绑定中修改。本设置与 TaCZ 的 HoldToCrawl 共用一个值，立即生效。",Ui.TEXT_DIM);
        row(controls,"子弹破空声",ClientPrefs.data().bulletWhizz?"开启":"关闭",()->{ClientPrefs.toggleWhizz();rebuild();});
        row(controls,"压制画面与轻微镜头抖动",ClientPrefs.data().suppressionEffects?"开启":"关闭",()->{ClientPrefs.toggleSuppression();rebuild();});
        controls.finish();help.finish();
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){controls.render(g,x,y,dt);help.render(g,x,y,dt);}
    public boolean mouseScrolled(double x, double y, double v){if(dropdownOpen())return super.mouseScrolled(x, y, v);return controls.wheel(x,y,v)||help.wheel(x,y,v)||super.mouseScrolled(x, y, v);}
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
