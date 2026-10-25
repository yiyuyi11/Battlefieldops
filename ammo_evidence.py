from pathlib import Path
import zipfile,json,collections
root=Path('D:/USTBL/.minecraft/versions/Mechanomania-航空学/mods')
for p in root.glob('*.jar'):
 if not any(n in p.name for n in ['superbwarfare-','doggiesnewvehicle','dragonrise']):continue
 hits=[]
 with zipfile.ZipFile(p) as z:
  for n in z.namelist():
   if '/vehicles/' not in n or not n.endswith('.json'):continue
   try:o=json.loads(z.read(n))
   except:continue
   text=json.dumps(o,ensure_ascii=False)
   if '@' in text and 'Weapons' in text:hits.append((n,[v for v in __import__('re').findall(r'"[^"\n]*@[^"\n]*"',text)][:6]))
 print(p.name, 'examples',hits[:4], 'matching vehicles',len(hits))
