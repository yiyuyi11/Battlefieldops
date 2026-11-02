package cn.bfops;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Ammunition types for Superb Warfare weapons.
 *
 * <p>Those guns keep ammunition inside the stack ({@code GunData.ammo} / {@code ammoSlot}) keyed by an
 * "ammo slot" name, and one gun can accept several kinds. TaCZ has no equivalent, so this stays a separate
 * reflection bridge rather than being folded into {@link Catalog}.
 */
final class SuperbAmmo {
    private static final String GUN_DATA = "com.atsuishio.superbwarfare.data.gun.GunData";
    private static final String GUN_PROP = "com.atsuishio.superbwarfare.data.gun.GunProp";

    private static Object data(ItemStack stack) {
        try {
            Class<?> type = Class.forName(GUN_DATA);
            return type.getMethod("from", ItemStack.class).invoke(null, stack);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    /** Ammo slot names this gun accepts, in the order the gun data declares them. */
    static List<String> slots(ItemStack stack) {
        List<String> out = new ArrayList<>();
        Object gunData = data(stack);
        if (gunData == null) return out;
        try {
            Class<?> propType = Class.forName(GUN_PROP);
            Object prop = propType.getField("AMMO_CONSUMER").get(null);
            Object consumers = gunData.getClass().getMethod("get", propType).invoke(gunData, prop);
            if (!(consumers instanceof List<?> list)) return out;
            for (Object consumer : list) {
                if (consumer == null) continue;
                Object slot = consumer.getClass().getMethod("getAmmoSlot").invoke(consumer);
                if (slot instanceof String name && !name.isEmpty() && !out.contains(name)) out.add(name);
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // Gun without the data, or a version that renamed it: an empty list means "no choice offered".
        }
        return out;
    }

    /**
     * Selects {@code slot} and stocks {@code count} rounds of it.
     *
     * @return true when the gun accepted it
     */
    static boolean apply(ItemStack stack, String slot, int count) {
        if (slot == null || slot.isEmpty()) return false;
        Object gunData = data(stack);
        if (gunData == null) return false;
        try {
            List<String> slots = slots(stack);
            int index = slots.indexOf(slot);
            if (index < 0) return false;
            Object selector = gunData.getClass().getField("selectedAmmoType").get(gunData);
            selector.getClass().getMethod("set", int.class).invoke(selector, index);
            Object ammoSlot = gunData.getClass().getField("ammoSlot").get(gunData);
            ammoSlot.getClass().getMethod("set", String.class, int.class, int.class)
                    .invoke(ammoSlot, slot, Math.max(0, count), 0);
            return true;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return false;
        }
    }


    /**
     * Stocks a gun's own ammunition counters.
     *
     * <p>These guns hold rounds inside the stack, so a "x3" loadout entry has to become rounds here; without
     * this the gun is issued empty and no resupply crate can top it up.
     */
    static boolean stock(ItemStack stack, int rounds, int backup) {
        Object gunData = data(stack);
        if (gunData == null) return false;
        try {
            Object ammo = gunData.getClass().getField("ammo").get(gunData);
            ammo.getClass().getMethod("set", int.class).invoke(ammo, Math.max(0, rounds));
            Object reserve = gunData.getClass().getField("backupAmmoCount").get(gunData);
            reserve.getClass().getMethod("set", int.class).invoke(reserve, Math.max(0, backup));
            return true;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return false;
        }
    }

    /** True when the stack is one of these guns, checked by class name so no hard dependency is needed. */
    static boolean isGun(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem().getClass().getName().startsWith("com.atsuishio.superbwarfare.item.gun");
    }


    /**
     * The ammo identifiers this gun consumes, as its own data declares them.
     *
     * <p>These guns use ammo <em>items</em> (Superb Warfare ships an ammo item type), so the string here is
     * either an item id or a tag; the caller resolves it against the item registry.
     */
    static List<String> ammoIds(ItemStack stack) {
        List<String> out = new ArrayList<>();
        Object gunData = data(stack);
        if (gunData == null) return out;
        try {
            Class<?> propType = Class.forName(GUN_PROP);
            Object prop = propType.getField("AMMO_CONSUMER").get(null);
            Object consumers = gunData.getClass().getMethod("get", propType).invoke(gunData, prop);
            if (!(consumers instanceof List<?> list)) return out;
            for (Object consumer : list) {
                if (consumer == null) continue;
                Object ammo = consumer.getClass().getMethod("getAmmo").invoke(consumer);
                if (ammo instanceof String id && !id.isEmpty() && !out.contains(id)) out.add(id);
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // No declared ammo: nothing to issue.
        }
        return out;
    }


    /**
     * The ammo item this gun should be fed: the configured one when it is on the gun's list, else the first.
     *
     * <p>The gun data lists them as item ids (for example {@code superbwarfare:rpg_rocket_standard}), which is
     * also what the ammunition item stack is made of.
     */
    static String chosen(ItemStack gun, String preferred) {
        List<String> ids = ammoIds(gun);
        if (ids.isEmpty()) return "";
        if (preferred != null && ids.contains(preferred)) return preferred;
        return ids.getFirst();
    }

    /** Slot name for an ammo id, used when the gun reports per-slot ammunition counts. */
    static String slotFor(ItemStack gun, String ammoId) {
        Object gunData = data(gun);
        if (gunData == null) return "";
        try {
            Class<?> propType = Class.forName(GUN_PROP);
            Object prop = propType.getField("AMMO_CONSUMER").get(null);
            Object consumers = gunData.getClass().getMethod("get", propType).invoke(gunData, prop);
            if (!(consumers instanceof List<?> list)) return "";
            for (Object consumer : list) {
                if (consumer == null) continue;
                Object ammo = consumer.getClass().getMethod("getAmmo").invoke(consumer);
                if (ammoId.equals(ammo)) {
                    Object slot = consumer.getClass().getMethod("getAmmoSlot").invoke(consumer);
                    return slot instanceof String name ? name : "";
                }
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return "";
        }
        return "";
    }

    /** Logs what a gun declares once per id, so the mapping can be verified from a single game session. */
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    static void logOnce(ItemStack gun, String gunId) {
        if (!LOGGED.add(gunId)) return;
        BattlefieldOps.LOG.info("[bfops] superb gun {} declares ammo {}", gunId, ammoIds(gun));
    }


    /** True when a registry id names one of these guns; works on both sides, no dependency needed. */
    static boolean isGunId(String id) {
        if (id == null || id.isEmpty()) return false;
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) return false;
        return isGun(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key)));
    }

    /**
     * The gun plus its ammunition items, which is what every gun slot needs.
     *
     * <p>Every Superb Warfare weapon works this way, launchers and rifles alike: the rounds are items, and the
     * gun only reports what is loaded. {@code rounds} is how many ammunition items to hand out.
     */
    static List<ItemStack> gunBundle(String gunId, int rounds) {
        return gunBundle(gunId, rounds, null);
    }

    /** Same, but only the requested ammunition type, so a launcher never arrives with two kinds of rocket. */
    static List<ItemStack> gunBundle(String gunId, int rounds, String preferredAmmo) {
        List<ItemStack> out = new ArrayList<>();
        var key = net.minecraft.resources.ResourceLocation.tryParse(gunId);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) return out;
        net.minecraft.world.item.Item gunItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
        ItemStack gun = new ItemStack(gunItem);
        out.add(gun);
        String ammoId = chosen(gun, preferredAmmo);
        if (ammoId.isEmpty()) return out;
        var ammoKey = net.minecraft.resources.ResourceLocation.tryParse(ammoId);
        if (ammoKey == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ammoKey)) return out;
        net.minecraft.world.item.Item ammoItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ammoKey);
        int perStack = Math.max(1, ammoItem.getDefaultMaxStackSize());
        int remaining = Math.max(0, rounds);
        while (remaining > 0) {
            int amount = Math.min(remaining, perStack);
            out.add(new ItemStack(ammoItem, amount));
            remaining -= amount;
        }
        return out;
    }


    /** Chosen ammunition item id for a gun given by id, or empty when the gun declares none. */
    static String chosenFor(String gunId, String preferred) {
        var bundle = gunBundle(gunId, 0, preferred);
        if (bundle.isEmpty()) return "";
        List<String> ids = ammoIds(bundle.getFirst());
        if (ids.isEmpty()) return "";
        if (preferred != null && ids.contains(preferred)) return preferred;
        return ids.getFirst();
    }

    private SuperbAmmo() {
    }
}
