package cn.bfops;

import java.util.List;

/**
 * Checks the item picker's catalogue and search.
 *
 * <p>The regression this exists for: the first version sent the catalogue from the server and passed an
 * empty {@code source} to the search, so {@code @modid} — the syntax the search box advertises — matched
 * nothing at all, and names came back translated with the server's language.
 */
public final class ItemCatalogSim {
    private static int checks;

    public static void main(String[] args) {
        searchSyntax();
        realRegistry();
        System.out.println("ItemCatalogSim: " + checks + " checks passed");
    }

    /** The query syntax, against a fixed catalogue so the assertions do not depend on the pack. */
    private static void searchSyntax() {
        List<ItemCatalog.Entry> entries = List.of(
                new ItemCatalog.Entry("minecraft:iron_chestplate", "Iron Chestplate", "minecraft"),
                new ItemCatalog.Entry("minecraft:golden_apple", "Golden Apple", "minecraft"),
                new ItemCatalog.Entry("superbwarfare:large_shell_ap", "Large Shell AP", "superbwarfare"),
                new ItemCatalog.Entry("gtceu:lv_motor", "LV Electric Motor", "gtceu"));

        // An empty query is not a filter.
        check(ItemCatalog.search(entries, "").size() == 4, "an empty query returns everything");
        check(ItemCatalog.search(entries, "   ").size() == 4, "a blank query returns everything");
        check(ItemCatalog.search(entries, null).size() == 4, "a null query returns everything");

        // Name matching, case-insensitive.
        check(ItemCatalog.search(entries, "chestplate").size() == 1, "name search finds one item");
        check(ItemCatalog.search(entries, "CHESTPLATE").size() == 1, "name search ignores case");
        check(ItemCatalog.search(entries, "apple").size() == 1, "a substring of the name matches");

        // Id matching, including the namespace as plain text.
        check(ItemCatalog.search(entries, "golden_apple").size() == 1, "id search works");
        check(ItemCatalog.search(entries, "minecraft").size() == 2,
                "the namespace matches as plain text");

        // @modid — the syntax that used to match nothing because source was empty.
        check(ItemCatalog.search(entries, "@minecraft").size() == 2,
                "@minecraft matches only that mod's items");
        check(ItemCatalog.search(entries, "@gtceu").size() == 1, "@gtceu matches its single item");
        check(ItemCatalog.search(entries, "@superbwarfare").size() == 1, "@superbwarfare matches");
        check(ItemCatalog.search(entries, "@nosuchmod").isEmpty(), "an unknown mod matches nothing");

        // Terms combine with AND, and @terms combine with plain terms.
        check(ItemCatalog.search(entries, "large shell").size() == 1, "multiple terms are ANDed");
        check(ItemCatalog.search(entries, "large apple").isEmpty(),
                "two terms on different items match nothing");
        check(ItemCatalog.search(entries, "@minecraft chestplate").size() == 1,
                "a mod term and a name term combine");
        check(ItemCatalog.search(entries, "@minecraft shell").isEmpty(),
                "combining a mod with another mod's item yields nothing");

        // Quoted terms let a phrase contain spaces.
        check(ItemCatalog.search(entries, "\"golden apple\"").size() == 1, "quoted phrases work");

        // The namespace list is sorted and unique, so the help line is stable.
        List<String> sources = ItemCatalog.sources(entries);
        check(sources.size() == 3, "three distinct namespaces");
        check(sources.get(0).equals("gtceu") && sources.get(2).equals("superbwarfare"),
                "namespaces are sorted: " + sources);
    }

    /**
     * The real registry, once bootstrapped.
     *
     * <p>Proves the catalogue actually enumerates items and can find a known one — which is the operator's
     * complaint in its plainest form ("can't find the right item"). Bootstrap is heavy but this is the only
     * way to check against the genuine registry rather than a fixture.
     */
    private static void realRegistry() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ex) {
            System.out.println("  (skipped registry checks: bootstrap unavailable)");
            return;
        }
        List<ItemCatalog.Entry> all = ItemCatalog.all();
        check(all.size() > 100, "the registry lists a useful number of items (" + all.size() + ")");
        check(ItemCatalog.all() == all, "the catalogue is cached rather than rebuilt");

        // A vanilla item everyone expects to find.
        List<ItemCatalog.Entry> iron = ItemCatalog.search(all, "iron_chestplate");
        check(!iron.isEmpty(), "a known vanilla item is findable by id");
        check(iron.stream().anyMatch(e -> e.id().equals("minecraft:iron_chestplate")),
                "the chestplate is among the matches");

        // Its namespace is recorded, so @minecraft is a real filter rather than a no-op.
        ItemCatalog.Entry chest = iron.stream()
                .filter(e -> e.id().equals("minecraft:iron_chestplate")).findFirst().orElseThrow();
        check("minecraft".equals(chest.source()), "the item carries its namespace");
        check(!ItemCatalog.search(all, "@minecraft").isEmpty(), "@minecraft returns real results");

        // air is excluded: picking it would create a kit entry that resolves to nothing.
        check(all.stream().noneMatch(e -> e.id().equals("minecraft:air")), "air is excluded");

        // Every entry has a non-blank display name, so the list never renders as blank rows.
        check(all.stream().allMatch(e -> e.name() != null && !e.name().isBlank()),
                "every entry has a display name");
        check(all.stream().allMatch(e -> e.source() != null && !e.source().isBlank()),
                "every entry has a namespace");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
