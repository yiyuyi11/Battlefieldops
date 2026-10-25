package cn.bfops;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Every item the game has registered, for the kit's personal-item / armour / accessory pickers.
 *
 * <p><b>Why this runs on the client, not the server.</b> The item registry is synchronised to clients, so
 * the client already holds exactly the same list. Asking the server for it — which is what the first
 * version did — bought nothing and cost three things: a paged protocol, a payload too large to send whole,
 * and names translated with the *server's* language. That last one is the defect an operator hits
 * immediately: a Chinese client on an English server could not find 铁胸甲 because the server had sent
 * "Iron Chestplate". Building the list locally fixes the language, drops the round trip, and makes search
 * instant instead of one request per keystroke.
 *
 * <p><b>Authority is unaffected.</b> The client only ever sends a kit id to the server when deploying;
 * the server resolves and validates every item itself. A client that could enumerate items was always able
 * to; that is not what made the loadout trustworthy.
 */
final class ItemCatalog {
    /** Built once: enumerating the registry and translating every name is far too costly per keystroke. */
    private static List<Entry> cache;

    private ItemCatalog() { }

    /**
     * One pickable item.
     *
     * @param id     registry id, e.g. {@code minecraft:iron_chestplate}
     * @param name   display name in the client's own language
     * @param source namespace, so {@code @minecraft} can filter by mod
     */
    record Entry(String id, String name, String source) {}

    /**
     * Every registered item, sorted by id.
     *
     * <p>Air is excluded because picking it yields a kit entry that silently resolves to nothing. Items
     * with no translation are kept: dropping them would make the "missing item" report wrong about an item
     * that does exist, and some mods register items without a lang entry.
     */
    static synchronized List<Entry> all() {
        if (cache != null) return cache;
        List<Entry> out = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
            var item = BuiltInRegistries.ITEM.get(key);
            if (item == null) continue;
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            if (key.getPath().equals("air")) continue;
            String name = stack.getHoverName().getString();
            if (name == null || name.isBlank()) name = key.toString();
            out.add(new Entry(key.toString(), name, key.getNamespace()));
        }
        out.sort(Comparator.comparing(Entry::id));
        cache = List.copyOf(out);
        return cache;
    }

    /** Drops the cached list, e.g. on logout so a resource-pack reload is picked up next session. */
    static synchronized void clear() {
        cache = null;
    }

    /**
     * Filters by the JEI-style query the picker's search box accepts.
     *
     * <p>Delegates to {@link LibrarySearch} so items, weapons and vehicles all answer to the same syntax —
     * and, crucially, passes the namespace through, without which {@code @modid} matched nothing at all.
     */
    static List<Entry> search(List<Entry> entries, String query) {
        String needle = query == null ? "" : query.trim();
        if (needle.isEmpty()) return entries;
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (LibrarySearch.matches(needle, entry.id(), entry.name(), entry.source())) out.add(entry);
        }
        return out;
    }

    /** Namespaces present in the catalogue, so the help line can name a few real ones. */
    static List<String> sources(List<Entry> entries) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (Entry entry : entries) out.add(entry.source());
        List<String> sorted = new ArrayList<>(out);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
