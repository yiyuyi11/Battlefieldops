package cn.bfops;
import java.util.*;
import java.util.function.Consumer;

/** Owns transformed textures: every eviction and reset disposes the corresponding allocation. */
final class IconVariantCache<V> {
    private record Entry<V>(V value,long bytes) {}
    private final LinkedHashMap<String,Entry<V>> entries=new LinkedHashMap<>(16,.75f,true);
    private final int limit;
    private final long budget;
    private final Consumer<V> release;
    private long bytes;
    IconVariantCache(int limit,long budget,Consumer<V> release){this.limit=limit;this.budget=budget;this.release=release;}
    V get(String key){var entry=entries.get(key);return entry==null?null:entry.value();}
    void put(String key,V value,long cost){
        if(cost<=0||cost>budget)throw new IllegalArgumentException("Icon exceeds cache budget");
        var old=entries.remove(key);
        if(old!=null){bytes-=old.bytes();release.accept(old.value());}
        while(!entries.isEmpty()&&(entries.size()>=limit||bytes+cost>budget)){
            var iterator=entries.entrySet().iterator();var entry=iterator.next().getValue();iterator.remove();
            bytes-=entry.bytes();release.accept(entry.value());
        }
        entries.put(key,new Entry<>(value,cost));bytes+=cost;
    }
    void clear(){for(var entry:entries.values())release.accept(entry.value());entries.clear();bytes=0;}
    int size(){return entries.size();}
    long bytes(){return bytes;}
}
