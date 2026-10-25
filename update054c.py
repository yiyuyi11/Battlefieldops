from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'OpsScreen.java';s=f.read_text('utf-8').replace('Math.rint(worldXAt(body, mouseX))','Math.floor(worldXAt(body, mouseX))').replace('Math.rint(worldZAt(body, mouseY))','Math.floor(worldZAt(body, mouseY))');s=s.replace('地图已写入服务器（已保留上一版备份）','保存成功！点击底部「作战房间」创建房间');f.write_text(s,'utf-8')
f=p/'Layout.java';s=f.read_text('utf-8').replace('int footerHeight = 10;','int footerHeight = 22;');f.write_text(s,'utf-8')
f=p/'FrontScreen.java';s=f.read_text('utf-8');a=s.index('            graphics.drawString(font, Ui.clip(font, message, footer.w() - 4)');b=s.index('\n        }',a);s=s[:a]+'''            var lines=font.split(Component.literal(message),Math.max(1,footer.w()-4));
            for(int i=0;i<Math.min(2,lines.size());i++)graphics.drawString(font,lines.get(i),footer.x()+2,footer.y()+i*10,messageColor,false);'''+s[b:];f.write_text(s,'utf-8')
