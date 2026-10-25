package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for vehicle loadout and supply pads.
 *
 * <p>The arithmetic that decides how much ammo and energy a deployed vehicle gets, and what a pad
 * restores, is pure and lives in {@link VehicleSupply}. These assertions exercise it directly, so the
 * numbers cannot drift when someone tunes a constant.
 */
public final class SupplySim {
    private static int checks;

    public static void main(String[] args) {
        kitIsVehicleSpecific();
        kitScalesWithWeapons();
        energyFillsToCapacity();
        healthIsClamped();
        ammoIdsAreNormalised();
        padCoverage();
        padServesTeams();
        padSelection();
        validation();
        System.out.println("SupplySim: " + checks + " checks passed");
    }

    private static void kitIsVehicleSpecific() {
        // A tank must get more than a car: that is the entire reason the amount is per-vehicle.
        int tank = VehicleSupply.Kit.of("TANK", 1, false).magazines();
        int car = VehicleSupply.Kit.of("CAR", 1, false).magazines();
        check(tank > car, "a tank carries more rounds than a car (tank " + tank + ", car " + car + ")");

        // An unclassified vehicle must still get a usable amount rather than zero.
        int unknown = VehicleSupply.Kit.of(null, 1, false).magazines();
        check(unknown >= 1, "an unknown type still receives ammunition (got " + unknown + ")");
        check(VehicleSupply.Kit.of("", 1, false).magazines() == unknown,
                "blank and null kinds behave the same");
        checks++;
    }

    private static void kitScalesWithWeapons() {
        int one = VehicleSupply.Kit.of("TANK", 1, false).magazines();
        int three = VehicleSupply.Kit.of("TANK", 3, false).magazines();
        check(three > one, "more weapons means more magazines");
        // But not without bound: a hull is not a warehouse.
        int many = VehicleSupply.Kit.of("TANK", 40, false).magazines();
        check(many <= 8, "the magazine count is capped (got " + many + ")");
        checks++;
    }

    private static void energyFillsToCapacity() {
        VehicleSupply.Kit powered = VehicleSupply.Kit.of("TANK", 1, true);
        check(powered.energy() < 0, "a hull with a battery asks for a full charge");
        check(VehicleSupply.energyFor(powered, 5000) == 5000, "which resolves to its maximum");
        check(VehicleSupply.energyFor(powered, 0) == 0, "and is zero when there is no capacity");

        VehicleSupply.Kit unpowered = VehicleSupply.Kit.of("TANK", 1, false);
        check(VehicleSupply.energyFor(unpowered, 5000) == 0, "an unpowered hull gets no charge");
        checks++;
    }

    private static void healthIsClamped() {
        check(VehicleSupply.healthFor(VehicleSupply.Kit.of("TANK", 1, false), 100f) == 100f,
                "a deployment repairs to full");
        check(VehicleSupply.healthFor(null, 100f) == 0f, "a missing kit produces no health");
        check(VehicleSupply.healthFor(VehicleSupply.Kit.of("TANK", 1, false), 0f) == 0f,
                "a zero maximum produces no health, not a negative");
        // A hypothetical fraction above 1 must not over-heal.
        VehicleSupply.Kit over = new VehicleSupply.Kit(2, 0, 3f);
        check(VehicleSupply.healthFor(over, 100f) == 100f, "an over-1 fraction is clamped to the maximum");
        checks++;
    }

    private static void ammoIdsAreNormalised() {
        check(VehicleSupply.ammoTypes(List.of("rifle")).equals(List.of("superbwarfare:rifle")),
                "a bare id gains the addon namespace");
        check(VehicleSupply.ammoTypes(List.of("superbwarfare:rifle"))
                        .equals(List.of("superbwarfare:rifle")),
                "a namespaced id is left alone");
        check(VehicleSupply.ammoTypes(List.of("rifle", "rifle")).size() == 1, "duplicates collapse");
        check(VehicleSupply.ammoTypes(List.of("", "  ")).isEmpty(), "blank entries are dropped");
        check(VehicleSupply.ammoTypes(null).isEmpty(), "null is handled");
        // An unarmed vehicle legitimately needs nothing.
        check(VehicleSupply.ammoTypes(List.of()).isEmpty(), "no weapons means no ammo");
        checks++;
    }

    private static void padCoverage() {
        VehicleSupply.Supply pad = new VehicleSupply.Supply("p", -1, 100, 64, 100, 20, true, true, false);
        check(pad.contains(100, 64, 100), "the centre is covered");
        check(pad.contains(118, 64, 100), "a point inside the radius is covered");
        check(!pad.contains(130, 64, 100), "a point outside the radius is not");
        // Radius is horizontal: a vehicle hovering above the pad is still serviced.
        check(pad.contains(100, 90, 100), "a point above the pad is covered");
        check(!pad.contains(100, 200, 100), "but not arbitrarily far above");
        check(!pad.contains(100, 20, 100), "nor far below");
        checks++;
    }

    private static void padServesTeams() {
        VehicleSupply.Supply both = new VehicleSupply.Supply("p", -1, 0, 0, 0, 10, true, false, false);
        check(both.serves(0) && both.serves(1), "team -1 serves both sides");
        VehicleSupply.Supply blue = both.withTeam(0);
        check(blue.serves(0) && !blue.serves(1), "a team-0 pad serves only blue");
        VehicleSupply.Supply red = both.withTeam(1);
        check(red.serves(1) && !red.serves(0), "a team-1 pad serves only red");
        checks++;
    }

    private static void padSelection() {
        List<VehicleSupply.Supply> pads = new ArrayList<>();
        pads.add(new VehicleSupply.Supply("blue", 0, 0, 64, 0, 10, true, false, false));
        pads.add(new VehicleSupply.Supply("red", 1, 100, 64, 0, 10, true, false, false));

        check("blue".equals(VehicleSupply.supplyAt(pads, 0, 0, 64, 0).id()), "blue gets its own pad");
        check("red".equals(VehicleSupply.supplyAt(pads, 1, 100, 64, 0).id()), "red gets its own pad");
        check(VehicleSupply.supplyAt(pads, 0, 100, 64, 0) == null,
                "blue cannot use the red pad even though it is in range");

        // A pad that restores nothing must never be selected: it would be a silent no-op.
        List<VehicleSupply.Supply> dead = new ArrayList<>();
        dead.add(new VehicleSupply.Supply("dead", -1, 0, 64, 0, 10, false, false, false));
        check(VehicleSupply.supplyAt(dead, 0, 0, 64, 0) == null, "an inert pad is not selected");
        check(VehicleSupply.supplyAt(null, 0, 0, 64, 0) == null, "a null list is handled");
        checks++;
    }

    private static void validation() {
        // A pad that restores nothing is always a mistake and must be rejected on save.
        expectInvalid("an inert supply pad is rejected", () -> {
            Model.Arena map = map();
            map.supplies.add(new VehicleSupply.Supply("补给1", -1, 100, 64, 100, 16,
                    false, false, false));
            map.validate();
        });

        // A pad outside the map boundary is unreachable.
        expectInvalid("a supply pad must be inside the map", () -> {
            Model.Arena map = map();
            map.supplies.add(new VehicleSupply.Supply("补给1", -1, 5000, 64, 5000, 16,
                    true, true, false));
            map.validate();
        });

        expectInvalid("the radius is bounded", () -> {
            Model.Arena map = map();
            map.supplies.add(new VehicleSupply.Supply("补给1", -1, 100, 64, 100, 500,
                    true, false, false));
            map.validate();
        });

        expectInvalid("duplicate ids are rejected", () -> {
            Model.Arena map = map();
            map.supplies.add(new VehicleSupply.Supply("补给1", -1, 100, 64, 100, 16, true, false, false));
            map.supplies.add(new VehicleSupply.Supply("补给1", -1, 200, 64, 200, 16, true, false, false));
            map.validate();
        });

        // A valid pad must pass, and a map with no pads must still validate — the compatibility
        // guarantee for every map saved before supply points existed.
        Model.Arena clean = map();
        clean.validate();
        checks++;
        Model.Arena withPad = map();
        withPad.supplies.add(new VehicleSupply.Supply("补给1", -1, 100, 64, 100, 16, true, true, false));
        withPad.validate();
        checks++;
        check(withPad.supplyAt(0, 100, 64, 100) != null, "and the pad is reachable through the arena");
        check(withPad.supplyAt(0, 500, 64, 500) == null, "while a distant point is not");
    }

    private static Model.Arena map() {
        Model.Arena map = new Model.Arena();
        map.mode = "CONQUEST";
        map.boundary = new ArrayList<>(List.of(
                new Model.Vec(0, 0), new Model.Vec(1000, 0),
                new Model.Vec(1000, 1000), new Model.Vec(0, 1000)));
        Model.Point point = new Model.Point();
        point.id = "A";
        point.x = 300;
        point.y = 64;
        point.z = 300;
        map.points.add(point);
        Model.Spawn blue = new Model.Spawn();
        blue.id = "蓝基地";
        blue.team = 0;
        blue.x = 100;
        blue.y = 64;
        blue.z = 100;
        map.spawns.add(blue);
        Model.Spawn red = new Model.Spawn();
        red.id = "红基地";
        red.team = 1;
        red.x = 900;
        red.y = 64;
        red.z = 900;
        map.spawns.add(red);
        Model.Kit kit = new Model.Kit();
        kit.id = "步兵";
        kit.primary = "tacz:rifle";
        kit.secondary = "";
        map.kits.add(kit);
        return map;
    }

    private static void expectInvalid(String message, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("expected validation to reject: " + message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
