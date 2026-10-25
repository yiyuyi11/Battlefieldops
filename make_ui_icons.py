from pathlib import Path
from PIL import Image,ImageDraw
out=Path('src/main/resources/assets/battlefield_ops/textures/gui');out.mkdir(parents=True,exist_ok=True)
s=4;im=Image.new('RGBA',(192*s,24*s));d=ImageDraw.Draw(im)
colors=['#64bddc']*5+['#619fea','#e96772','#42d9df']
for i,c in enumerate(colors):
 def line(coords,width=2):d.line([(int((x+i*24)*s),int(y*s)) for x,y in coords],fill=c,width=width*s,joint='curve')
 def box(coords):d.rounded_rectangle(tuple(int(v*s) for v in (coords[0]+i*24,coords[1],coords[2]+i*24,coords[3])),radius=2*s,outline=c,width=2*s)
 if i==0:
  box((4,3,20,21));box((8,3,16,9));box((8,14,16,21))
 elif i==1:
  box((3,3,10,10));box((14,3,21,10));box((3,14,10,21));box((14,14,21,21))
 elif i==2:
  d.ellipse((4*s+i*24*s,3*s,16*s+i*24*s,15*s),outline=c,width=2*s);line([(15,15),(21,21)],3)
 elif i==3:line([(12,4),(12,20)]);line([(4,12),(20,12)])
 elif i==4:line([(14,5),(7,12),(14,19)]);line([(7,12),(21,12)])
 elif i==5:
  d.rounded_rectangle((5*s+i*24*s,5*s,19*s+i*24*s,19*s),radius=3*s,fill=c,outline='#08121c',width=2*s)
 elif i==6:
  d.polygon([(int((x+i*24)*s),int(y*s)) for x,y in [(12,2),(22,12),(12,22),(2,12)]],fill='#08121c');d.polygon([(int((x+i*24)*s),int(y*s)) for x,y in [(12,5),(19,12),(12,19),(5,12)]],fill=c)
 else:line([(12,3),(20,20),(12,16),(4,20),(12,3)])
im.resize((192,24),Image.Resampling.LANCZOS).save(out/'tactical_icons.png')
