package cn.bfops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Everything a soldier carries besides the two guns: arbitrary items, the four armour pieces, and
 * accessories.
 *
 * <p><b>Why this is not just "more options".</b> A kit used to describe exactly two weapons and an
 * ammunition count, so a medic could not be issued medical supplies and an engineer could not be
 * issued blocks — the only way to give players anything else was to hand them out by command, which
 * breaks the server-authoritative promise. This makes the whole loadout a property of the kit, which
 * means it is authored in the editor, validated on the server, and restored with the kit.

 * <p><b>Why items are a list of ids rather than a category.</b> Categories would mean inventing a
 * taxonomy that then has to agree with every other mod's idea of "consumable". An explicit id list is
 * what the operator actually means ("six golden apples, torches"), needs no classification, and is
 * searchable through the same catalogue the weapons use.
 *
 * <p><b>Counts.</b> Each entry carries a count capped by the item's own maximum stack size, so a kit
 * cannot be used to smuggle a chest's worth of one item past the inventory limit.
 */
public final class KitGear {
    /** Total entries across items + armour + accessories. Keeps one kit from becoming a warehouse. */
    public static final int MAX_ENTRIES = 32;
    /** Upper bound per count, independent of the item's stack size. */
    public static final int MAX_COUNT = 64;
    /** Armour slots, in the order the game uses. */
    public static final List<String> ARMOUR_SLOTS = List.of("HEAD", "CHEST", "LEGS", "FEET");

    /** One configured item and how many to issue. */
    public static final class Entry {
        public String id = "";
        public int count = 1;

        public Entry() { }

        public Entry(String id, int count) {
            this.id = id;
            this.count = count;
        }

        public void validate() {
            Model.require(id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && id.length() < 160,
                    "物品 ID 无效：" + id);
            Model.require(count >= 1 && count <= MAX_COUNT,
                    "物品「" + id + "」的数量为 1～" + MAX_COUNT);
        }
    }

    /** Free-form items issued into the inventory. */
    public final List<Entry> items = new ArrayList<>();
    /** Armour slot ({@code HEAD}/{@code CHEST}/{@code LEGS}/{@code FEET}) to item id. */
    public final Map<String, String> armour = new LinkedHashMap<>();
    /** Accessory slot id (as defined by the accessory mod) to item id. */
    public final Map<String, String> accessories = new LinkedHashMap<>();

    public boolean configured() {
        return !items.isEmpty() || !armour.isEmpty() || !accessories.isEmpty();
    }

    public int entries() {
        return items.size() + armour.size() + accessories.size();
    }

    public void validate() {
        Model.require(entries() <= MAX_ENTRIES, "每个兵种的物品、盔甲与配饰合计最多 " + MAX_ENTRIES + " 项");
        Model.require(items != null, "物品列表无效");
        for (Entry entry : items) {
            Model.require(entry != null, "物品条目不能为空");
            entry.validate();
        }
        Model.require(armour != null, "盔甲配置无效");
        for (Map.Entry<String, String> entry : armour.entrySet()) {
            Model.require(ARMOUR_SLOTS.contains(entry.getKey()),
                    "盔甲槽位只能是 HEAD / CHEST / LEGS / FEET，收到：" + entry.getKey());
            Model.require(itemId(entry.getValue()), "盔甲 ID 无效：" + entry.getValue());
        }
        Model.require(accessories != null, "配饰配置无效");
        for (Map.Entry<String, String> entry : accessories.entrySet()) {
            Model.require(entry.getKey() != null && !entry.getKey().isBlank()
                    && entry.getKey().length() < 64, "配饰槽位名称无效");
            Model.require(itemId(entry.getValue()), "配饰 ID 无效：" + entry.getValue());
        }
    }

    private static boolean itemId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && id.length() < 160;
    }

    /** The armour slots this kit fills, in a stable order, for the server and the summary line. */
    public List<String> armourSlots() {
        List<String> out = new ArrayList<>();
        for (String slot : ARMOUR_SLOTS) {
            if (armour.containsKey(slot)) out.add(slot);
        }
        return out;
    }

    /** Duplicate entry ids are collapsed, keeping the first count, so a kit cannot double-issue. */
    public List<Entry> deduplicatedItems() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Entry> out = new ArrayList<>();
        for (Entry entry : items) {
            if (entry == null || entry.id == null || entry.id.isEmpty()) continue;
            if (seen.add(entry.id)) out.add(entry);
        }
        return out;
    }
}
