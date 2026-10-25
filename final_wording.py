from pathlib import Path
p=Path('src/main/java/cn/bfops/VehicleEquip.java');s=p.read_text('utf-8').replace('append(" 件，覆盖 ")','append(" 件；识别 ")');p.write_text(s,'utf-8')
