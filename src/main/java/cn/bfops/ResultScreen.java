package cn.bfops;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;
/** Immutable final-room snapshot, distinct from server-wide career standings. */
final class ResultScreen extends FrontScreen {
 private final JsonObject result;private final Screen previous;private Form personal,ranking,actions;
 ResultScreen(JsonObject result,Screen previous){super("对局结算");this.result=result;this.previous=previous;}
 protected String subtitle(){return Engine.str(result,"mapName","")+" · "+Engine.str(result,"modeName","");}
 protected void rebuildContent(){
  clearWidgets();personal=newForm(Ui.headerBody(layout.left));ranking=newForm(Ui.headerBody(layout.right));
  personal.skip(32);personal.paragraph(Engine.str(result,"reason",""),Ui.TEXT_DIM);
  String[][] metrics={{"个人得分","score"},{"击杀","kills"},{"阵亡","deaths"},{"助攻","assists"},{"救援","revives"},{Engine.str(result,"mode","").equals("RUSH")?"目标摧毁":"据点占领","captures"}};
  for(int i=0;i<metrics.length;i+=2){int at=i;personal.paint(34,(g,r)->{var cells=Layout.columns(r,2,4);for(int k=0;k<2;k++){var cell=cells[k];g.fill(cell.x(),cell.y(),cell.right(),cell.bottom()-2,0x38243b4a);g.drawString(font,metrics[at+k][0],cell.x()+5,cell.y()+4,Ui.TEXT_DIM,false);g.drawString(font,""+Engine.num(result,metrics[at+k][1],0),cell.x()+5,cell.y()+17,Ui.ACCENT,true);}});}
  double kd=Engine.num(result,"kills",0)/(double)Math.max(1,Engine.num(result,"deaths",0));personal.caption(String.format(java.util.Locale.ROOT,"K/D %.2f",kd),Ui.TEXT_DIM);
  if(result.has("experience"))personal.paragraph("本局经验 +"+Engine.num(result,"experience",0)+" · 等级 "+Engine.num(result,"level",0),Ui.ACCENT);
  if(result.has("progressError"))personal.paragraph(Engine.str(result,"progressError",""),Ui.TEXT_WARN);
  int mine=Engine.num(result,"team",0);for(int team:new int[]{mine,1-mine}){
   int tint=team==mine?TacticalColors.FRIEND:TacticalColors.ENEMY;
   var rows=new java.util.ArrayList<JsonObject>();if(result.has("ranking"))for(var value:result.getAsJsonArray("ranking")){var row=value.getAsJsonObject();if(Engine.num(row,"team",-1)==team)rows.add(row);}
   rows.sort(java.util.Comparator.comparingInt((JsonObject row)->Engine.num(row,"score",0)).reversed());
   ranking.section(Engine.str(result,team==0?"blueFactionName":"redFactionName",Board.label(team))+" · "+(team==mine?"我方":"敌方")+" · "+rows.size()+" 人");
   ranking.caption("姓名       击/死 · 助/救 · 得分",Ui.TEXT_DIM);
   int index=0;for(var row:rows){int position=++index;ranking.paint(16,(g,r)->{
    boolean self=Engine.str(row,"name","").equals(Engine.str(result,"playerName",""));if(self)g.fill(r.x(),r.y(),r.right(),r.bottom(),0x405b9abb);
    String stats=Engine.num(row,"kills",0)+"/"+Engine.num(row,"deaths",0)+" · "+Engine.num(row,"assists",0)+"/"+Engine.num(row,"revives",0)+" · "+Engine.num(row,"score",0);
    int room=Math.max(8,r.w()-font.width(stats)-8);g.drawString(font,Ui.clip(font,"#"+position+" "+Engine.str(row,"name",""),room),r.x()+2,r.y()+4,self?Ui.TEXT:tint,false);g.drawString(font,stats,r.right()-font.width(stats)-2,r.y()+4,Ui.TEXT_DIM,false);
   });}
   if(rows.isEmpty())ranking.caption("无参战记录",Ui.TEXT_FAINT);
  }
  personal.finish();ranking.finish();actions=newForm(layout.bar.inset(2));var cells=Layout.columns(layout.bar.inset(2),3,4);
  place(actions,cells[0],previous instanceof OpsScreen?"返回编辑器":"关闭结算","","","",true,this::onClose);
  place(actions,cells[1],"服务器累计战绩","","","",true,()->NativeKillIcons.openScoreboard(this));
  place(actions,cells[2],"返回作战大厅","","","",!(previous instanceof OpsScreen),LobbyScreen::open);actions.finish();
  say("右侧为本局最终战绩；服务器累计战绩单独查看。");
 }
 protected void paint(GuiGraphics g,int x,int y,float dt){
  String outcome=Engine.str(result,"outcome","对局结束");
  int color=outcome.equals("胜利")?TacticalColors.FRIEND:outcome.equals("失败")?TacticalColors.ENEMY:Ui.TEXT_DIM;
  // Battlefield announces the result in big condensed type across the top; the two panes keep every stat.
  int banner=30;
  g.fill(0,0,width,banner,0xc010181f);
  g.fill(0,banner-2,width,banner-1,color);
  g.pose().pushPose();
  try{
   g.pose().translate(width/2f,banner/2f-1,200);
   g.pose().scale(2f,2f,1);
   g.drawCenteredString(font,UiFont.text(outcome),0,-4,color);
  }finally{g.pose().popPose();}
  Ui.header(g,font,layout.left,"个人表现");Ui.header(g,font,layout.right,"本局最终排行");
  personal.render(g,x,y,dt);ranking.render(g,x,y,dt);actions.render(g,x,y,dt);
 }
 public boolean mouseScrolled(double x,double y,double h,double v){if(dropdownOpen())return super.mouseScrolled(x,y,h,v);return personal.wheel(x,y,v)||ranking.wheel(x,y,v)||super.mouseScrolled(x,y,h,v);}
 public void onClose(){minecraft.setScreen(previous instanceof OpsScreen?previous:null);}public boolean isPauseScreen(){return false;}
}
