package cn.bfops;

import java.util.List;
import java.util.Map;

/**
 * What a freshly deployed vehicle is equipped with, and what a supply point restores.
 *
 * <p>A vehicle created by {@code EntityType#create} is an empty shell: the hull exists, but the ammo
 * rack holds nothing and the battery reads zero. That is correct behaviour for a player-built vehicle
 * and wrong for a deployed one — a tank that cannot fire and an aircraft that cannot start are not a
 * deployment, they are a four-block sandbox with a seat.
 *
 * <p>So deployment fills the vehicle in, and the amount is per-vehicle rather than global. A tank and a
 * jeep do not carry the same ammunition; filling both from one number either starves the tank or
 * over-arms the jeep. The counts are derived from the vehicle's own weapon table where that is readable
 * and fall back to a class-based default where it is not, because a third-party addon's data format is
 * not something this mod should fail over.
 *
 * <p>The pure arithmetic lives here so it can be checked without a running server: everything taking a
 * {@code Vehicle} handle is a thin adapter, and the decisions are in {@link Kit#of} and
 * {@link Supply#restore}.
 */
final class VehicleSupply {
    /**
     * Ammo rounds and energy units handed to a deployed vehicle.
     *
     * @param magazines how many magazines' worth of each ammo type to place in the rack
     * @param energy    energy units to charge, or a negative value for "fill to capacity"
     * @param health    fraction of max health to set, 1.0 for undamaged
     */
    record Kit(int magazines, int energy, float health) {
        static Kit of(String kind, int weaponCount, boolean hasEnergy) {
            return of(kind, weaponCount, hasEnergy, -1);
        }

        /**
         * Same, with the operator's own magazine count taking precedence.
         *
         * <p>{@code override} of {@code -1} means "use the class table below", which is what every map
         * that has not configured this vehicle gets. The override is a single number for the whole hull
         * rather than per station because the ammunition rack is one shared container: a per-station
         * count would have to answer "what if the two stations disagree about the total", and there is no
         * good answer that is also simple. Per-station control lives where it belongs — in which
         * ammunition types each station may take, not in how many fit.
         */
        static Kit of(String kind, int weaponCount, boolean hasEnergy, int override) {
            // A hull with more weapons needs more resupply than a single-gun vehicle, but not
            // proportionally: the point is "enough to fight", not "enough to never return".
            int mags = switch (normalise(kind)) {
                case "TANK" -> 4;
                case "APC" -> 4;
                case "AA" -> 3;
                case "ARTILLERY" -> 3;
                case "AIRPLANE" -> 3;
                case "HELICOPTER" -> 3;
                case "DRONE" -> 2;
                case "AIRSHIP" -> 3;
                case "BOAT" -> 3;
                case "CAR" -> 2;
                case "DEFENSE" -> 3;
                default -> 2;
            };
            // Multi-weapon hulls get one extra magazine per extra weapon, capped so a battleship does
            // not receive a warehouse.
            int extra = Math.min(3, Math.max(0, weaponCount - 1));
            mags = Math.min(8, mags + extra);
            // A configured count replaces the whole derivation including the multi-weapon bonus: the
            // operator typed a number, so applying a hidden bonus on top would make the editor lie.
            if (override >= 0) mags = override;
            // Energy is filled to capacity: unlike ammo, a partial charge is not a meaningful choice,
            // because the player can simply wait. Sending -1 means "whatever this hull holds".
            return new Kit(mags, hasEnergy ? -1 : 0, 1f);
        }
    }

    /**
     * What a supply point restores, and to whom.
     *
     * <p>A record with explicit {@code with*} copies rather than setters: the editor mutates a draft
     * through a list index, and handing it a mutable object would let a half-finished edit leak into the
     * live match before the operator saved. Copy-on-write keeps every intermediate state valid.
     */
    record Supply(String id, int team, double x, double y, double z, double radius,
                  boolean ammo, boolean energy, boolean health) {
        boolean restoresAnything() {
            return ammo || energy || health;
        }

        Supply withId(String value) {
            return new Supply(value, team, x, y, z, radius, ammo, energy, health);
        }

        Supply withTeam(int value) {
            return new Supply(id, value, x, y, z, radius, ammo, energy, health);
        }

        Supply withX(double value) {
            return new Supply(id, team, value, y, z, radius, ammo, energy, health);
        }

        Supply withY(double value) {
            return new Supply(id, team, x, value, z, radius, ammo, energy, health);
        }

        Supply withZ(double value) {
            return new Supply(id, team, x, y, value, radius, ammo, energy, health);
        }

        Supply withRadius(double value) {
            return new Supply(id, team, x, y, z, value, ammo, energy, health);
        }

        Supply withAmmo(boolean value) {
            return new Supply(id, team, x, y, z, radius, value, energy, health);
        }

        Supply withEnergy(boolean value) {
            return new Supply(id, team, x, y, z, radius, ammo, value, health);
        }

        Supply withHealth(boolean value) {
            return new Supply(id, team, x, y, z, radius, ammo, energy, value);
        }

        /** Whether this point services a given team; {@code team} outside 0..1 means both sides. */
        boolean serves(int other) {
            return team < 0 || team > 1 || team == other;
        }

        boolean contains(double px, double py, double pz) {
            double dx = px - x, dz = pz - z;
            // Horizontal radius with a generous vertical allowance: a supply point is a place, and a
            // player or vehicle hovering just above the pad should still be serviced. A tight vertical
            // bound would make the pad unusable the moment terrain is uneven.
            if (dx * dx + dz * dz > radius * radius) return false;
            return py >= y - 8 && py <= y + 32;
        }
    }

    private VehicleSupply() { }

    /**
     * The first supply point that can service this position, or {@code null}.
     *
     * <p>Order is the list's own, so a map's first point wins a tie; supply points are deliberately not
     * ranked by distance because the only decision is "is anything restored", and a nearest-first order
     * would make the result depend on floating point when two pads overlap.
     */
    static Supply supplyAt(List<Supply> supplies, int team, double x, double y, double z) {
        if (supplies == null) return null;
        for (Supply supply : supplies) {
            if (supply == null || !supply.restoresAnything()) continue;
            if (!supply.serves(team)) continue;
            if (supply.contains(x, y, z)) return supply;
        }
        return null;
    }

    /**
     * Rounds to place in the rack for a given ammo item.
     *
     * <p>Ignores stack size: the caller splits the total across slots, because a magazine is a slot and
     * the rack layout is the vehicle addon's business, not this mod's.
     */
    static int rounds(Kit kit, int weaponCount) {
        if (kit == null) return 0;
        int bases = Math.max(1, weaponCount);
        return Math.max(0, kit.magazines()) * 32 * bases;
    }

    /** Energy to set, resolving the "fill to capacity" sentinel against the hull's own maximum. */
    static int energyFor(Kit kit, int maxEnergy) {
        if (kit == null || kit.energy() == 0 || maxEnergy <= 0) return 0;
        return kit.energy() < 0 ? maxEnergy : Math.min(maxEnergy, kit.energy());
    }

    /** Health to set, clamped so a kit can never produce an invalid or over-maximum value. */
    static float healthFor(Kit kit, float maxHealth) {
        if (kit == null || maxHealth <= 0) return 0;
        float fraction = kit.health() <= 0 ? 1f : Math.min(1f, kit.health());
        return maxHealth * fraction;
    }

    /**
     * Ammo ids a vehicle needs, from its own weapon table.
     *
     * <p>Read defensively: {@code AmmoConsumer#getAmmo()} is the addon's own field and may be blank or
     * absent for energy weapons, so blank entries are dropped and duplicates collapsed. An empty result
     * is a legitimate answer — an unarmed vehicle needs no ammunition.
     */
    static List<String> ammoTypes(Iterable<String> fromWeapons) {
        List<String> out = new java.util.ArrayList<>();
        if (fromWeapons == null) return out;
        for (String ammo : fromWeapons) {
            if (ammo == null || ammo.isBlank()) continue;
            String id = ammo.contains(":") ? ammo : "superbwarfare:" + ammo;
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    /** Normalises a vehicle type constant for switch purposes. */
    private static String normalise(String kind) {
        return kind == null ? "" : kind.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Serialisable description of what one vehicle receives, for the editor and for diagnostics. */
    static String describe(String kind, int weaponCount, boolean hasEnergy, Map<String, String> ammoNames) {
        Kit kit = Kit.of(kind, weaponCount, hasEnergy);
        StringBuilder text = new StringBuilder();
        text.append("弹药 ").append(kit.magazines()).append(" 个基数");
        if (hasEnergy) text.append(" · 电量满");
        if (ammoNames != null && !ammoNames.isEmpty()) {
            text.append(" · ").append(String.join("/", ammoNames.values()));
        }
        return text.toString();
    }
}
