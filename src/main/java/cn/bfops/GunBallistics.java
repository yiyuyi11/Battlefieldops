package cn.bfops;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * Real damage-over-distance curves, read from the gun pack instead of guessed.
 *
 * <p>TaCZ keeps the falloff in {@code BulletData.extraDamage.damageAdjust}: a list of
 * {@code (distance, multiplier)} pairs that the pack author wrote. This class turns those pairs into
 * sample points the bench can plot, and — because reading it costs a reflective walk through the gun
 * index — writes the whole catalogue to {@code config/battlefield_ops/ballistics.json} once after the
 * client starts, so later sessions are a file read.
 *
 * <p>Guns whose pack has no falloff data are marked as such; the bench then prints the effective range
 * (bullet speed × life) rather than inventing a curve, which is what the previous placeholder did.
 */
final class GunBallistics {
    /** One gun's curve: base damage, reach in metres, and the pack's own falloff points. */
    record Curve(float baseDamage, float range, List<float[]> points) {
        boolean falloff() {
            return points.size() >= 2;
        }
    }

    private static final Map<String, Curve> CACHE = new LinkedHashMap<>(64, .75f, true);
    private static boolean baked;

    private GunBallistics() {}


    private static java.lang.reflect.Method policyStampMethod, policyMultiplierMethod;
    private static boolean policyLooked;
    private static long seenStamp = Long.MIN_VALUE;

    /** Revision of Combat Balance's policy, or 0 when that mod is not installed. */
    private static long policyStamp() {
        try {
            if (!policyLooked) {
                policyLooked = true;
                Class<?> policy = Class.forName("cn.combatbalance.ClientPolicy");
                policyStampMethod = policy.getMethod("stamp");
                policyMultiplierMethod = policy.getMethod("damageMultiplier", String.class);
            }
            if (policyStampMethod == null) return 0L;
            return ((Number) policyStampMethod.invoke(null)).longValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            policyStampMethod = null;
            return 0L;
        }
    }

    /** Damage multiplier Combat Balance applies to this weapon, or 1 when it does not tune it. */
    private static float policyMultiplier(String gunId) {
        try {
            if (!policyLooked) policyStamp();
            if (policyMultiplierMethod == null) return 1f;
            Object value = policyMultiplierMethod.invoke(null, gunId);
            return value instanceof Number number ? number.floatValue() : 1f;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return 1f;
        }
    }

    static Curve of(String gunId) {
        if (gunId == null || gunId.isEmpty()) return new Curve(0, 0, List.of());
        long stamp = policyStamp();
        if (stamp != seenStamp) {
            seenStamp = stamp;
            CACHE.clear();
        }
        Curve cached = CACHE.get(gunId);
        if (cached != null) return cached;
        Curve curve;
        try {
            curve = read(gunId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.debug("Ballistics unavailable: {}", gunId, ex);
            curve = new Curve(0, 0, List.of());
        }
        CACHE.put(gunId, curve);
        while (CACHE.size() > 256) CACHE.remove(CACHE.keySet().iterator().next());
        return curve;
    }

    private static Curve read(String gunId) throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
        Object index = ((Optional<?>) api.getMethod("getCommonGunIndex", ResourceLocation.class)
                .invoke(null, new ResourceLocation(gunId))).orElse(null);
        if (index == null) return new Curve(0, 0, List.of());
        Object data = index.getClass().getMethod("getGunData").invoke(index);
        Object bullet = data.getClass().getMethod("getBulletData").invoke(data);

        float damage = ((Number) bullet.getClass().getMethod("getDamageAmount").invoke(bullet)).floatValue();
        float speed = ((Number) bullet.getClass().getMethod("getSpeed").invoke(bullet)).floatValue();
        float life = ((Number) bullet.getClass().getMethod("getLifeSecond").invoke(bullet)).floatValue();
        float range = speed * life;

        List<float[]> points = new ArrayList<>();
        Object extra = bullet.getClass().getMethod("getExtraDamage").invoke(bullet);
        if (extra != null) {
            Object adjust = extra.getClass().getMethod("getDamageAdjust").invoke(extra);
            if (adjust instanceof Iterable<?> pairs) {
                for (Object pair : pairs) {
                    if (pair == null) continue;
                    Method distance = pair.getClass().getMethod("getDistance");
                    Method multiplier = pair.getClass().getMethod("getDamage");
                    float at = ((Number) distance.invoke(pair)).floatValue();
                    float factor = ((Number) multiplier.invoke(pair)).floatValue();
                    points.add(new float[]{at, damage * factor});
                }
            }
        }
        // Packs write the last bracket as "infinite", which arrives as a huge float. Anchor it to the
        // bullet's own reach so the plot spans metres a player can read, and drop anything past 4 km.
        for (float[] point : points) {
            if (point[0] > 4096f) point[0] = range > 1f ? range : 200f;
        }
        points.removeIf(point -> point[0] <= 0f);
        float multiplier = policyMultiplier(gunId);
        if (multiplier != 1f) {
            damage *= multiplier;
            for (float[] point : points) point[1] *= multiplier;
        }
        points.sort(Comparator.comparingDouble(point -> point[0]));
        // Two brackets at the same distance would draw a vertical segment; keep the later one.
        List<float[]> clean = new ArrayList<>();
        for (float[] point : points) {
            if (!clean.isEmpty() && Math.abs(clean.get(clean.size() - 1)[0] - point[0]) < 0.5f) {
                clean.set(clean.size() - 1, point);
            } else {
                clean.add(point);
            }
        }
        return new Curve(damage, range, List.copyOf(clean));
    }

    /**
     * Rewrites the catalogue when the gun data changed.
     *
     * <p>The file is keyed by a signature of the catalogue (how many guns, and a hash of their ids), so
     * installing, removing or updating a gun pack makes the next launch regenerate it; an unchanged
     * install costs one small file read. A missing or unreadable file is treated as a change.
     */
    static void bakeIfChanged() {
        if (baked) return;
        baked = true;
        Path file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("battlefield_ops/ballistics.json");
        try {
            var catalog = Client.catalog;
            if (catalog == null || !catalog.has("entries")) {
                baked = false;
                return;   // catalogue not delivered yet; try again on a later tick
            }
            String signature = signature(catalog);
            if (signature == null) return;
            if (Files.isRegularFile(file)) {
                try (var reader = Files.newBufferedReader(file)) {
                    JsonObject stored = BattlefieldOps.GSON.fromJson(reader, JsonObject.class);
                    if (stored != null && stored.has("signature")
                            && signature.equals(stored.get("signature").getAsString())) {
                        return;
                    }
                } catch (Exception ignored) {
                    // unreadable cache: fall through and rewrite it
                }
            }
            JsonObject root = new JsonObject();
            root.addProperty("generatedBy", "battlefield_ops");
            root.addProperty("note", "read from each gun pack's BulletData.extraDamage.damageAdjust");
            root.addProperty("signature", signature);
            JsonObject guns = new JsonObject();
            for (var element : catalog.getAsJsonArray("entries")) {
                if (!element.isJsonObject()) continue;
                var entry = element.getAsJsonObject();
                if (!"gun".equals(Engine.str(entry, "kind", ""))) continue;
                String id = Engine.str(entry, "id", "");
                if (id.isEmpty()) continue;
                Curve curve = of(id);
                if (curve.range() <= 0 && !curve.falloff()) continue;
                JsonObject row = new JsonObject();
                row.addProperty("baseDamage", curve.baseDamage());
                row.addProperty("range", curve.range());
                JsonArray points = new JsonArray();
                for (float[] point : curve.points()) {
                    JsonArray pair = new JsonArray();
                    pair.add(point[0]);
                    pair.add(point[1]);
                    points.add(pair);
                }
                row.add("points", points);
                guns.add(id, row);
            }
            root.add("guns", guns);
            Files.createDirectories(file.getParent());
            Files.writeString(file, BattlefieldOps.GSON.toJson(root));
            BattlefieldOps.LOG.info("[bfops] ballistics rewritten: {} guns (signature {})", guns.size(), signature);
        } catch (Exception ex) {
            BattlefieldOps.LOG.warn("Cannot bake ballistics", ex);
        }
    }

    /** Catalogue fingerprint: gun count plus a stable hash of the sorted ids. */
    private static String signature(JsonObject catalog) {
        var ids = new java.util.ArrayList<String>();
        for (var element : catalog.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) continue;
            var entry = element.getAsJsonObject();
            if ("gun".equals(Engine.str(entry, "kind", ""))) ids.add(Engine.str(entry, "id", ""));
        }
        if (ids.isEmpty()) return null;
        ids.sort(String::compareTo);
        return ids.size() + ":" + Integer.toHexString(ids.hashCode()) + ":" + ids.get(0) + ":"
                + ids.get(ids.size() - 1) + ":policy" + policyStamp();
    }
}
