from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
S=3
im=Image.new('RGB',(960,390),'#101d28');d=ImageDraw.Draw(im)
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',27)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
d.text((30,20),'战地风云 · 0.6.2 击杀卡片样式预览',font=font,fill='#c6e2e8')
d.text((30,63),'代码绘制样式示意；实际游戏使用 Minecraft 字体',font=small,fill='#8da9b8')
skull=['...#####...','..#######..','.#########.','.#########.','##..###..##','##..###..##','.####.####.','..##...##..','..#######..','...#.#.#...','...#.#.#...']
def card(x,y,label,detail,points,head=False):
 w=206*S;h=30*S;accent='#ed5969' if head else '#65dbe8';ink='#dce8ec'
 d.rectangle((x,y,x+w-1,y+h-1),fill='#09141c')
 d.rectangle((x,y,x+12*S,y+S-1),fill=accent);d.rectangle((x,y,x+S-1,y+8*S),fill=accent)
 d.rectangle((x+w-12*S,y+29*S,x+w-1,y+30*S-1),fill='#3f5964')
 d.rectangle((x+w-S,y+22*S,x+w-1,y+30*S-1),fill='#3f5964')
 for row,line in enumerate(skull):
  for col,v in enumerate(line):
   if v=='#':d.rectangle((x+(6+col)*S,y+(7+row)*S,x+(7+col)*S-1,y+(8+row)*S-1),fill='#ff5c68' if head else '#ffffff')
 txtx=x+24*S; fw=d.textlength(label,font=font)+6*S
 d.rectangle((txtx-2*S,y+3*S,txtx+fw,y+15*S),fill='#203640')
 d.text((txtx+S,y+2*S),label,font=font,fill=accent)
 d.text((x+w-7*S-d.textlength(points,font=font),y+2*S),points,font=font,fill=ink)
 d.text((txtx,y+16*S),detail,font=font,fill=ink)
 d.rectangle((x,y+29*S,x+int(w*.73),y+30*S-1),fill=accent)
card(30,108,'击杀','敌方步枪手','+100')
card(30,218,'爆头','敌方侦察兵','+150',True)
d.text((690,140),'白色骷髅\n青色细框\n独立分数栏',font=small,fill='#b8d3dc',spacing=8)
d.text((690,245),'红色骷髅\n红色强调\n深色承托文字',font=small,fill='#b8d3dc',spacing=8)
d.text((30,340),'乘车：侧上方自动避让  ·  载具开镜：暂缓显示  ·  下镜后补显',font=small,fill='#8da9b8')
im.save('build/kill-card-preview-0.6.2.png')
