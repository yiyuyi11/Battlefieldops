package cn.bfops;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Weapon property summary read from the installed TaCZ pack, for the loadout card's property line.
 *
 * <p>The reference pack has a dedicated weapon-property panel; this mod surfaces the same three headline
 * numbers on the card itself — damage, rate of fire, magazine size — read reflectively from TaCZ's own
 * gun index so the numbers are the pack's, not a hard-coded table. Damage is multiplied by pellet count so
 * a shotgun reads as its full per-trigger damage rather than one pellet.
 *
 * <p>Everything is best-effort and cached: a missing gun, a future TaCZ that renames a getter, or a gun
 * with no bullet data must not break the card — it degrades to an empty summary, and the caller falls back
 * to the attachment count.
 */
final class GunStats {
    private static final Map<String, String> cache = new LinkedHashMap<>();

    private GunStats() { }

    /** "伤害 35 · 射速 600 · 弹容 30", or {@code ""} when the gun or its data cannot be read. */
    static String summary(String id) {
        if (id == null || id.isEmpty()) return "";
        // Prefer the server's startup-scanned value; fall back to client reflection only for an older
        // server that does not send the field.
        JsonObject stats = Client.state != null && Client.state.has("gunStats")
                ? Client.state.getAsJsonObject("gunStats") : null;
        if (stats != null && stats.has(id) && stats.get(id).isJsonPrimitive()) {
            return BalanceDisplay.summary(id,stats.get(id).getAsString());
        }
        if (cache.size() > 256) cache.clear();
        return BalanceDisplay.summary(id,cache.computeIfAbsent(id, GunStats::resolve));
    }

    private static String resolve(String id) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object index = ((Optional<?>) api.getMethod("getClientGunIndex", net.minecraft.resources.ResourceLocation.class)
                    .invoke(null, new net.minecraft.resources.ResourceLocation(id))).orElse(null);
            if (index == null) return "";
            Object gun = index.getClass().getMethod("getGunData").invoke(index);
            if (gun == null) return "";
            int rpm = (int) gun.getClass().getMethod("getRoundsPerMinute").invoke(gun);
            int mag = (int) gun.getClass().getMethod("getAmmoAmount").invoke(gun);
            float weight = (float) gun.getClass().getMethod("getWeight").invoke(gun);
            float damage = 0;
            Object bullet = gun.getClass().getMethod("getBulletData").invoke(gun);
            if (bullet != null) {
                float perPellet = (float) bullet.getClass().getMethod("getDamageAmount").invoke(bullet);
                int pellets = Math.max(1, (int) bullet.getClass().getMethod("getBulletAmount").invoke(bullet));
                damage = perPellet * pellets;
            }
            Object ammo = gun.getClass().getMethod("getAmmoId").invoke(gun);
            StringBuilder text = new StringBuilder();
            if (damage > 0) text.append("伤害 ").append(Math.round(damage)).append(" · ");
            if (rpm > 0) text.append("射速 ").append(rpm).append(" · ");
            if (mag > 0) text.append("弹容 ").append(mag).append(" · ");
            if (weight > 0) text.append("重量 ").append(Math.round(weight)).append(" · ");
            if (ammo != null) text.append("弹药 ").append(Catalog.prettify(ammo.toString()));
            return text.toString().trim();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            BattlefieldOps.LOG.debug("No gun stats for {}", id, ex);
            return "";
        }
    }
}
