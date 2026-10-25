from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'Model.java';s=f.read_text('utf-8').replace('public int minY=-64,maxY=320,vehicleLimit=6;','''public int minY=-64,maxY=320,vehicleLimit=6;
        public int blueVehicleLimit=-1,redVehicleLimit=-1;
        public int vehicleLimitFor(int team){return team==0?(blueVehicleLimit<0?vehicleLimit:blueVehicleLimit):team==1?(redVehicleLimit<0?vehicleLimit:redVehicleLimit):0;}''');s=s.replace('range(vehicleLimit,1,32,"载具上限");','range(vehicleLimit,1,32,"载具上限");range(blueVehicleLimit,-1,32,"蓝方载具名额");range(redVehicleLimit,-1,32,"红方载具名额");');f.write_text(s,'utf-8')
f=p/'Engine.java';s=f.read_text('utf-8').replace('match.vehicles.size() < map.vehicleLimit','teamVehicles(match,member.team) < map.vehicleLimitFor(member.team)').replace('"本局载具数已达上限"','"本方载具名额已满"').replace('match.vehicles.size(), map.vehicleLimit','teamVehicles(match,member.team), map.vehicleLimitFor(member.team)').replace('map.vehicleLimit, map.vehicleLimit','teamVehicles(match,member.team), map.vehicleLimitFor(member.team)').replace('out.put("limit", map.vehicleLimit);','out.put("limit", map.vehicleLimitFor(member.team));').replace('out.put("used", match.vehicles.size());','out.put("used", teamVehicles(match,member.team));');idx=s.index('    private Entity findEntity');s=s[:idx]+'''    private int teamVehicles(Match match,int team){
        return (int)match.vehicles.stream().filter(id->match.vehicleTeams.getOrDefault(id,-1)==team).filter(id->{Entity e=findEntity(id);return e!=null&&e.isAlive();}).count();
    }

'''+s[idx:];f.write_text(s,'utf-8')
f=p/'OpsScreen.java';s=f.read_text('utf-8').replace('number("本局同时载具数", draft.vehicleLimit, value -> draft.vehicleLimit = value, 1, 32);','number("蓝方载具名额（0=禁用）", draft.vehicleLimitFor(0), value -> draft.blueVehicleLimit = value, 0, 32);\n        number("红方载具名额（0=禁用）", draft.vehicleLimitFor(1), value -> draft.redVehicleLimit = value, 0, 32);').replace('{"载具上限", "" + draft.vehicleLimit}','{"载具名额", "蓝 "+draft.vehicleLimitFor(0)+" / 红 "+draft.vehicleLimitFor(1)}');f.write_text(s,'utf-8')
for n in ['BattleScreen.java','OpsScreen.java','VehicleHud.java']:
 f=p/n;s=f.read_text('utf-8').replace('本局载具名额','本方载具名额').replace('本局载具上限','本方载具上限').replace('本局载具数','本方载具数');f.write_text(s,'utf-8')
