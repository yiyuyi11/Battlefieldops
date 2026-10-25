from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'TacticalColors.java';s=f.read_text('utf-8').replace('static final int FRIEND=0xff38dcff, ENEMY=0xffff762b, NEUTRAL=0xffd8d8d8;','static final int SELF=0xff42d9df, FRIEND=0xff619fea, ENEMY=0xffe96772, NEUTRAL=0xffa3b3bd;');s=s.replace('    static int objective','''    static void label(GuiGraphics g,net.minecraft.client.gui.Font font,String text,int centerX,int y,int color){
        int half=font.width(text)/2;
        g.fill(centerX-half-2,y-1,centerX+half+3,y+9,0xe0000000);
        g.drawCenteredString(font,text,centerX,y,color);
    }
    static int objective''');f.write_text(s,'utf-8')
f=p/'Minimap.java';s=f.read_text('utf-8').replace('graphics.drawCenteredString(font, point.id, px, py - 6, 0xffffffff);','TacticalColors.label(graphics,font,point.id,px,py-15,TacticalColors.objective(owner,selfTeam));').replace('graphics.drawCenteredString(font, "!", px, py + 9, 0xffffaa00);','TacticalColors.label(graphics,font,"!",px,py+9,TacticalColors.ENEMY);');s=s.replace('leftY, 0xffffffff);','leftY, TacticalColors.SELF);').replace('rightY, 0xffffffff);','rightY, TacticalColors.SELF);');f.write_text(s,'utf-8')
f=p/'MapPicker.java';s=f.read_text('utf-8').replace('graphics.drawCenteredString(font, point.id, px, pz - 13, 0xffffffff);','TacticalColors.label(graphics,font,point.id,px,pz-15,fill);').replace('Ui.handle(graphics, px, pz, 2, 0xffffffff);','Ui.handle(graphics, px, pz, 2, TacticalColors.SELF);');s=s.replace('0xffffffff, 1);','TacticalColors.SELF, 1);');s=s.replace('graphics.drawString(font, label, px + 9, pz - 4, color, false);','TacticalColors.label(graphics,font,label,px+9+font.width(label)/2,pz-4,color);');f.write_text(s,'utf-8')
f=p/'CombatHud.java';s=f.read_text('utf-8').replace('g.drawString(mc.font,"青方块友 / 橙菱形敌",8,y+size+5,0xd3dfeb,false);','g.fill(6,y+size+3,8+mc.font.width("青自己 蓝友 红敌")+2,y+size+15,0xe0000000);\n            g.drawString(mc.font,"青自己 蓝友 红敌",8,y+size+5,0xa3c8dc,false);');f.write_text(s,'utf-8')
for f in [Path('build.py'),Path('gradle.properties'),Path('src/main/resources/META-INF/neoforge.mods.toml')]:
 if f.exists():f.write_text(f.read_text('utf-8').replace('0.5.4','0.5.5'),'utf-8')
s=Path('install-0.5.4.ps1').read_text('utf-8-sig').replace('0.5.4','0.5.5').replace("'battlefield-ops-0.5.3-neoforge-1.21.1.jar'","'battlefield-ops-0.5.3-neoforge-1.21.1.jar','battlefield-ops-0.5.4-neoforge-1.21.1.jar'")
Path('install-0.5.5.ps1').write_text(s,'utf-8-sig')
