package cn.bfops;
import java.util.*;
import net.neoforged.fml.ModList;

final class LibrarySource {
    private static final Map<String,String> names=new HashMap<>();
    static String name(String id){
        String namespace=id.contains(":")?id.substring(0,id.indexOf(':')):id;
        return names.computeIfAbsent(namespace,key->ModList.get().getModContainerById(key)
                .map(mod->mod.getModInfo().getDisplayName()).orElse(key));
    }
    static String searchable(String id,String kind){
        String namespace=id.contains(":")?id.substring(0,id.indexOf(':')):id;
        return namespace+" "+name(id)+(kind.equals("gun")?" tacz Timeless and Classics Zero 永恒枪械":"")
                +(namespace.equals("superbwarfare")?" 卓越前线":"");
    }
}
