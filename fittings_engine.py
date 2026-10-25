from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'Engine.java';s=f.read_text('utf-8').replace('case "catalog" -> catalog(player, request);','case "catalog" -> catalog(player, request);\n                case "fittings" -> fittings(player,request);');s=s.replace('str(request,"primary",null),str(request,"secondary",null));','str(request,"primary",null),str(request,"secondary",null),request);');s=s.replace('String primary, String secondary) throws Exception {','String primary, String secondary, JsonObject deployment) throws Exception {');a=s.index('        List<ItemStack> items = new ArrayList<>(catalog.gun');b=s.index('        Model.require(items.size()',a);s=s[:a]+'''        List<ItemStack> mainItems=catalog.gun(player,kit.select(true,primary),kit.ammoStacks);
        List<ItemStack> sideItems=catalog.gun(player,kit.select(false,secondary),kit.ammoStacks);
        Map<String,String> mainFittings=selections(deployment,"primaryFittings"),sideFittings=selections(deployment,"secondaryFittings"),vehicleFittings=selections(deployment,"vehicleFittings");
        if(!mainItems.isEmpty())Fittings.applyGun(mainItems.getFirst(),mainFittings,player.registryAccess());
        else Model.require(mainFittings.isEmpty(),"没有主武器可改装");
        if(!sideItems.isEmpty())Fittings.applyGun(sideItems.getFirst(),sideFittings,player.registryAccess());
        else Model.require(sideFittings.isEmpty(),"没有副武器可改装");
        if(vehicleType!=null){Entity sample=vehicleType.create(level(map));Model.require(sample!=null,"载具数据不可用");Fittings.validate(Fittings.vehicleOptions(sample),vehicleFittings);}
        else Model.require(vehicleFittings.isEmpty(),"未选择载具");
        List<ItemStack> items=new ArrayList<>(mainItems);items.addAll(sideItems);
'''+s[b:];s=s.replace('vehicleType, spawn);','vehicleType, spawn,vehicleFittings);');s=s.replace('Member member, EntityType<?> vehicleType, Deploy.Site site) {','Member member, EntityType<?> vehicleType, Deploy.Site site,Map<String,String> fittings) {');s=s.replace('        vehicle.addTag("bfops_vehicle");','''        try{Fittings.applyVehicle(vehicle,fittings);}catch(ReflectiveOperationException|IllegalArgumentException ex){vehicle.discard();return "载具弹种应用失败："+ex.getMessage();}
        vehicle.addTag("bfops_vehicle");''');s=s.replace('        VehicleEquip.equip(vehicle, VehicleEquip.kindOf(vehicle));','        String equipSummary=VehicleEquip.equip(vehicle, VehicleEquip.kindOf(vehicle));\n        player.sendSystemMessage(Component.literal("载具配装："+equipSummary));');idx=s.index('    private void deploy(ServerPlayer');s=s[:idx]+'''    static Map<String,String> selections(JsonObject request,String key){
        Map<String,String> out=new LinkedHashMap<>();
        if(!request.has(key))return out;
        for(var entry:request.getAsJsonObject(key).entrySet()){
            Model.require(out.size()<32&&entry.getKey().length()<128,"改装参数过多或过长");
            String value=entry.getValue().getAsString();Model.require(value.length()<160,"配件 ID 过长");out.put(entry.getKey(),value);
        }
        return out;
    }
    private void fittings(ServerPlayer player,JsonObject request)throws Exception{
        Member member=member(player);Model.Arena map=maps.get(member.map);
        Model.require(map!=null,"请先进入房间");
        String id=str(request,"id",""),kind=str(request,"kind","");
        List<Fittings.Group> groups;
        if(kind.equals("gun")){
            Model.require(map.kits.stream().anyMatch(k->k.options(true).contains(id)||k.options(false).contains(id)),"武器未获本图授权");
            groups=Fittings.gunOptions(catalog.gun(player,id,0).getFirst());
        }else{
            Model.require(kind.equals("vehicle")&&map.vehicles.contains(id),"载具未获本图授权");
            EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            Entity sample=type.create(level(map));Model.require(sample!=null&&catalog.isVehicleEntity(sample),"载具不可用");
            groups=Fittings.vehicleOptions(sample);
        }
        Network.send(player,Map.of("type","fittings","id",id,"kind",kind,"groups",groups));
    }

'''+s[idx:];f.write_text(s,'utf-8')
f=p/'AssetIcons.java';s=f.read_text('utf-8');s=s.replace('        if(stack.isEmpty() && !id.isEmpty())','        if(kind.equals("attachment")){try{stack=Fittings.attachment(id);}catch(ReflectiveOperationException ex){BattlefieldOps.LOG.debug("Attachment icon unavailable",ex);}}\n        if(stack.isEmpty() && !id.isEmpty())');s=s.replace('!kind.equals("role"))','!kind.equals("role")&&!kind.equals("attachment")&&!kind.equals("item"))');f.write_text(s,'utf-8')
f=p/'ChoiceScreen.java';s=f.read_text('utf-8').replace('record Choice(String id, String name, String kind) {}','record Choice(String id,String name,String kind,String icon){Choice(String id,String name,String kind){this(id,name,kind,id);}}');s=s.replace('LibrarySource.searchable(c.id,c.kind)','LibrarySource.searchable(c.icon,c.kind)');s=s.replace('choice.kind, choice.id, true','choice.kind, choice.icon, true').replace('choice.kind, choice.id, icon.x()','choice.kind, choice.icon, icon.x()');f.write_text(s,'utf-8')
