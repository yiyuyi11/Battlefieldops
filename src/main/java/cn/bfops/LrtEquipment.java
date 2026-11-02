package cn.bfops;

import java.util.*;
import net.minecraft.world.item.ItemStack;

/** Optional bridge to native LRTactical indices; never manufactures a bare custom-item stack. */
final class LrtEquipment {
    private static final String PREFIX="bfops_lrtactical:";
    private static final Map<String,String> METHODS=Map.of("melee","getMeleeIndexes","throwable","getThrowableIndexes","consumable","getConsumableIndexes");
    static String label(String category){return switch(category){case "melee"->"近战刀具";case "throwable"->"投掷物";case "consumable"->"消耗品";default->"道具";};}
    private static Collection<?> indices(String category)throws ReflectiveOperationException{return (Collection<?>)Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI").getMethod(METHODS.get(category)).invoke(null);}
    private static String id(Object index)throws ReflectiveOperationException{return String.valueOf(index.getClass().getMethod("getId").invoke(index));}
    private static String name(Object index)throws ReflectiveOperationException{
        try{return String.valueOf(index.getClass().getMethod("getDescriptionId").invoke(index));}
        catch(NoSuchMethodException absent){return String.valueOf(index.getClass().getMethod("getName").invoke(index));}
    }
    private static String selection(String category,String id){return PREFIX+category+"/"+id.replaceFirst(":","/");}
    static List<Map<String,String>> entries(){
        var out=new ArrayList<Map<String,String>>();
        for(String category:List.of("melee","throwable","consumable")){
            try{for(Object index:indices(category)){String id=id(index);out.add(Map.of("id",selection(category,id),"resource",id,"name",name(index),"category",category));}}
            catch(ClassNotFoundException absent){return out;}
            catch(ReflectiveOperationException|LinkageError ex){BattlefieldOps.LOG.warn("Unable to read LRTactical {} catalogue",category,ex);}
        }
        return out;
    }
    static ItemStack create(String selection,int count){
        String category=null,id=selection;
        if(selection.startsWith(PREFIX)){
            String[] parts=selection.substring(PREFIX.length()).split("/",3);
            if(parts.length!=3||!METHODS.containsKey(parts[0]))return ItemStack.EMPTY;
            category=parts[0];id=parts[1]+":"+parts[2];
        }
        for(String kind:category==null?List.of("melee","throwable","consumable"):List.of(category)){
            try{for(Object index:indices(kind))if(id.equals(id(index))){
                var stack=((ItemStack)index.getClass().getMethod("createItemStack").invoke(index)).copy();
                if(stack.isEmpty())return ItemStack.EMPTY;
                stack.setCount(Math.clamp(count,1,stack.getMaxStackSize()));return stack;
            }}catch(ClassNotFoundException absent){return ItemStack.EMPTY;}
            catch(ReflectiveOperationException|LinkageError ex){BattlefieldOps.LOG.warn("Unable to create LRTactical item {}",selection,ex);return ItemStack.EMPTY;}
        }
        return ItemStack.EMPTY;
    }
    static boolean explicit(String id){return id.startsWith(PREFIX);}

    private static final Map<String,String> INDEX_GETTERS = Map.of(
            "throwable", "getThrowableIndex",
            "consumable", "getConsumableIndex",
            "melee", "getMeleeIndex");

    /**
     * The pseudo id of a stack's LRTactical variant, in the same form the editor stores.
     *
     * <p>The server cannot build these stacks (the index table is client-side), but it can recognise one that
     * is already in a player's inventory, which is all resupply needs: grow the stack instead of creating it.
     */
    static String idOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            Class<?> api = Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI");
            for (var entry : INDEX_GETTERS.entrySet()) {
                Object result = api.getMethod(entry.getValue(), net.minecraft.world.item.ItemStack.class)
                        .invoke(null, stack);
                if (!(result instanceof java.util.Optional<?> optional) || optional.isEmpty()) continue;
                Object index = optional.get();
                Object id = index.getClass().getMethod("getId").invoke(index);
                String text = String.valueOf(id);
                int colon = text.indexOf(':');
                if (colon <= 0) continue;
                return PREFIX + entry.getKey() + "/" + text.substring(0, colon) + "/" + text.substring(colon + 1);
            }
        } catch (ClassNotFoundException absent) {
            return "";
        } catch (ReflectiveOperationException | LinkageError broken) {
            return "";
        }
        return "";
    }



    /** True for the bare per-category items LRTactical registers, e.g. {@code lrtactical:throwable}. */
    static boolean baseItem(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) return false;
        String path = id.substring(colon + 1);
        return path.equals("throwable") || path.equals("consumable") || path.equals("melee")
                || path.equals("flash_shield") || path.equals("detonator");
    }

    /** A bare category id names no variant, so it cannot be turned into an item; report it as unusable. */
    static ItemStack resolveBaseId(String id, int count) {
        return ItemStack.EMPTY;
    }



    /**
     * Fills {@code lrtEntries}/{@code meleeEntries} from this client's own LRTactical registry when the
     * server sent none.
     *
     * <p>LRTactical resolves its melee/throwable indices from client resources, so the server-side call
     * returns an empty collection and the catalogue arrives with no bridge entries at all, which is why the
     * knives and grenades could not be picked anywhere. Read locally instead and splice the result in.
     */
    static void mergeLocal(com.google.gson.JsonObject catalog) {
        try {
            if (catalog.has("lrtEntries") && catalog.getAsJsonArray("lrtEntries").size() > 0) return;
            var array = new com.google.gson.JsonArray();
            for (var entry : entries()) {
                var json = new com.google.gson.JsonObject();
                for (var pair : entry.entrySet()) json.addProperty(pair.getKey(), pair.getValue());
                array.add(json);
            }
            if (array.size() == 0) return;
            var melee = new com.google.gson.JsonArray();
            for (var element : array) {
                if ("melee".equals(element.getAsJsonObject().get("category").getAsString())) melee.add(element);
            }
            catalog.add("lrtEntries", array);
            catalog.add("meleeEntries", melee);
        } catch (Throwable ignored) {
        }
    }

    private LrtEquipment(){}
}
