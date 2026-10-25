package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional access to the Curios accessory slots.
 *
 * <p>Curios is not a dependency of this mod, so every call is reflective and every failure degrades to
 * "no accessories" rather than preventing the editor or a match from running. That is the same contract
 * this mod already applies to TaCZ and Superb Warfare, and it matters more here: a server that
 * cannot see Curios must still be able to load and play a map whose kits happen to configure it.
 *
 * <p>Slot ids come from Curios itself rather than being hard-coded, because the slot set is data: packs
 * add slots, servers rename them, and a hard-coded list would silently stop matching. What the editor
 * shows is therefore exactly what the installed pack defines.
 */
final class CuriosSupport {
    private static Class<?> api;
    private static boolean resolved;
    private static List<String> slotCache;
    private static final java.util.Map<String, String> nameCache = new java.util.LinkedHashMap<>();

    private CuriosSupport() { }

    /** Whether the accessory mod is present. */
    static boolean available() {
        return api() != null;
    }

    /**
     * Slot ids the installed accessory mod defines, in a stable sorted order.
     *
     * <p>Cached: this is called while building a screen, which happens on every keystroke in the editor,
     * and the slot set does not change at runtime.
     */
    static List<String> slotIds() {
        if (slotCache != null) return slotCache;
        Class<?> type = api();
        if (type == null) return slotCache = List.of();
        try {
            Object raw = type.getMethod("getSlots").invoke(null);
            List<String> out = new ArrayList<>();
            if (raw instanceof java.util.Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    if (key != null) out.add(key.toString());
                }
            }
            out.sort(String::compareTo);
            return slotCache = List.copyOf(out);
        } catch (ReflectiveOperationException | LinkageError ex) {
            BattlefieldOps.LOG.warn("Cannot read accessory slots", ex);
            return slotCache = List.of();
        }
    }

    /**
     * Human-readable slot name.
     *
     * <p>Falls back to the raw id: a slot added by a datapack may have no translation, and showing
     * {@code ring} is more useful to an operator than showing nothing.
     */
    static String slotName(String slot) {
        if (slot == null || slot.isEmpty()) return "";
        String cached = nameCache.get(slot);
        if (cached != null) return cached;
        Class<?> type = api();
        String out = slot;
        if (type != null) {
            try {
                Object optional = type.getMethod("getSlot", String.class, boolean.class).invoke(null, slot, false);
                if (optional instanceof java.util.Optional<?> present && present.isPresent()) {
                    Object slotType = present.get();
                    Object display = slotType.getClass().getMethod("getDisplayName").invoke(slotType);
                    if (display != null) {
                        String text = display.toString();
                        if (!text.isBlank()) out = text;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Keep the raw id.
            }
        }
        nameCache.put(slot, out);
        return out;
    }

    private static Class<?> api() {
        if (!resolved) {
            resolved = true;
            try {
                api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            } catch (ClassNotFoundException missing) {
                api = null;
            }
        }
        return api;
    }

    /**
     * The player's accessory slots as NBT, or {@code null} when Curios is absent.
     *
     * <p>Needed because accessories are <em>issued</em> by a kit at deploy time and live outside the
     * vanilla inventory. Backing up only {@code getInventory()} left them equipped across a match, so a
     * player who deployed and then left kept every accessory the kit had handed them — free items, and a
     * mismatch between what the backup said they owned and what they were wearing.
     */
    static net.minecraft.nbt.ListTag save(net.minecraft.world.entity.player.Player player) {
        if (player == null) return null;
        try {
            Object handler = handler(player);
            if (handler == null) return null;
            Object tag = handler.getClass().getMethod("saveInventory", boolean.class)
                    .invoke(handler, false);
            return tag instanceof net.minecraft.nbt.ListTag list ? list : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            BattlefieldOps.LOG.warn("Cannot save accessories", ex);
            return null;
        }
    }

    /** Replaces the player's accessories with a saved set. */
    static void load(net.minecraft.world.entity.player.Player player,
                     net.minecraft.nbt.ListTag tag) {
        if (player == null || tag == null) return;
        try {
            Object handler = handler(player);
            if (handler == null) return;
            handler.getClass().getMethod("loadInventory", net.minecraft.nbt.ListTag.class)
                    .invoke(handler, tag);
        } catch (ReflectiveOperationException | LinkageError ex) {
            BattlefieldOps.LOG.warn("Cannot restore accessories", ex);
        }
    }

    /**
     * Empties every accessory slot.
     *
     * <p>Called before a kit's accessories are fitted and before a backup is restored: without it, an
     * issued accessory survives into the next deployment, and a restored backup ends up wearing both its
     * own accessories and the ones the previous match handed out.
     */
    static void clear(net.minecraft.world.entity.player.Player player) {
        if (player == null) return;
        try {
            Object handler = handler(player);
            if (handler == null) return;
            Object equipped = handler.getClass().getMethod("getEquippedCurios").invoke(handler);
            if (equipped == null) return;
            java.lang.reflect.Method slots = equipped.getClass().getMethod("getSlots");
            java.lang.reflect.Method set = equipped.getClass().getMethod("setStackInSlot", int.class,
                    net.minecraft.world.item.ItemStack.class);
            int count = (int) slots.invoke(equipped);
            for (int i = 0; i < count; i++) set.invoke(equipped, i, net.minecraft.world.item.ItemStack.EMPTY);
        } catch (ReflectiveOperationException | LinkageError ex) {
            BattlefieldOps.LOG.warn("Cannot clear accessories", ex);
        }
    }

    /** The accessory handler, or {@code null} when Curios is absent or has nothing for this player. */
    private static Object handler(net.minecraft.world.entity.player.Player player)
            throws ReflectiveOperationException {
        Class<?> type = api();
        if (type == null) return null;
        Object optional = type.getMethod("getCuriosInventory",
                net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
        if (!(optional instanceof java.util.Optional<?> present) || present.isEmpty()) return null;
        return present.get();
    }
}
