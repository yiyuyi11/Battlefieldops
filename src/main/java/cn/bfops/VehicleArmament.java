package cn.bfops;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Per-map, per-vehicle-weapon configuration: how much ammunition a station gets, which ammunition it
 * may be loaded with, and which attachments it may carry.
 *
 * <p><b>Why per map and per vehicle.</b> The magazine count is a balance figure: a tank that spawns with
 * four magazines on an open desert map is starved, and the same tank on a dense urban map is oppressive.
 * A single global number would force the operator to pick one and live with the other. The same argument
 * applies to ammunition types — some maps want AP-vs-HE as a real decision, others do not.
 *
 * <p><b>Defaults are not empty.</b> Every field falls back to a sensible value when the operator has not
 * configured anything: {@link #magazines} of {@code -1} means "use the class table in
 * {@link VehicleSupply.Kit}", an empty {@link #ammo} means "every type the weapon actually accepts", and
 * an empty {@link #attachments} means "no attachment may be changed". This matters because every map
 * saved before this existed has no configuration at all, and those maps must keep working untouched.
 *
 * <p>The whole point of the type is that it is <em>authorisation</em>: it states what the operator allows.
 * What the weapon can physically take is a separate question answered by the vehicle itself, and the
 * engine intersects the two. Configuring an ammunition type a weapon cannot fire is therefore harmless —
 * it simply never appears — rather than a crash or a silent substitution.
 */
public final class VehicleArmament {
    /**
     * Upper bound on magazines. The engine refuses more than this because a station holding more
     * ammunition than the chassis has slots would report "full" while the rack overflows nowhere to go.
     */
    public static final int MAX_MAGAZINES = 16;
    /** Upper bound on configured ammunition types per station. */
    public static final int MAX_AMMO = 16;
    /**
     * Hard upper bound on configured rounds, so a typo cannot ask for a million shells.
     *
     * <p>Deliberately far above any real rack: the authoritative limit is the vehicle's own container, and
     * this only stops absurd values from being stored at all.
     */
    public static final int MAX_ROUNDS = 20000;
    /** Upper bound on configured attachments per station. */
    public static final int MAX_ATTACHMENTS = 8;

    /**
     * One weapon station's configuration.
     *
     * <p>{@code station} is the weapon's slot key on the vehicle ({@code "0"}, {@code "1"}, …, or a named
     * station for chassis that use them). It is matched by name so that reordering weapons in a future
     * Superb Warfare version does not silently apply one station's limits to another.
     */
    public static final class Station {
        /** Weapon station key, e.g. {@code "0"}. */
        public String station = "0";
        /**
         * Exact rounds to place for this station, or {@code -1} for the default.
         *
         * <p>Rounds rather than magazines because a magazine is not a fixed quantity: different
         * ammunition stacks to different sizes, and a vehicle's rack holds a fixed number of <em>slots</em>.
         * Expressing the setting in rounds is the only way it can be checked against the rack and shown to
         * the operator as a real number of shells.
         *
         * <p>{@code 0} is a meaningful setting ("spawn this station dry and make them resupply") and is
         * therefore distinguished from "unset" by the {@code -1} sentinel.
         */
        public int rounds = -1;
        /**
         * Legacy magazine count, read only when {@link #rounds} is unset.
         *
         * <p>Kept so a map saved by the first build of this feature still loads with the intent it was
         * written with, instead of silently reverting to defaults.
         */
        public int magazines = -1;
        /**
         * Ammunition item ids this station may be loaded with, in preference order.
         *
         * <p>Empty means unrestricted: every type the weapon declares is allowed. Restricting it is how a
         * map says "no HE in the main gun" without needing a rule engine.
         */
        public List<String> ammo = new ArrayList<>();
        /**
         * Attachment ids this station may fit, keyed by slot.
         *
         * <p>Keys are the Superb Warfare attachment slots ({@code SCOPE}, {@code MAGAZINE},
         * {@code BARREL}, {@code STOCK}, {@code GRIP}) and values are the attachment index. An absent
         * slot means "leave it as the weapon ships", which is also what an unconfigured map gets.
         */
        public java.util.Map<String, Integer> attachments = new java.util.LinkedHashMap<>();

        public Station() { }

        public Station(String station) {
            this.station = station;
        }

        public boolean configured() {
            return rounds >= 0 || magazines >= 0 || !ammo.isEmpty() || !attachments.isEmpty();
        }

        public void validate() {
            Model.require(station != null && !station.isBlank() && station.length() < 64,
                    "载具武器站名称无效");
            Model.require(rounds >= -1 && rounds <= MAX_ROUNDS,
                    "载具武器「" + station + "」弹药数为 -1（默认）或 0～" + MAX_ROUNDS);
            Model.require(magazines >= -1 && magazines <= MAX_MAGAZINES,
                    "载具武器「" + station + "」弹药组数为 -1（默认）或 0～" + MAX_MAGAZINES);
            Model.require(ammo != null && ammo.size() <= MAX_AMMO,
                    "载具武器「" + station + "」最多 " + MAX_AMMO + " 种弹药");
            for (String id : ammo) {
                Model.require(id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && id.length() < 160,
                        "载具弹药 ID 无效：" + id);
            }
            Model.require(attachments != null && attachments.size() <= MAX_ATTACHMENTS,
                    "载具武器「" + station + "」最多 " + MAX_ATTACHMENTS + " 个配件槽");
            for (var entry : attachments.entrySet()) {
                String slot = entry.getKey();
                Model.require(isAttachmentSlot(slot), "载具配件槽位无效：" + slot);
                Integer value = entry.getValue();
                Model.require(value != null && value >= 0 && value <= 64,
                        "配件 " + slot + " 的选项编号为 0～64");
            }
        }

        /** The five attachment slots Superb Warfare defines on a weapon. */
        public static boolean isAttachmentSlot(String slot) {
            if (slot == null) return false;
            return switch (slot) {
                case "SCOPE", "MAGAZINE", "BARREL", "STOCK", "GRIP" -> true;
                default -> false;
            };
        }

        /** Chinese label for a slot, for the editor and for player-facing summaries. */
        public static String slotLabel(String slot) {
            return switch (slot == null ? "" : slot) {
                case "SCOPE" -> "瞄具";
                case "MAGAZINE" -> "弹匣";
                case "BARREL" -> "枪管";
                case "STOCK" -> "枪托";
                case "GRIP" -> "握把";
                default -> slot == null ? "" : slot;
            };
        }

        /**
         * Rounds to actually load, resolving both sentinels.
         *
         * <p>Priority: an explicit round count; then a legacy magazine count converted using the same
         * 32-round magazine this mod has always used; then the default implied by the ammunition's stack
         * sizes. Old maps therefore keep the intent they were saved with.
         */
        public int resolvedRounds(List<Integer> typeStacks) {
            if (rounds >= 0) return Math.min(rounds, MAX_ROUNDS);
            if (magazines >= 0) {
                return magazines * 32 * Math.max(1, typeStacks == null ? 0 : typeStacks.size());
            }
            return defaultRounds(typeStacks);
        }

        /** Magazines to actually load, for display only. */
        public int magazines(int fallback) {
            return magazines < 0 ? fallback : magazines;
        }

        /**
         * Whether a given ammunition id may be loaded into this station.
         *
         * <p>An empty list is unrestricted, which is the pre-existing behaviour and the behaviour every
         * old map keeps.
         */
        public boolean allowsAmmo(String id) {
            return ammo.isEmpty() || ammo.contains(id);
        }

        /**
         * Filters the weapon's real ammunition list down to what this station permits.
         *
         * <p>Intersection, not substitution: an operator who typed an id the weapon cannot fire gets that
         * entry ignored. Substituting a "close enough" type would put ammunition in the rack that the gun
         * then refuses to use, which looks like a broken vehicle rather than a misconfiguration.
         */
        public List<String> permitted(List<String> offered) {
            if (offered == null || offered.isEmpty()) return List.of();
            if (ammo.isEmpty()) return List.copyOf(offered);
            List<String> out = new ArrayList<>();
            for (String id : offered) if (ammo.contains(id)) out.add(id);
            return out;
        }
    }

    /** Station configurations for one vehicle, keyed by station name. */
    public final List<Station> stations = new ArrayList<>();

    public Station forStation(String station) {
        for (Station entry : stations) {
            if (entry != null && entry.station.equals(station)) return entry;
        }
        return null;
    }

    public boolean configured() {
        for (Station entry : stations) if (entry != null && entry.configured()) return true;
        return false;
    }

    /**
     * Magazines for the whole hull, resolving per-station values into the one shared rack.
     *
     * <p>Only a summary figure for display. Allocation does <em>not</em> use this — see
     * {@link #plan}. Using a single number for the whole hull was the bug behind "the per-station count
     * does not seem to do anything": the editor let each station be given its own count, and the loader
     * then collapsed them all to one value.
     *
     * @param fallback the per-class default from {@link VehicleSupply.Kit}
     */
    public int magazines(int fallback) {
        int best = -1;
        for (Station entry : stations) {
            if (entry == null || entry.magazines < 0) continue;
            best = Math.max(best, entry.magazines);
        }
        return best < 0 ? fallback : best;
    }

    /**
     * Default rounds for one ammunition type, from how large a single stack of it is.
     *
     * <p>The operator's own rule, made concrete: belted small-arms ammunition stacks high and is issued by
     * the several hundred, while shells stack low and are issued by the dozen. Deriving it from the stack
     * size rather than from a hard-coded list is what makes it right for ammunition this mod has never
     * seen — an addon's new shell gets a sensible default without anyone editing a table.
     */
    public static int defaultRounds(int maxStack) {
        if (maxStack >= 64) return 512;
        if (maxStack >= 32) return 384;
        if (maxStack >= 16) return 128;
        if (maxStack >= 8) return 64;
        if (maxStack >= 4) return 32;
        return 16;
    }

    /** Default total for a station, summed over the ammunition types it may actually load. */
    public static int defaultRounds(List<Integer> maxStacks) {
        if (maxStacks == null || maxStacks.isEmpty()) return 0;
        int total = 0;
        for (Integer stack : maxStacks) total += defaultRounds(stack == null ? 1 : stack);
        return total;
    }

    /**
     * Rounds for each ammunition type when {@code total} is split evenly.
     *
     * <p>Mirrors {@link AmmoAllocation#topUp}, which divides the total into equal quotas and gives any
     * remainder to the earlier types. Capacity has to be counted with the same split the loader will use,
     * or the preview and the real fill would disagree.
     */
    public static int[] splitRounds(int total, int types) {
        if (types <= 0) return new int[0];
        int clamped = Math.max(0, total);
        int[] out = new int[types];
        int quota = clamped / types;
        int remainder = clamped % types;
        for (int i = 0; i < types; i++) out[i] = quota + (i < remainder ? 1 : 0);
        return out;
    }

    /**
     * Slots this allocation would occupy in the rack.
     *
     * <p>Each type needs {@code ceil(rounds / itsStackSize)} slots. This is the number that decides whether
     * a configuration is physically possible, because the rack is a fixed number of slots rather than a
     * fixed number of rounds — which is exactly why a round count alone cannot be validated.
     */
    public static int slotsNeeded(int total, List<Integer> maxStacks) {
        if (maxStacks == null || maxStacks.isEmpty()) return 0;
        int[] perType = splitRounds(total, maxStacks.size());
        int slots = 0;
        for (int i = 0; i < perType.length; i++) {
            int stack = Math.max(1, maxStacks.get(i) == null ? 1 : maxStacks.get(i));
            slots += (perType[i] + stack - 1) / stack;
        }
        return slots;
    }

    /**
     * The largest round count that still fits the rack.
     *
     * <p>Found by binary search because {@link #slotsNeeded} is monotonic in the round count but not
     * invertible in one step once several types with different stack sizes share the total.
     */
    public static int maxRounds(int containerSlots, List<Integer> maxStacks) {
        if (containerSlots <= 0 || maxStacks == null || maxStacks.isEmpty()) return 0;
        int low = 0, high = MAX_ROUNDS;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (slotsNeeded(mid, maxStacks) <= containerSlots) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    /**
     * How much ammunition each station should receive, station by station.
     *
     * <p>This is the whole reason the configuration is per station: a tank's main gun and its coaxial
     * machine gun do not take the same rounds, so giving the hull one total makes both wrong. Each
     * station contributes its own round count, resolved from its own setting or from the default implied
     * by the stack size of the ammunition it accepts.
     *
     * @param offered    station name → ammunition ids that station can physically accept
     * @param stacks     ammunition id → its maximum stack size, used for defaults and capacity
     * @param containerSlots slots in the vehicle's rack, or 0 when unknown (no cap is applied then)
     * @return one allocation per station, in the order the vehicle lists them
     */
    public List<Allocation> plan(java.util.LinkedHashMap<String, List<String>> offered,
                                 java.util.Map<String, Integer> stacks,
                                 int containerSlots) {
        List<Allocation> out = new ArrayList<>();
        if (offered == null) return out;
        for (var entry : offered.entrySet()) {
            String station = entry.getKey();
            List<String> real = entry.getValue() == null ? List.of() : entry.getValue();
            Station config = forStation(station);
            // Intersection of what the weapon takes and what the operator allows. A station that permits
            // nothing it can actually fire gets an empty allocation rather than a substituted round.
            List<String> types = config == null ? List.copyOf(real) : config.permitted(real);
            List<Integer> typeStacks = new ArrayList<>();
            for (String id : types) {
                Integer stack = stacks == null ? null : stacks.get(id);
                typeStacks.add(stack == null || stack <= 0 ? 1 : stack);
            }
            int rounds = config == null ? defaultRounds(typeStacks)
                    : config.resolvedRounds(typeStacks);
            // A station can never be given more than the whole rack holds on its own; the operator is told
            // this in the editor, and the loader still places only what physically fits.
            int capacity = maxRounds(containerSlots, typeStacks);
            if (containerSlots > 0 && rounds > capacity) rounds = capacity;
            out.add(new Allocation(station, types, typeStacks, rounds));
        }
        return out;
    }

    /**
     * One station's share of the rack.
     *
     * @param station   weapon station key
     * @param types     ammunition ids this station may be loaded with, already intersected
     * @param magazines resolved magazine count (the sentinel already applied)
     * @param rounds    total rounds to place for this station
     */
    public record Allocation(String station, List<String> types, List<Integer> stacks, int rounds) {
        public boolean empty() {
            return rounds <= 0 || types.isEmpty();
        }

        /** Slots this allocation occupies, for reporting and for the capacity check. */
        public int slots() {
            return slotsNeeded(rounds, stacks);
        }
    }

    /**
     * Narrows the ammunition a hull may carry to what its stations allow.
     *
     * <p>Restriction is <em>union across stations</em>, not intersection: the rack is shared, so a round
     * that any station accepts must be allowed to sit in it. If every station is unrestricted (the
     * default, and what every map saved before this feature gets) the list passes through untouched.
     *
     * <p>An id the weapon does not actually offer can never appear here because the input is the
     * weapon's own list, so a typo degrades to "restricted to nothing" only for that typo, never to
     * ammunition the gun refuses to fire.
     */
    public List<String> permitted(List<String> offered) {
        if (offered == null || offered.isEmpty()) return List.of();
        boolean anyUnrestricted = false;
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        for (Station entry : stations) {
            if (entry == null) continue;
            if (entry.ammo.isEmpty()) anyUnrestricted = true;
            else allowed.addAll(entry.ammo);
        }
        if (anyUnrestricted || allowed.isEmpty()) return List.copyOf(offered);
        List<String> out = new ArrayList<>();
        for (String id : offered) if (allowed.contains(id)) out.add(id);
        return out;
    }

    /** Every attachment slot the operator configured anywhere in this hull, in slot order. */
    public java.util.Map<String, Integer> attachments() {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Station entry : stations) {
            if (entry == null || entry.attachments == null) continue;
            for (var attachment : entry.attachments.entrySet()) {
                out.putIfAbsent(attachment.getKey(), attachment.getValue());
            }
        }
        return out;
    }

    public void validate() {
        Model.require(stations != null && stations.size() <= 16, "每种载具最多 16 个武器站配置");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Station entry : stations) {
            Model.require(entry != null, "载具武器配置不能为空");
            entry.validate();
            Model.require(seen.add(entry.station), "载具武器站重复：" + entry.station);
        }
    }
}
