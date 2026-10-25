package cn.bfops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the new armament and kit-gear rules.
 *
 * <p>Deliberately arithmetic only: nothing here needs Minecraft running. The point is to pin the
 * decisions that are invisible until a player notices something wrong — a restricted round still being
 * issued, a per-station magazine count silently overriding another station, an old map without the new
 * blocks failing to load, or a kit's gear lossy round-trip through JSON.
 */
public final class ArmamentSim {
    private static int checks;

    public static void main(String[] args) {
        defaults();
        magazineResolution();
        ammoRestriction();
        attachmentSlots();
        gearDefaults();
        armourAndAccessories();
        perStationPlan();
        roundsAndCapacity();
        System.out.println("ArmamentSim: " + checks + " checks passed");
    }

    /** An unconfigured vehicle must behave exactly as it did before this feature existed. */
    private static void defaults() {
        VehicleArmament none = new VehicleArmament();
        check(!none.configured(), "an empty configuration reports unconfigured");
        check(none.magazines(4) == 4, "an unconfigured hull keeps the class default");
        check(none.permitted(List.of("a:x", "a:y")).equals(List.of("a:x", "a:y")),
                "an unconfigured hull allows every ammunition type");
        check(none.forStation("0") == null, "no station entry exists by default");
        check(none.attachments().isEmpty(), "no attachments are configured by default");

        // A map saved before armaments existed has no key at all; reading it must not distinguish
        // "absent" from "empty".
        Model.Arena old = BattlefieldOps.GSON.fromJson("{\"vehicleLimit\":6}", Model.Arena.class);
        check(old.armaments != null && old.armaments.isEmpty(), "an old map loads with no armament entries");
        check(old.armament("superbwarfare:t_90a").magazines(4) == 4,
                "asking an old map for a vehicle yields defaults");
    }

    /**
     * Per-station magazine counts resolve to one shared rack, and the largest wins.
     *
     * <p>The rack is a single container, so there is exactly one number. Choosing the smallest (or the
     * first) would silently under-fill a station the operator explicitly asked to be stocked.
     */
    private static void magazineResolution() {
        VehicleArmament armament = new VehicleArmament();
        VehicleArmament.Station a = new VehicleArmament.Station("0");
        VehicleArmament.Station b = new VehicleArmament.Station("1");

        check(armament.magazines(4) == 4, "no configured station falls back to the class default");

        a.magazines = 2;
        armament.stations.add(a);
        check(armament.magazines(4) == 2, "a configured count overrides the default");

        b.magazines = -1;
        armament.stations.add(b);
        check(armament.magazines(4) == 2, "an unset station does not drag the total down");

        b.magazines = 5;
        check(armament.magazines(4) == 5, "the largest configured count wins");

        // Zero is a real setting, not "unset": a dry vehicle that must be resupplied is a legitimate
        // map design, and it must not be confused with the -1 sentinel.
        VehicleArmament dry = new VehicleArmament();
        dry.stations.add(new VehicleArmament.Station("0"));
        dry.stations.getFirst().magazines = 0;
        check(dry.magazines(4) == 0, "zero magazines is a real setting, not a missing one");
        check(dry.configured(), "a zero-magazine station counts as configured");

        // The sentinel resolution on the station itself.
        check(new VehicleArmament.Station("0").magazines(7) == 7, "the sentinel resolves to the fallback");
    }

    /**
     * The ammunition allow-list is a union across stations, because the rack is shared.
     *
     * <p>An intersection would let one unrestricted station empty the whole vehicle's options, and a
     * round any station accepts must be allowed to sit in the shared container.
     */
    private static void ammoRestriction() {
        List<String> offered = List.of("sw:small_shell_ap", "sw:small_shell_he", "sw:rifle");

        VehicleArmament restricted = new VehicleArmament();
        VehicleArmament.Station station = new VehicleArmament.Station("0");
        station.ammo.add("sw:small_shell_ap");
        restricted.stations.add(station);
        check(restricted.permitted(offered).equals(List.of("sw:small_shell_ap")),
                "only the allowed round survives");

        // A second station allowing another round widens the shared rack.
        VehicleArmament.Station second = new VehicleArmament.Station("1");
        second.ammo.add("sw:rifle");
        restricted.stations.add(second);
        check(restricted.permitted(offered).equals(List.of("sw:small_shell_ap", "sw:rifle")),
                "the union of station lists is what the rack may hold");

        // One unrestricted station means the vehicle is unrestricted.
        VehicleArmament.Station open = new VehicleArmament.Station("2");
        restricted.stations.add(open);
        check(restricted.permitted(offered).equals(offered),
                "an unrestricted station lifts the restriction for the whole hull");

        // A typo can only ever remove ammunition, never add anything the weapon cannot fire.
        VehicleArmament typo = new VehicleArmament();
        VehicleArmament.Station bad = new VehicleArmament.Station("0");
        bad.ammo.add("sw:does_not_exist");
        typo.stations.add(bad);
        check(typo.permitted(offered).isEmpty(),
                "a mistyped id restricts to nothing rather than inventing a round");

        check(restricted.permitted(List.of()).isEmpty(), "no offered ammunition stays empty");
    }

    /** Attachment slot names are the five the addon defines; anything else is a configuration error. */
    private static void attachmentSlots() {
        check(VehicleArmament.Station.isAttachmentSlot("SCOPE"), "SCOPE is a valid slot");
        check(VehicleArmament.Station.isAttachmentSlot("MAGAZINE"), "MAGAZINE is a valid slot");
        check(VehicleArmament.Station.isAttachmentSlot("BARREL"), "BARREL is a valid slot");
        check(VehicleArmament.Station.isAttachmentSlot("STOCK"), "STOCK is a valid slot");
        check(VehicleArmament.Station.isAttachmentSlot("GRIP"), "GRIP is a valid slot");
        check(!VehicleArmament.Station.isAttachmentSlot("TURRET"), "an invented slot is rejected");
        check(!VehicleArmament.Station.isAttachmentSlot(null), "a null slot is rejected");
        check(!VehicleArmament.Station.isAttachmentSlot("scope"), "slot names are case-sensitive");

        // Labels exist for every real slot, so the editor never shows a bare enum name.
        for (String slot : List.of("SCOPE", "MAGAZINE", "BARREL", "STOCK", "GRIP")) {
            check(!VehicleArmament.Station.slotLabel(slot).equals(slot),
                    "slot " + slot + " has a translated label");
        }

        VehicleArmament.Station station = new VehicleArmament.Station("0");
        station.attachments.put("SCOPE", 2);
        station.validate();
        check(station.configured(), "a station with only attachments counts as configured");
    }

    /** A kit without a gear block must behave exactly as before, and JSON round-trip must not lose it. */
    private static void gearDefaults() {
        Model.Kit kit = new Model.Kit();
        check(kit.gear == null, "a new kit starts with no gear block");
        check(kit.gear().items.isEmpty(), "gear() creates an empty block");
        check(!kit.gear().configured(), "an empty gear block is not configured");
        check(kit.gear() == kit.gear(), "gear() returns the same instance on repeat calls");

        // A map saved before gear existed.
        Model.Kit old = BattlefieldOps.GSON.fromJson("{\"id\":\"rifleman\"}", Model.Kit.class);
        check(old.gear == null, "an old kit loads with no gear");
        check(old.gear().items.isEmpty(), "and asking it for gear is safe");

        // Round-trip through JSON: a record-shaped structure that silently drops on serialize would
        // look fine in the editor and issue nothing in game.
        Model.Kit full = new Model.Kit();
        full.id = "medic";
        full.gear().items.add(new KitGear.Entry("minecraft:golden_apple", 6));
        full.gear().armour.put("CHEST", "minecraft:iron_chestplate");
        full.gear().accessories.put("ring", "minecraft:gold_nugget");
        String json = BattlefieldOps.GSON.toJson(full);
        Model.Kit back = BattlefieldOps.GSON.fromJson(json, Model.Kit.class);
        check(back.gear != null, "the gear block survives serialization");
        check(back.gear.items.size() == 1, "the item list survives");
        check(back.gear.items.getFirst().count == 6, "the item count survives");
        check("minecraft:iron_chestplate".equals(back.gear.armour.get("CHEST")), "the armour survives");
        check("minecraft:gold_nugget".equals(back.gear.accessories.get("ring")), "the accessory survives");
    }

    /** Armour slot names are a fixed set; anything else must be rejected, not silently ignored. */
    private static void armourAndAccessories() {
        check(KitGear.ARMOUR_SLOTS.equals(List.of("HEAD", "CHEST", "LEGS", "FEET")),
                "the four armour slots are the four the game uses");

        KitGear gear = new KitGear();
        gear.armour.put("HEAD", "minecraft:iron_helmet");
        gear.armour.put("FEET", "minecraft:iron_boots");
        check(gear.armourSlots().equals(List.of("HEAD", "FEET")),
                "armour slots are reported in the game's own order, not insertion order");

        gear.validate();

        KitGear bad = new KitGear();
        bad.armour.put("TORSO", "minecraft:iron_chestplate");
        check(throwsOn(bad::validate), "an invented armour slot is rejected");

        KitGear badCount = new KitGear();
        badCount.items.add(new KitGear.Entry("minecraft:stone", 0));
        check(throwsOn(badCount::validate), "a zero count is rejected");

        KitGear badCount2 = new KitGear();
        badCount2.items.add(new KitGear.Entry("minecraft:stone", KitGear.MAX_COUNT + 1));
        check(throwsOn(badCount2::validate), "an oversized count is rejected");

        KitGear badId = new KitGear();
        badId.items.add(new KitGear.Entry("not a valid id", 1));
        check(throwsOn(badId::validate), "a malformed item id is rejected");

        KitGear tooMany = new KitGear();
        for (int i = 0; i < KitGear.MAX_ENTRIES + 1; i++) {
            tooMany.items.add(new KitGear.Entry("minecraft:stone", 1));
        }
        check(throwsOn(tooMany::validate), "a kit cannot exceed the entry cap");

        // Duplicates collapse to the first count, so a kit cannot double-issue by listing an id twice.
        KitGear dupes = new KitGear();
        dupes.items.add(new KitGear.Entry("minecraft:torch", 8));
        dupes.items.add(new KitGear.Entry("minecraft:torch", 32));
        List<KitGear.Entry> collapsed = dupes.deduplicatedItems();
        check(collapsed.size() == 1, "duplicate ids collapse to one entry");
        check(collapsed.getFirst().count == 8, "the first count wins");

        // Map ordering must be stable so the deployment summary reads the same way twice.
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("HEAD", "minecraft:iron_helmet");
        ordered.put("CHEST", "minecraft:iron_chestplate");
        check(new ArrayList<>(ordered.keySet()).equals(List.of("HEAD", "CHEST")),
                "armour configuration keeps insertion order");
    }

    /**
     * Every weapon station gets its own count and its own ammunition types.
     *
     * <p>This is the regression the operator actually hit: the editor let each station be configured
     * separately, and the loader collapsed them into one hull-wide number, so a station's own count
     * appeared to do nothing. The plan is the single place that turns per-station configuration into
     * per-station rounds, so it is asserted station by station.
     */
    private static void perStationPlan() {
        java.util.LinkedHashMap<String, List<String>> offered = new java.util.LinkedHashMap<>();
        offered.put("Cannon", List.of("sw:large_shell_ap", "sw:large_shell_he"));
        offered.put("MachineGun", List.of("sw:small_shell_ap"));
        java.util.Map<String, Integer> stacks = new java.util.HashMap<>();
        stacks.put("sw:large_shell_ap", 16);
        stacks.put("sw:large_shell_he", 16);
        stacks.put("sw:small_shell_ap", 64);

        // Unconfigured: every station keeps every round it can fire, at the default implied by its stacks.
        VehicleArmament none = new VehicleArmament();
        List<VehicleArmament.Allocation> base = none.plan(offered, stacks, 40);
        check(base.size() == 2, "one allocation per station");
        check(base.get(0).station().equals("Cannon"), "station order follows the vehicle");
        check(base.get(0).types().size() == 2, "an unconfigured station keeps both of its rounds");
        check(base.get(0).rounds() == 2 * VehicleArmament.defaultRounds(16),
                "an unconfigured station gets the default for each of its types");
        check(base.get(1).rounds() == VehicleArmament.defaultRounds(64),
                "a single-type station gets that type's default");

        // Configured: the cannon gets its own count and only AP; the machine gun is untouched.
        VehicleArmament two = new VehicleArmament();
        VehicleArmament.Station cannon = new VehicleArmament.Station("Cannon");
        cannon.rounds = 90;
        cannon.ammo.add("sw:large_shell_ap");
        two.stations.add(cannon);
        List<VehicleArmament.Allocation> plan = two.plan(offered, stacks, 40);
        check(plan.get(0).rounds() == 90, "the configured count lands on its own station");
        check(plan.get(0).types().equals(List.of("sw:large_shell_ap")),
                "the restricted station keeps only the allowed round");
        check(plan.get(1).rounds() == VehicleArmament.defaultRounds(64),
                "an unconfigured station is not dragged to another station's count");

        // Two stations with different counts must not average or collapse.
        VehicleArmament both = new VehicleArmament();
        VehicleArmament.Station a = new VehicleArmament.Station("Cannon");
        a.rounds = 120;
        VehicleArmament.Station b = new VehicleArmament.Station("MachineGun");
        b.rounds = 800;
        both.stations.add(a);
        both.stations.add(b);
        List<VehicleArmament.Allocation> split = both.plan(offered, stacks, 40);
        check(split.get(0).rounds() == 120, "station one gets its own count");
        check(split.get(1).rounds() == 800, "station two gets its own, larger count");
        check(split.get(0).rounds() != split.get(1).rounds(),
                "different counts produce different allocations");

        // A station restricted to ammunition it cannot fire gets nothing, rather than a substitute.
        VehicleArmament wrong = new VehicleArmament();
        VehicleArmament.Station bad = new VehicleArmament.Station("Cannon");
        bad.ammo.add("sw:small_shell_ap");
        wrong.stations.add(bad);
        List<VehicleArmament.Allocation> wrongPlan = wrong.plan(offered, stacks, 40);
        check(wrongPlan.get(0).types().isEmpty(), "a station cannot be loaded with another station's round");
        check(wrongPlan.get(0).empty(), "and it is skipped rather than filled with something else");

        // Zero rounds is a real setting and must survive per station.
        VehicleArmament dry = new VehicleArmament();
        VehicleArmament.Station dryCannon = new VehicleArmament.Station("Cannon");
        dryCannon.rounds = 0;
        dry.stations.add(dryCannon);
        check(dry.plan(offered, stacks, 40).get(0).rounds() == 0,
                "zero rounds means that station is dry");
        check(dry.plan(offered, stacks, 40).get(1).rounds() == VehicleArmament.defaultRounds(64),
                "and only that station is dry");

        check(new VehicleArmament().plan(null, stacks, 40).isEmpty(), "no stations offered plans nothing");
        check(new VehicleArmament().plan(new java.util.LinkedHashMap<>(), stacks, 40).isEmpty(),
                "an empty station table plans nothing");

        // A station whose ammunition cannot be read is still listed, but allocates nothing.
        java.util.LinkedHashMap<String, List<String>> noRounds = new java.util.LinkedHashMap<>();
        noRounds.put("Cannon", List.of());
        List<VehicleArmament.Allocation> blank = new VehicleArmament().plan(noRounds, stacks, 40);
        check(blank.size() == 1, "a station with no ammunition is still listed");
        check(blank.getFirst().empty(), "but it allocates nothing");
        checks++;
    }

    /**
     * Round counts, defaults and rack capacity.
     *
     * <p>The rule the operator asked for: light ammunition in the hundreds, shells in the dozens, derived
     * from how large a stack of that ammunition is. And the check that matters — a configured round count
     * has to be answerable against the vehicle's fixed number of <em>slots</em>, because one slot holds a
     * different number of rounds per type.
     */
    private static void roundsAndCapacity() {
        // Defaults follow the stack size, so an addon's new shell gets a sane figure with no table edit.
        check(VehicleArmament.defaultRounds(64) >= 256,
                "a large stack defaults to hundreds of rounds");
        check(VehicleArmament.defaultRounds(64) <= 1024,
                "and stays within the stated range for light ammunition");
        check(VehicleArmament.defaultRounds(16) <= 128, "a shell stack defaults to dozens");
        check(VehicleArmament.defaultRounds(1) <= 32, "a single-round item defaults to a small count");
        // Monotonic: a bigger stack never yields fewer rounds.
        int previous = 0;
        for (int stack = 1; stack <= 64; stack++) {
            int rounds = VehicleArmament.defaultRounds(stack);
            check(rounds >= previous, "defaults never decrease as the stack size grows (" + stack + ")");
            previous = rounds;
        }

        // An empty type list has no default.
        check(VehicleArmament.defaultRounds(List.of()) == 0, "no types means no default rounds");
        check(VehicleArmament.defaultRounds(List.of(16, 16)) ==
                        2 * VehicleArmament.defaultRounds(16),
                "the station default sums over its types");

        // Even split, remainder to the earlier types — matching how the loader distributes rounds.
        check(java.util.Arrays.equals(VehicleArmament.splitRounds(10, 2), new int[]{5, 5}),
                "an even total splits evenly");
        check(java.util.Arrays.equals(VehicleArmament.splitRounds(10, 3), new int[]{4, 3, 3}),
                "a remainder goes to the earlier types");
        check(java.util.Arrays.equals(VehicleArmament.splitRounds(0, 3), new int[]{0, 0, 0}),
                "zero splits into zeros");
        check(VehicleArmament.splitRounds(5, 0).length == 0, "no types splits into nothing");

        // Capacity: slots, not rounds, decide what fits.
        check(VehicleArmament.slotsNeeded(64, List.of(64)) == 1, "64 of a 64-stack is one slot");
        check(VehicleArmament.slotsNeeded(65, List.of(64)) == 2, "65 needs a second slot");
        check(VehicleArmament.slotsNeeded(0, List.of(64)) == 0, "zero rounds need no slots");
        check(VehicleArmament.slotsNeeded(10, List.of(16)) == 1, "a few shells fit one slot");
        check(VehicleArmament.slotsNeeded(40, List.of(16, 16)) == 4,
                "two shell types each need their own slots");
        // A station with no ammunition cannot occupy anything.
        check(VehicleArmament.slotsNeeded(100, List.of()) == 0, "no types need no slots");

        // Maximum rounds that fit a rack, found by search over the non-invertible mapping.
        int cap64 = VehicleArmament.maxRounds(4, List.of(64));
        check(VehicleArmament.slotsNeeded(cap64, List.of(64)) <= 4, "the maximum fits the rack");
        check(VehicleArmament.slotsNeeded(cap64 + 1, List.of(64)) > 4,
                "one more round would not fit");
        int capShell = VehicleArmament.maxRounds(4, List.of(16));
        check(capShell == 64, "four slots of 16-round shells hold 64 (" + capShell + ")");
        check(VehicleArmament.maxRounds(0, List.of(16)) == 0, "a zero-slot rack holds nothing");
        check(VehicleArmament.maxRounds(4, List.of()) == 0, "no types means no capacity");

        // An explicit round count wins over the default, and the legacy magazine field still resolves.
        VehicleArmament.Station station = new VehicleArmament.Station("Cannon");
        check(station.resolvedRounds(List.of(16)) == VehicleArmament.defaultRounds(List.of(16)),
                "unset rounds falls back to the default");
        station.rounds = 42;
        check(station.resolvedRounds(List.of(16)) == 42, "an explicit count wins");
        station.rounds = -1;
        station.magazines = 2;
        check(station.resolvedRounds(List.of(16)) == 64,
                "a legacy magazine count still resolves, so old maps keep their intent");
        station.magazines = -1;
        station.rounds = 0;
        check(station.resolvedRounds(List.of(16)) == 0, "zero rounds is a real setting");

        // The plan clamps to the rack: a station cannot be given more than physically fits.
        java.util.LinkedHashMap<String, List<String>> offered = new java.util.LinkedHashMap<>();
        offered.put("Cannon", List.of("sw:large_shell"));
        java.util.Map<String, Integer> stacks = new java.util.HashMap<>();
        stacks.put("sw:large_shell", 16);
        VehicleArmament over = new VehicleArmament();
        VehicleArmament.Station greedy = new VehicleArmament.Station("Cannon");
        greedy.rounds = 9999;
        over.stations.add(greedy);
        List<VehicleArmament.Allocation> clamped = over.plan(offered, stacks, 2);
        check(clamped.getFirst().rounds() == 32,
                "an over-capacity count is clamped to what the rack holds (got "
                        + clamped.getFirst().rounds() + ")");
        check(clamped.getFirst().slots() <= 2, "and the clamped allocation fits");

        // With no rack size known, no cap is applied — the loader still places only what fits.
        List<VehicleArmament.Allocation> uncapped = over.plan(offered, stacks, 0);
        check(uncapped.getFirst().rounds() == 9999, "an unknown rack applies no artificial cap");
        checks++;
    }

    private static boolean throwsOn(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException expected) {
            return true;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
