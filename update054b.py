from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'Minimap.java';s=f.read_text('utf-8');a=s.index('                if (hostile) {',s.index('JsonArray mates'));b=s.index('                // Riders',a);s=s[:a]+'                TacticalColors.person(graphics,px,py,hostile);\n'+s[b:];s=s.replace('hostile ? RED : 0xff88ddff','hostile ? TacticalColors.ENEMY : TacticalColors.FRIEND').replace('color(owner)|0xff000000','TacticalColors.objective(owner,selfTeam)').replace('friendly ? 0xff66e0ff : 0xffff5555','friendly ? TacticalColors.FRIEND : TacticalColors.ENEMY');f.write_text(s,'utf-8')
f=p/'MapPicker.java';s=f.read_text('utf-8');a=s.index('            if (hostile) {');b=s.index('\n            }',s.index('            } else {',a))+14;s=s[:a]+'            TacticalColors.person(graphics,px,pz,hostile);'+s[b:];s=s.replace('owner == 0 ? Ui.BLUE : owner == 1 ? Ui.RED : 0xffcccccc','TacticalColors.objective(owner,selfTeam)');f.write_text(s,'utf-8')
f=p/'CombatHud.java';s=f.read_text('utf-8');s=s.replace('"K/D "+Engine.num(me,"kills",0)+"/"+Engine.num(me,"deaths",0)','"青方块友 / 橙菱形敌"');s=s.replace('Minimap.color(owner)|0xff000000','TacticalColors.objective(owner,Engine.num(me,"team",-1))');s=s.replace('if(!ObjectiveZone.contains(p,mc.player.getX(),mc.player.getY(),mc.player.getZ()))continue;','if(!me.get("alive").getAsBoolean()||Engine.num(state,"objective",-1)!=i)continue;');s=s.replace('p.id+" · "+progress+"%"','p.id+" "+(f.has("contested")&&f.get("contested").getAsBoolean()?"争夺中":Engine.num(f,"owner",-1)==Engine.num(me,"team",-1)?"我方控制":"占领中")');f.write_text(s,'utf-8')
f=p/'Engine.java';s=f.read_text('utf-8');s=s.replace('            out.put("flags", match.rules.flags);','''            out.put("flags", match.rules.flags);
            int objective=-1;
            Model.Arena activeMap=match.rules.map;
            if(me.alive&&player.isAlive()&&!player.isSpectator()&&me.outside==0
                    &&player.level().dimension().location().toString().equals(activeMap.dimension)){
                for(int i=0;i<activeMap.points.size();i++)if(match.rules.active(i)&&ObjectiveZone.contains(activeMap.points.get(i),player.getX(),player.getY(),player.getZ())){objective=i;break;}
            }
            out.put("objective",objective);''');f.write_text(s,'utf-8')
f=p/'BattleScreen.java';s=f.read_text('utf-8');s=s.replace('vehicle = cycleGround(options);\n                    rebuild();','chooseVehicle(options,"选择地面载具");');a=s.index('                    String picked = cycle(options, current);');b=s.index('                    rebuild();',a)+len('                    rebuild();');s=s[:a]+'                    chooseVehicle(options,"选择航空器");'+s[b:];marker='    private String name(String id) {';s=s.replace(marker,'''    private void chooseVehicle(List<String> options,String title){
        var choices=options.stream().map(id->new ChoiceScreen.Choice(id,id.isEmpty()?"不部署载具":name(id),"vehicle")).toList();
        minecraft.setScreen(new ChoiceScreen(this,title,choices,id->{vehicle=id;}));
    }

'''+marker);f.write_text(s,'utf-8')
