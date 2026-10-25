from pathlib import Path
f=Path('test.py');s=f.read_text('utf-8').replace("cp=str(root/'build/classes')+';'+", "cp=str(root/'build/classes')+';D:/USTBL/.minecraft/libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-extra.jar;'+");f.write_text(s,'utf-8')
f=Path('sim/VehicleHudSim.java');s=f.read_text('utf-8').replace('VehicleHud.describe(VehicleHud.State.MATCH_LIMIT, 0, 6, 2).contains("6")','VehicleHud.describe(VehicleHud.State.MATCH_LIMIT, 0, 6, 2).contains("2")');f.write_text(s,'utf-8')
