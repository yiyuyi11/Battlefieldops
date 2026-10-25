from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'Client.java';s=f.read_text('utf-8').replace('            state = payload;','''            if(Engine.str(payload,"type","").equals("fittings")){
                if(mc.screen instanceof FittingScreen screen)screen.receive(payload);return;
            }
            state = payload;
            if(mc.screen instanceof FittingScreen screen&&!Engine.str(payload,"message","").isEmpty())screen.error(Engine.str(payload,"message",""));''');f.write_text(s,'utf-8')
f=p/'BattleScreen.java';s=f.read_text('utf-8');s=s.replace('    private int tab;','''    private final Map<String,Map<String,String>> fittings=new HashMap<>();
    private Map<String,String> fitting(String kind,String id){return fittings.computeIfAbsent(kind+":"+id,k->new LinkedHashMap<>());}
    private void customize(String kind,String id){
        Model.require(!id.isEmpty(),"请先选择装备");
        minecraft.setScreen(new FittingScreen(this,selected,id,kind,fitting(kind,id)));
    }
    private int tab;''');s=s.replace('row(f, "主武器", name(kit.primary)','row(f, "主武器", name(first(kit,true))');s=s.replace('            row(f, "副武器", kit.secondary.isEmpty() ? "无" : name(kit.secondary),','            row(f,"改装主武器",fitting("gun",first(kit,true)).size()+" 项",!first(kit,true).isEmpty(),()->customize("gun",first(kit,true)));\n            row(f, "副武器", first(kit,false).isEmpty() ? "无" : name(first(kit,false)),');needle='                    "gun", first(kit, false), () -> weapon(kit, false));';s=s.replace(needle,needle+'\n            row(f,"改装副武器",fitting("gun",first(kit,false)).size()+" 项",!first(kit,false).isEmpty(),()->customize("gun",first(kit,false)));');s=s.replace('        // --- deployment','        // --- deployment',1);s=s.replace('        vehicleFacts(f);','        if(!vehicle.isEmpty())row(f,"载具弹种",fitting("vehicle",vehicle).size()+" 项",()->customize("vehicle",vehicle));\n        vehicleFacts(f);');s=s.replace('        request.addProperty("secondary", secondary);','''        request.addProperty("secondary", secondary);
        Model.Kit selectedKit=kit();
        request.add("primaryFittings",BattlefieldOps.GSON.toJsonTree(fitting("gun",first(selectedKit,true))));
        request.add("secondaryFittings",BattlefieldOps.GSON.toJsonTree(fitting("gun",first(selectedKit,false))));
        request.add("vehicleFittings",BattlefieldOps.GSON.toJsonTree(fitting("vehicle",vehicle)));''');f.write_text(s,'utf-8')
