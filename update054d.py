from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'CombatHud.java';s=f.read_text('utf-8');s=s.replace('int progress=Engine.num(f,"progress",50),px=','int progress=Engine.num(f,"progress",50);if(Engine.num(me,"team",-1)==1)progress=100-progress;int px=');s=s.replace('0xff65b5fa);g.fill(px+2+progress,py+15,px+102,py+18,0xffef7272)','TacticalColors.FRIEND);g.fill(px+2+progress,py+15,px+102,py+18,TacticalColors.ENEMY)');s=s.replace('        // Only the objective','        String hint=Engine.str(state,"objectiveHint","");\n        if(!hint.isEmpty())g.drawCenteredString(mc.font,hint,width/2,height-76,0xffffca77);\n        // Only the objective');f.write_text(s,'utf-8')
f=p/'Engine.java';s=f.read_text('utf-8').replace('            out.put("objective",objective);','''            out.put("objective",objective);
            if(objective<0&&me.alive&&player.isAlive()){
                for(int i=0;i<activeMap.points.size();i++){
                    Model.Point point=activeMap.points.get(i);
                    if(Math.hypot(player.getX()-point.x,player.getZ()-point.z)>point.radius)continue;
                    String hint=!match.rules.active(i)?point.id+" 尚未解锁（突破阶段 "+(point.sector+1)+"）":
                        !ObjectiveZone.contains(point,player.getX(),player.getY(),player.getZ())?point.id+" 高度不符：Y "+Math.round(point.y-point.height/2)+"～"+Math.round(point.y+point.height/2):"战区外不能占点";
                    out.put("objectiveHint",hint);break;
                }
            }''');f.write_text(s,'utf-8')
f=p/'ResultScreen.java';s=f.read_text('utf-8').replace('        details.section(Engine.str(result,"outcome","对局结束"));','        details.skip(32);');s=s.replace('protected void paint(GuiGraphics g,int x,int y,float dt){details.render(g,x,y,dt);actions.render(g,x,y,dt);}','''protected void paint(GuiGraphics g,int x,int y,float dt){
        String outcome=Engine.str(result,"outcome","对局结束");
        int color=outcome.equals("胜利")?0xff38dcff:outcome.equals("失败")?0xffff762b:0xffffffff;
        g.pose().pushPose();g.pose().scale(2,2,1);
        g.drawCenteredString(font,outcome,layout.content.cx()/2,(layout.content.y()+8)/2,color);g.pose().popPose();
        details.render(g,x,y,dt);actions.render(g,x,y,dt);
    }''');f.write_text(s,'utf-8')
f=p/'Minimap.java';s=f.read_text('utf-8').replace('int filled = (int) Math.round(barWidth * Math.max(0, Math.min(100, progress)) / 100.0);','int filled = (int) Math.round(barWidth * Math.max(0, Math.min(100, selfTeam==1?100-progress:progress)) / 100.0);').replace('py + 8, BLUE|0xff000000);','py + 8, selfTeam<0?BLUE|0xff000000:TacticalColors.FRIEND);').replace('py + 8, RED|0xff000000);','py + 8, selfTeam<0?RED|0xff000000:TacticalColors.ENEMY);');f.write_text(s,'utf-8')
for f in [Path('build.py'),Path('gradle.properties'),Path('src/main/resources/META-INF/neoforge.mods.toml')]:
 if f.exists():f.write_text(f.read_text('utf-8').replace('0.5.3','0.5.4'),'utf-8')
s=Path('install-0.5.3.ps1').read_text('utf-8-sig').replace('0.5.3','0.5.4').replace("'battlefield-ops-0.5.2-neoforge-1.21.1.jar'","'battlefield-ops-0.5.2-neoforge-1.21.1.jar','battlefield-ops-0.5.3-neoforge-1.21.1.jar'")
Path('install-0.5.4.ps1').write_text(s,'utf-8-sig')
