package cn.bfops;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The numbers the MODIFY page prints, read once per (weapon, attachment set) and cached.
 *
 * <p>Everything here comes from the gun pack's own data — rate of fire, fire modes, magazine size,
 * bullet speed/lifetime and the recoil and inaccuracy blocks — rather than from a formula invented in
 * this mod. Values are only normalised to 0..1 for the four segmented bars; the numbers printed beside
 * them are the raw ones.
 */
final class GunSpec {
    /** Four segmented bars, 0..1, higher is better. */
    record Bars(float damage, float accuracy, float hipFire, float control) {}

    record Spec(String name, String role, int rpm, String fireModes, int magazine,
                Bars bars, float effectiveRange, float damagePerShot, boolean fromPack) {}

    private static final Map<String, Spec> CACHE = new LinkedHashMap<>(64, .75f, true);
    private static final Spec UNKNOWN = new Spec("", "", 0, "", 0, new Bars(.25f, .25f, .25f, .25f), 0, 0, false);

    static void clear() {
        CACHE.clear();
    }

    static Spec of(String id, Map<String, String> selected) {
        if (id == null || id.isEmpty()) return UNKNOWN;
        String key = id + "|" + new TreeMap<>(selected == null ? Map.of() : selected);
        Spec cached = CACHE.get(key);
        if (cached != null) return cached;
        Spec spec;
        try {
            spec = read(id, selected == null ? Map.of() : selected);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.debug("Gun spec unavailable: {}", id, ex);
            spec = UNKNOWN;
        }
        CACHE.put(key, spec);
        while (CACHE.size() > 64) CACHE.remove(CACHE.keySet().iterator().next());
        return spec;
    }

    private static Spec read(String id, Map<String, String> selected) throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
        Object index = ((Optional<?>) api.getMethod("getCommonGunIndex", ResourceLocation.class)
                .invoke(null, ResourceLocation.parse(id))).orElse(null);
        if (index == null) return UNKNOWN;
        Object data = index.getClass().getMethod("getGunData").invoke(index);
        Class<?> dataType = data.getClass();

        int rpm = ((Number) dataType.getMethod("getRoundsPerMinute").invoke(data)).intValue();
        List<?> modes = (List<?>) dataType.getMethod("getFireModeSet").invoke(data);
        String fireModes = fireModes(modes);

        Object bullet = dataType.getMethod("getBulletData").invoke(data);
        float damage = ((Number) bullet.getClass().getMethod("getDamageAmount").invoke(bullet)).floatValue();
        float speed = ((Number) bullet.getClass().getMethod("getSpeed").invoke(bullet)).floatValue();
        float life = ((Number) bullet.getClass().getMethod("getLifeSecond").invoke(bullet)).floatValue();

        Class<?> inaccuracy = Class.forName("com.tacz.guns.resource.pojo.data.gun.InaccuracyType");
        float aim = inaccuracyOf(dataType, data, inaccuracy.getField("AIM").get(null));
        float stand = inaccuracyOf(dataType, data, inaccuracy.getField("STAND").get(null));
        float recoil = recoilPitch(dataType, data);

        int magazine = magazine(id, selected, data);

        // Normalisation scales are the practical ranges of the pack's own numbers: a 0.02 rad aim
        // inaccuracy is a laser, 0.12 is a shotgun; a first-shot pitch kick of 4 degrees is heavy.
        Bars bars = new Bars(
                clamp01(damage / 90f),
                clamp01(1f - aim / 0.14f),
                clamp01(1f - stand / 0.30f),
                clamp01(1f - recoil / 9f));
        // Blocks are metres; speed is blocks/second and life is seconds, so the product is the reach.
        float range = speed * life;
        return new Spec(GearNames.display("gun", id, id), "", rpm, fireModes, magazine, bars, range, damage, true);
    }

    private static String fireModes(List<?> modes) {
        if (modes == null || modes.isEmpty()) return "—";
        List<String> names = new ArrayList<>();
        for (Object mode : modes) {
            names.add(switch (mode.toString()) {
                case "AUTO" -> "全自动";
                case "SEMI" -> "半自动";
                case "BURST" -> "三连发";
                default -> mode.toString();
            });
        }
        return String.join(" / ", names);
    }

    private static float inaccuracyOf(Class<?> dataType, Object data, Object type) {
        try {
            Method method = dataType.getMethod("getInaccuracy", type.getClass());
            Object value = method.invoke(data, type);
            return value instanceof Number number ? number.floatValue() : 0f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0f;
        }
    }

    private static float recoilPitch(Class<?> dataType, Object data) {
        try {
            Object recoil = dataType.getMethod("getRecoil").invoke(data);
            Object[] keys = (Object[]) recoil.getClass().getMethod("getPitch").invoke(recoil);
            if (keys == null || keys.length == 0) return 0f;
            Object first = keys[0];
            Object value = first.getClass().getMethod("getValue").invoke(first);
            return value instanceof Number number ? Math.abs(number.floatValue()) : 0f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0f;
        }
    }

    private static int magazine(String id, Map<String, String> selected, Object data) {
        try {
            ItemStack bare = Fittings.gun(id);
            Class<?> igun = Class.forName("com.tacz.guns.api.item.IGun");
            Object item = igun.getMethod("getIGunOrNull", ItemStack.class).invoke(null, bare);
            ItemStack current = bare.copy();
            if (item != null) {
                for (String attachment : selected.values()) {
                    if (attachment == null || attachment.isEmpty()) continue;
                    ItemStack part = Fittings.attachment(attachment);
                    if ((boolean) igun.getMethod("allowAttachment", ItemStack.class, ItemStack.class)
                            .invoke(item, current, part)) {
                        igun.getMethod("installAttachment", net.minecraft.core.HolderLookup.Provider.class,
                                        ItemStack.class, ItemStack.class)
                                .invoke(item, net.minecraft.client.Minecraft.getInstance().player.registryAccess(), current, part);
                    }
                }
            }
            Class<?> utils = Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            Object value = utils.getMethod("getAmmoCountWithAttachment", ItemStack.class, data.getClass())
                    .invoke(null, current, data);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            try {
                Object ammo = data.getClass().getMethod("getAmmoAmount").invoke(data);
                return ammo instanceof Number number ? number.intValue() : 0;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return 0;
            }
        }
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
