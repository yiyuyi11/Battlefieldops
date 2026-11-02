package cn.bfops;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Capacity is captured from issued equipment, never from an item/count supplied by a client. */
final class GadgetRefill {
    private static final class Loadout {
        final String map,kit;final long life;
        final EnumMap<SupportRules.Kind,Integer> caps=new EnumMap<>(SupportRules.Kind.class);
final EnumMap<SupportRules.Kind,Integer> slots=new EnumMap<>(SupportRules.Kind.class);
        final EnumMap<SupportRules.Kind,Long> ready=new EnumMap<>(SupportRules.Kind.class);
        Loadout(Engine.Member m){map=m.map;kit=m.kit+"/"+m.eliteType;life=m.deploySequence;}
        boolean matches(Engine.Member m){return m!=null&&map.equals(m.map)&&kit.equals(m.kit+"/"+m.eliteType)&&life==m.deploySequence;}
    }
    private final Engine engine;private final MinecraftServer server;
    private final Map<UUID,Loadout> loadouts=new HashMap<>();
    private long nextCheck=Long.MAX_VALUE;
    GadgetRefill(Engine engine,MinecraftServer server){this.engine=engine;this.server=server;}
    void issued(ServerPlayer player){
        var m=engine.members.get(player.getUUID());if(m==null)return;
        var loadout=new Loadout(m);
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);var kind=ConquestEquipment.kind(stack);
            if(kind!=null){loadout.caps.merge(kind,stack.getCount(),Integer::sum);loadout.slots.putIfAbsent(kind,i);}
        }
        loadouts.put(player.getUUID(),loadout);
    }
    /**
     * Puts a refilled gadget back where it was, so a medic box returns to its tactical slot instead of landing
     * in the backpack (vanilla add() fills the main inventory rows first).
     */
    /** Returns how many items actually landed in the inventory, so the caller can judge success. */
    /** Returns how many items actually landed in the inventory, so the caller can judge success. */
    /** Returns how many items actually landed in the inventory, so the caller can judge success. */
    /** The slot the engine issued this gadget into, which survives a click that emptied the slot. */
    private int issuedSlotFor(ServerPlayer player, SupportRules.Kind kind, int fallback) {
        var member = engine.members.get(player.getUUID());
        if (member == null || member.issuedSlots == null) return fallback;
        Integer slot = member.issuedSlots.get(kind.key());
        return slot == null ? fallback : slot;
    }

    private static int putBack(ServerPlayer player, ItemStack stack, int preferred) {
        var inventory = player.getInventory();
        int before = stack.getCount();

        // Back to the exact slot it came from: merge if the same item is already there.
        if (preferred >= 0 && preferred < inventory.getContainerSize()) {
            ItemStack existing = inventory.getItem(preferred);
            if (existing.isEmpty()) {
                inventory.setItem(preferred, stack);
                return before;
            }
            if (!existing.isEmpty() && existing.is(stack.getItem())) {
                int room = existing.getMaxStackSize() - existing.getCount();
                if (room > 0) {
                    int moved = Math.min(room, stack.getCount());
                    existing.grow(moved);
                    stack.shrink(moved);
                    if (stack.isEmpty()) return before;
                }
            }
        }
        // Then the combat rows 3-8, so a gadget never displaces a weapon in 0-2.
        for (int slot = 3; slot <= 8; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack);
                return before - 0;
            }
        }
        for (int slot = 0; slot <= 2; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack);
                return before;
            }
        }
        inventory.add(stack);
        return before - stack.getCount();
    }

    /** How many of this gadget deployment actually handed out; never refill past that. */
    private int issuedCount(ServerPlayer player, SupportRules.Kind kind, int fallback) {
        var member = engine.members.get(player.getUUID());
        if (member == null || member.issuedTemplates == null) return fallback;
        var template = member.issuedTemplates.get(kind.key());
        if (template == null || template.isEmpty()) return Math.min(fallback, 1);
        return Math.min(fallback, Math.max(1, template.getCount()));
    }

    private int count(ServerPlayer p,SupportRules.Kind kind){
        int total=0;
        String wanted=kind.key();
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            var stack=p.getInventory().getItem(i);
            if(stack.isEmpty())continue;
            // Count by registered id as well: the real support items carry no kind tag, so the old
            // lookup reported zero and the box kept handing out another copy every cooldown.
            if(ConquestEquipment.kind(stack)==kind)total+=stack.getCount();
            else if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wanted))total+=stack.getCount();
        }
        return total;
    }
    void used(ServerPlayer player,SupportRules.Kind kind,long now){
        var loadout=loadouts.get(player.getUUID());var m=engine.members.get(player.getUUID());
        if(loadout==null||!loadout.matches(m)||!loadout.caps.containsKey(kind))return;
        var map=engine.supportMap(m);if(map==null)return;int seconds=map.refillSeconds(kind);
        // Only start a cooldown when the item is really gone: if the use path failed to consume it, scheduling a
    // refill here is what made a second bag appear the instant one was used.
    if(seconds>0&&count(player,kind)==0){
            long due=now+seconds*20L;loadout.ready.put(kind,due);nextCheck=Math.min(nextCheck,due);
        }
    }
    void tick(long now){
        if(now<nextCheck)return;
        nextCheck=Long.MAX_VALUE;
        for(var entry:loadouts.entrySet()){
            var loadout=entry.getValue();var m=engine.members.get(entry.getKey());
            if(!loadout.matches(m)||!engine.supportActive(m))continue;
            if(!m.alive||m.downed){if(!loadout.ready.isEmpty())nextCheck=Math.min(nextCheck,now+20);continue;}
            var player=server.getPlayerList().getPlayer(entry.getKey());if(player==null)continue;
            boolean changed=false;
            for(var kind:new ArrayList<>(loadout.ready.keySet())){
                if(now<loadout.ready.get(kind)){nextCheck=Math.min(nextCheck,loadout.ready.get(kind));continue;}
                // One unit per finished cooldown: the old code topped the whole stack back up at once, which is why a
        // second bag appeared in the backpack the moment the first one was used.
        int cap=issuedCount(player,kind,loadout.caps.get(kind));
        int missing=Math.min(1,cap-count(player,kind));
                while(missing>0){
                    ItemStack stack=ConquestEquipment.stack(kind.key(),missing);int before=stack.getCount();
        int added=putBack(player,stack,issuedSlotFor(player,kind,loadout.slots.getOrDefault(kind,-1)));
        if(added<=0)break;
                    changed=true;missing-=added;
                }
                if(missing<=0)loadout.ready.remove(kind);
                else nextCheck=Math.min(nextCheck,now+20);
            }
            if(changed){player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();engine.supportRefresh(player);}
        }
    }
    List<Map<String,Object>> snapshot(ServerPlayer player,long now){
        var loadout=loadouts.get(player.getUUID());if(loadout==null||!loadout.matches(engine.members.get(player.getUUID())))return List.of();
        var out=new ArrayList<Map<String,Object>>();
        for(var kind:loadout.caps.keySet())out.add(Map.of("kind",kind.id,"label",kind.label,"count",count(player,kind),"capacity",loadout.caps.get(kind),"remaining",Math.max(0,(loadout.ready.getOrDefault(kind,now)-now+19)/20)));
        return out;
    }
    void leave(UUID id){loadouts.remove(id);}
    void clear(){loadouts.clear();nextCheck=Long.MAX_VALUE;}
}
