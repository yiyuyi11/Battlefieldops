package cn.bfops;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a kit's {@link KitGear} into real items, and wears what should be worn.
 *
 * <p><b>Resolution is by registry id, every time.</b> A map is a text file an operator can edit, and it
 * can name an item that is not installed — the pack is updated, a mod is removed, or the id was simply
 * mistyped. Every one of those cases has to degrade to "that entry is skipped and the operator is told",
 * never to an exception mid-deployment that leaves the player half-equipped. Looking the id up in the
 * registry on the server is also what makes the loadout authoritative: the client cannot invent an item
 * because it never sends one, it only ever sends a kit name.
 *
 * <p><b>Armour and accessories are worn, not handed over.</b> Putting a chestplate in the inventory has
 * the player arrive unarmoured, which defeats the purpose of configuring armour at all. Curios is reached
 * reflectively because it is an optional dependency: this mod must load and run with it absent, exactly
 * as it does for TaCZ and Superb Warfare.
 */
final class KitItems {
    /** Curios API entry points; resolved once, and remembered as absent for the rest of the session. */
    private static Class<?> curiosApi;
    private static boolean curiosResolved;

    private KitItems() { }

    /**
     * Builds the items a kit issues, without touching the player.
     *
     * <p>Separated from {@link #issue} because the deployment path has to count the total against the
     * inventory capacity <em>before</em> it clears the player's inventory. Delivering first and
     * discovering the overflow afterwards would leave the player with a silently truncated loadout.
     */
    static List<ItemStack> build(Model.Kit kit, Player player) {
        if (kit == null) return List.of();
        KitGear gear = kit.gear();
        List<ItemStack> out = new ArrayList<>();
        for (KitGear.Entry entry : gear.deduplicatedItems()) {
            ItemStack stack = resolve(entry.id, entry.count);
            if (!stack.isEmpty()) out.add(stack);
        }
        return out;
    }

    /**
     * Hands the kit's gear to a player: items into the inventory, armour onto the body, accessories into
     * their slots.
     *
     * <p>Called after the inventory has been cleared and after the capacity check, so nothing here can
     * overflow. It still tolerates a failed resolution per entry rather than aborting, so one bad id
     * cannot cost a player their whole deployment.
     */
    static void issue(Model.Kit kit, Player player) {
        if (kit == null || player == null) return;
        KitGear gear = kit.gear();
        for (KitGear.Entry entry : gear.deduplicatedItems()) {
            ItemStack stack = resolve(entry.id, entry.count);
            if (!stack.isEmpty()) player.getInventory().add(stack);
        }
        wear(kit, player);
    }

    /** Puts the configured armour on the body and the accessories into their slots. */
    static void wear(Model.Kit kit, Player player) {
        if (kit == null || player == null) return;
        KitGear gear = kit.gear();
        for (var entry : gear.armour.entrySet()) {
            EquipmentSlot slot = slotFor(entry.getKey());
            if (slot == null) continue;
            ItemStack stack = resolve(entry.getValue(), 1);
            if (!stack.isEmpty()) player.setItemSlot(slot, stack);
        }
        if (gear.accessories.isEmpty()) return;
        try {
            equipAccessories(gear, player);
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Curios absent, or its API moved. The kit is still deployed; the accessories are not worn.
            BattlefieldOps.LOG.warn("Cannot equip accessories (Curios unavailable?)", ex);
        }
    }

    /**
     * Fits accessories through Curios, one slot at a time.
     *
     * <p>A configured slot that does not exist on this player is skipped rather than treated as an
     * error: slot availability depends on the accessory mod's own config and on what other equipment
     * grants, neither of which this mod can know.
     */
    private static void equipAccessories(KitGear gear, Player player) throws ReflectiveOperationException {
        Class<?> api = curiosApi();
        if (api == null) return;
        Object optional = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, player);
        if (!(optional instanceof java.util.Optional<?> present) || present.isEmpty()) return;
        Object handler = present.get();
        java.lang.reflect.Method set = handler.getClass()
                .getMethod("setEquippedCurio", String.class, int.class, ItemStack.class);
        for (var entry : gear.accessories.entrySet()) {
            String slot = entry.getKey();
            ItemStack stack = resolve(entry.getValue(), 1);
            if (stack.isEmpty()) continue;
            try {
                set.invoke(handler, slot, 0, stack);
            } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                BattlefieldOps.LOG.warn("Cannot equip accessory into slot " + slot, ex);
            }
        }
    }

    private static Class<?> curiosApi() {
        if (!curiosResolved) {
            curiosResolved = true;
            try {
                curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            } catch (ClassNotFoundException missing) {
                curiosApi = null;
            }
        }
        return curiosApi;
    }

    private static EquipmentSlot slotFor(String name) {
        return switch (name == null ? "" : name) {
            case "HEAD" -> EquipmentSlot.HEAD;
            case "CHEST" -> EquipmentSlot.CHEST;
            case "LEGS" -> EquipmentSlot.LEGS;
            case "FEET" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    /**
     * Looks up an item id and builds a stack of at most one full stack.
     *
     * <p>The count is clamped to the item's own maximum stack size: a kit asking for 64 of something that
     * stacks to 16 gets 16, because {@code ItemStack} would otherwise silently disagree with what the
     * inventory can hold and the excess would vanish.
     */
    static ItemStack resolve(String id, int count) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation key;
        try {
            key = ResourceLocation.parse(id);
        } catch (RuntimeException malformed) {
            return ItemStack.EMPTY;
        }
        if (!BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(key);
        ItemStack stack = new ItemStack(item);
        int capped = Math.max(1, Math.min(count, stack.getMaxStackSize()));
        stack.setCount(capped);
        return stack;
    }

    /** Display name for an item id, falling back to the raw id when the item is not installed. */
    static String name(String id) {
        ItemStack stack = resolve(id, 1);
        return stack.isEmpty() ? id : stack.getHoverName().getString();
    }

    /**
     * Ids the kit names that this instance cannot actually resolve.
     *
     * <p>Surfaced to the operator rather than silently dropped: an item that vanished because a mod was
     * removed is a configuration error they need to see, and it is invisible in a client that never
     * renders the slot.
     */
    static List<String> missing(Model.Kit kit) {
        List<String> out = new ArrayList<>();
        if (kit == null) return out;
        KitGear gear = kit.gear();
        for (KitGear.Entry entry : gear.items) {
            if (entry != null && !entry.id.isEmpty() && resolve(entry.id, 1).isEmpty()) out.add(entry.id);
        }
        for (String id : gear.armour.values()) if (!id.isEmpty() && resolve(id, 1).isEmpty()) out.add(id);
        for (String id : gear.accessories.values()) if (!id.isEmpty() && resolve(id, 1).isEmpty()) out.add(id);
        return out;
    }
}
