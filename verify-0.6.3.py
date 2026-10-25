from pathlib import Path
p=Path('src/main/java/cn/bfops/VehicleKillCompat.java');s=p.read_text('utf-8').replace('    static void check(Entity entity) {','    private static final java.util.Map<Entity,Boolean> aliveSeen = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());\n    static void check(Entity entity) {');s=s.replace('if(((Number)a.health.invoke(entity)).floatValue()<=0 && a.attacker.invoke(entity) instanceof ServerPlayer player)\n                BattlefieldOps.engine.cosmeticKills.report(entity,player,false,"摧毁载具");','boolean alive=((Number)a.health.invoke(entity)).floatValue()>0;\n            Boolean previous=aliveSeen.put(entity,alive);\n            if(!alive && Boolean.TRUE.equals(previous) && a.attacker.invoke(entity) instanceof ServerPlayer player)\n                BattlefieldOps.engine.cosmeticKills.report(entity,player,false,"摧毁载具");');p.write_text(s,'utf-8')
p=Path('sim/FeedSim.java');s=p.read_text('utf-8');needle='public static void main(String[] args) {';s=s.replace(needle,needle+'''
        Feed.Popup cosmetic = new Feed.Popup("击杀",0,1000,"僵尸","kill");
        check(cosmetic.text().equals("击杀"), "non-player kill has no points label, including no +0");
        check(cosmetic.detailText().equals("僵尸"), "cosmetic kill names the victim");
        check(Feed.alpha(cosmetic,1100)>0, "zero-point kill still animates");
        check(Feed.prune(java.util.List.of(cosmetic),1000+Feed.LIFETIME_MS).isEmpty(), "cosmetic kill still expires");
''');p.write_text(s,'utf-8')
