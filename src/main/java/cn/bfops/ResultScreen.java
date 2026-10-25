package cn.bfops;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** A participant's own final summary survives removal from the room. */
final class ResultScreen extends FrontScreen {
    private final JsonObject result;
    private final Screen previous;
    private Form details,actions;
    ResultScreen(JsonObject result,Screen previous){super("对局结算");this.result=result;this.previous=previous;}
    protected String subtitle(){return Engine.str(result,"mapName","");}
    protected void rebuild(){
        clearWidgets();details=newForm(layout.content);
        details.skip(32);
        details.paragraph(Engine.str(result,"reason",""),Ui.TEXT);
        details.section("个人战绩");
        details.text("击杀 "+Engine.num(result,"kills",0)+"    阵亡 "+Engine.num(result,"deaths",0),Ui.TEXT);
        details.text("占点 "+Engine.num(result,"captures",0)+"    得分 "+Engine.num(result,"score",0),Ui.TEXT);
        details.finish();actions=newForm(layout.bar.inset(2));
        var cells=Layout.columns(layout.bar.inset(2),2,4);
        place(actions,cells[0],previous instanceof OpsScreen?"返回编辑器":"关闭结算","","","",true,this::onClose);
        place(actions,cells[1],"作战房间","","","",!(previous instanceof OpsScreen),()->minecraft.setScreen(new BattleScreen()));
        actions.finish();ok("本局已结束，战绩已结算；关闭后可选择其他房间");
    }
    protected void paint(GuiGraphics g,int x,int y,float dt){
        String outcome=Engine.str(result,"outcome","对局结束");
        int color=outcome.equals("胜利")?0xff38dcff:outcome.equals("失败")?0xffff762b:0xffffffff;
        // The details pane had no surface at all: text was painted straight onto the backdrop, so the
        // panel frame the other screens all have was simply missing here. Painting it first also means
        // the outcome banner has something to sit on instead of floating over empty space.
        Layout.Rect panel=layout.content;
        Ui.header(g,font,panel,"结算详情");
        Layout.Rect band=new Layout.Rect(panel.x()+1,panel.y()+1,panel.w()-2,30);
        Ui.gradient(g,band,Ui.mix(color,Ui.PANEL_ALT,0.78),Ui.PANEL_ALT);
        g.fill(band.x(),band.bottom()-1,band.right(),band.bottom(),color);
        g.pose().pushPose();g.pose().scale(2,2,1);
        g.drawCenteredString(font,outcome,band.cx()/2,(band.y()+7)/2,color);g.pose().popPose();
        details.render(g,x,y,dt);actions.render(g,x,y,dt);
    }
    public void onClose(){minecraft.setScreen(previous instanceof OpsScreen?previous:null);}
    public boolean isPauseScreen(){return false;}
}
