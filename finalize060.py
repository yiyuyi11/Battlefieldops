from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'FittingScreen.java';s=f.read_text('utf-8').replace('Math.max(24,pane.h()-76)','Math.max(24,pane.h()-112)');s=s.replace('        TacticalColors.label(g,font,"已选','''        int tile=26,step=30,shown=0;
        for(var group:groups){
            String selected=working.getOrDefault(group.id(),"");
            var option=group.options().stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);
            if(option==null||shown>=Math.max(1,(pane.w()-16)/step))continue;
            int x=pane.x()+8+shown++*step,y=pane.bottom()-80;
            Ui.sunken(g,new Layout.Rect(x,y,tile,tile));AssetIcons.draw(g,option.kind(),option.icon(),x+3,y+3,20,20);
            if(mx>=x&&mx<x+tile&&my>=y&&my<y+tile)TacticalColors.label(g,font,option.name(),pane.cx(),y-12,Ui.ACCENT);
        }
        TacticalColors.label(g,font,"已选''');f.write_text(s,'utf-8')
f=p/'OpsScreen.java';s=f.read_text('utf-8').replace('Engine.num(vehicleState, "limit", draft.vehicleLimit)','Engine.num(vehicleState, "limit", draft.vehicleLimitFor(selfTeam()))');f.write_text(s,'utf-8')
f=p/'VehicleDeploy.java';s=f.read_text('utf-8').replace('本局载具数已达上限','本方载具名额已满');f.write_text(s,'utf-8')
f=p/'VehicleHud.java';s=f.read_text('utf-8').replace('int matchLimit, int vehicleLimit) {','int used, int limit) {').replace('"可部署 · 本方已用 " + matchLimit + "/" + vehicleLimit','"可部署 · 本方已用 " + used + "/" + limit').replace('vehicleLimit==0?"本方载具已禁用":"本方名额已满 " + vehicleLimit','limit==0?"本方载具已禁用":"本方名额已满 " + limit');f.write_text(s,'utf-8')
f=p/'BattleScreen.java';s=f.read_text('utf-8').replace('if (!kit.options(true).contains(primary)) primary = kit.primary;','if (!kit.options(true).contains(primary)) primary = kit.options(true).getFirst();').replace('if (!kit.options(false).contains(secondary)) secondary = kit.secondary;','if (!kit.options(false).contains(secondary)) secondary = kit.options(false).getFirst();');s=s.replace('request.addProperty("primary", primary);','request.addProperty("primary", first(kit(),true));').replace('request.addProperty("secondary", secondary);','request.addProperty("secondary", first(kit(),false));');f.write_text(s,'utf-8')
f=p/'VehicleEquip.java';s=f.read_text('utf-8');a=s.index('    private static int ammoRounds(');b=s.index('    /** Heals a player',a);s=s[:a]+s[b:];a=s.index('    private static int fillAmmo(');b=s.index('    private static int containerSize',a);s=s[:a]+s[b:];a=s.index('    private static boolean isSlotEmpty');b=s.index('    private static int charge',a);s=s[:a]+s[b:];s=s.replace('if (placed > 0) text.append("已配弹药 ").append(placed).append(" 发");','if (placed > 0) text.append("已配弹药 ").append(placed).append(" 件，覆盖 ").append(ammo.size()).append(" 种弹药定义");\n        else if(!ammo.isEmpty())text.append("警告：弹药舱未接收弹药，请检查车型弹药槽限制");');f.write_text(s,'utf-8')
for f in [Path('build.py'),Path('gradle.properties'),Path('src/main/resources/META-INF/neoforge.mods.toml')]:
 if f.exists():f.write_text(f.read_text('utf-8').replace('0.5.9','0.6.0'),'utf-8')
s=Path('install-0.5.9.ps1').read_text('utf-8-sig').replace('0.5.9','0.6.0').replace("'battlefield-ops-0.5.8-neoforge-1.21.1.jar'","'battlefield-ops-0.5.8-neoforge-1.21.1.jar','battlefield-ops-0.5.9-neoforge-1.21.1.jar'")
Path('install-0.6.0.ps1').write_text(s,'utf-8-sig')
