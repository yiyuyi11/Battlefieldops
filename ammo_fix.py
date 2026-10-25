from pathlib import Path
p=Path('src/main/java/cn/bfops/VehicleEquip.java');s=p.read_text('utf-8');s=s.replace('int placed = fillAmmo(vehicle, ammo, VehicleSupply.rounds(kit, weapons));','int placed = topUp(vehicle, VehicleSupply.rounds(kit, weapons));');s=s.replace('int have = ammoRounds(vehicle);\n                if (have < want && fillAmmo(vehicle, ammo, want - have) > 0) changed = true;','if (topUp(vehicle, want) > 0) changed = true;');s=s.replace('private static Object weapons(Entity vehicle)','static Object weapons(Entity vehicle)');a=s.index('    static List<String> ammoTypes(Entity vehicle) {');b=s.index('    /** Ammo ids with display names',a);s=s[:a]+'''    static List<String> ammoTypes(Entity vehicle) {
        return ammoStacks(vehicle).stream().map(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).distinct().toList();
    }
    static List<ItemStack> ammoStacks(Entity vehicle){
        List<ItemStack> stacks=new ArrayList<>();
        try{
            Object all=weapons(vehicle);
            if(all instanceof Map<?,?> map)for(Object weapon:map.values()){
                try{
                    for(Object consumer:(Iterable<?>)weapon.getClass().getMethod("getProcessedAmmoConsumers").invoke(weapon)){
                        consumer.getClass().getMethod("init").invoke(consumer);
                        ItemStack stack=(ItemStack)consumer.getClass().getMethod("getStack").invoke(consumer);
                        if(!stack.isEmpty()&&stacks.stream().noneMatch(s->ItemStack.isSameItemSameComponents(s,stack)))stacks.add(stack.copyWithCount(1));
                    }
                }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("Cannot resolve vehicle weapon ammunition",ex);}
            }
        }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("Cannot read vehicle armament",ex);}
        return stacks;
    }
    static int topUp(Entity vehicle,int total){
        try{
            Object raw=vehicle.getClass().getMethod("getInventory").invoke(vehicle);
            if(!(raw instanceof net.neoforged.neoforge.items.IItemHandler inventory))return 0;
            return AmmoAllocation.topUp(inventory,ammoStacks(vehicle),total);
        }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("Cannot supply vehicle ammunition",ex);return 0;}
    }

'''+s[b:];p.write_text(s,'utf-8')
