package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for the two new map features: per-team activity zones and the overhead deploy camera.
 *
 * <p>Both are geometry, which is exactly the kind of thing that looks right when read and is wrong when
 * run, so every assertion below computes a real result rather than inspecting a flag.
 */
public final class ZoneSim {
    private static int checks;

    public static void main(String[] args) {
        // ---------------------------------------------------------------- zone containment
        Model.Arena map = squareMap(1000);
        Model.Zone blue = zone(0, 0, 0, 500, 1000);
        map.zones.add(blue);
        check(map.inZone(0, 250, 250), "a point inside the blue zone is in the zone");
        check(!map.inZone(0, 750, 750), "a point outside the blue zone is not");
        check(map.inZone(1, 750, 750), "an unconstrained team is allowed everywhere");

        // A zone literally at the map edge must not lose its own boundary: the polygon test is inclusive.
        Model.Arena edge = squareMap(1000);
        edge.zones.add(zone(0, 0, 0, 1000, 1000));
        check(edge.inZone(0, 0, 0), "a vertex of the zone is inside it");
        check(edge.inZone(0, 1000, 1000), "the far vertex is inside it");
        check(edge.inZone(0, 500, 500), "the middle is inside it");

        // ---------------------------------------------------------------- degenerate zones
        Model.Zone empty = new Model.Zone();
        empty.team = 0;
        check(!empty.active(), "an empty zone is not active");
        Model.Arena degenerate = squareMap(1000);
        degenerate.zones.add(empty);
        check(degenerate.zone(0) == null, "an empty zone is treated as no restriction");
        check(degenerate.inZone(0, 999, 999), "and therefore allows everywhere");

        Model.Zone two = new Model.Zone();
        two.team = 1;
        two.boundary = new ArrayList<>(List.of(new Model.Vec(0, 0), new Model.Vec(10, 0)));
        check(!two.active(), "a two-vertex zone cannot contain anything and is not active");

        // ---------------------------------------------------------------- view framing
        Model.Arena zoned = squareMap(1000);
        zoned.zones.add(zone(0, 0, 0, 400, 400));
        zoned.zones.add(zone(1, 600, 600, 1000, 1000));
        double[] blueExtent = zoned.extent(zoned.viewArea(0));
        check(blueExtent[0] == 200 && blueExtent[1] == 200, "the view centre is the zone centre");
        check(blueExtent[2] == 400, "the view span is the zone span, not the map span");
        double[] redExtent = zoned.extent(zoned.viewArea(1));
        check(redExtent[0] == 800, "the red zone frames its own centre");

        Model.Arena unzoned = squareMap(1000);
        double[] whole = unzoned.extent(unzoned.viewArea(0));
        check(whole[0] == 500 && whole[2] == 1000, "without zones the whole map is framed");

        // ---------------------------------------------------------------- camera placement
        // The farthest corner of an area of radius r seen from height h is sqrt(r^2+h^2) away, so the
        // whole area fits only when that is within the view distance. A 400-wide area (radius 200)
        // cannot fit in a 100-block view at any height, which is what the warning exists for.
        DeployView.Placement tooWide = DeployView.place(0, 0, 400, 64, 100);
        check(!tooWide.fits(), "a 400-wide area does not fit in a 100-block view");
        check(tooWide.overflow() > 0, "and reports a positive overflow (got " + tooWide.overflow() + ")");
        check(tooWide.overflow() >= 100, "the overflow is the radius past the view (got " + tooWide.overflow() + ")");

        // A small area fits comfortably, and the height is chosen so the far corner lands exactly on the
        // view limit rather than below it.
        DeployView.Placement fits = DeployView.place(0, 0, 100, 64, 100);
        check(fits.fits(), "a 100-wide area fits in a 100-block view");
        check(fits.overflow() == 0, "with no overflow");
        check(Math.abs(Math.hypot(50, fits.headroom()) - 100) < 1e-9,
                "the far corner sits exactly at the view limit");

        // Bigger area, lower camera. This is the counter-intuitive part and the thing most likely to be
        // "fixed" wrongly later, so it is pinned: at a fixed view distance more span must mean less height.
        DeployView.Placement narrow = DeployView.place(0, 0, 40, 64, 100);
        DeployView.Placement wider = DeployView.place(0, 0, 120, 64, 100);
        check(wider.headroom() <= narrow.headroom(),
                "a wider area gets a lower camera, not a higher one (narrow "
                        + narrow.headroom() + " vs wide " + wider.headroom() + ")");

        DeployView.Placement small = DeployView.place(0, 0, 10, 64, 100);
        check(small.headroom() >= DeployView.MIN_Y, "a tiny area still gets a minimum height");
        check(small.headroom() <= DeployView.MAX_Y, "and never exceeds the maximum");

        DeployView.Placement huge = DeployView.place(0, 0, 100000, 64, 100);
        check(huge.headroom() == DeployView.MIN_Y,
                "an area far wider than the view gets the minimum height (got " + huge.headroom() + ")");

        // The camera must look straight down: any tilt foreshortens part of the area and breaks the
        // correspondence between the screen and the ground plan.
        check(small.pitch() == -90f, "the camera looks straight down (got " + small.pitch() + ")");
        check(small.x() == 0 && small.z() == 0, "and sits over the centre it was given");

        // The ground height must be respected as the base.
        DeployView.Placement high = DeployView.place(0, 0, 100, 200, 100);
        DeployView.Placement low = DeployView.place(0, 0, 100, 0, 100);
        check(high.y() - low.y() == 200, "the camera follows the ground height");

        // A view distance of zero or negative must fall back rather than produce a division or a NaN.
        DeployView.Placement guarded = DeployView.place(0, 0, 100, 64, 0);
        check(!Double.isNaN(guarded.headroom()) && guarded.headroom() > 0, "a zero view distance is guarded");
        check(guarded.fits(), "and falls back to the default view distance");

        // The fit test must account for the minimum height, so a span that only just fits horizontally
        // with a zero-height camera is still reported as too wide.
        check(DeployView.fitsHorizontally(100, 100), "span 100 fits in view 100");
        check(!DeployView.fitsHorizontally(100000, 100), "a huge span does not");

        String warning = DeployView.warning(400, 100);
        check(warning != null && warning.contains("400"), "the warning names the actual span");
        check(warning != null && warning.contains("100"), "and the actual view distance");
        check(DeployView.warning(100, 100) == null, "a fitting area produces no warning");

        double maxSpan = DeployView.maxSpan(100);
        check(maxSpan < 200, "the usable span is below twice the view distance (got " + maxSpan + ")");
        check(DeployView.fitsHorizontally(maxSpan - 1, 100), "just under the limit fits");
        check(!DeployView.fitsHorizontally(maxSpan + 1, 100), "just over it does not");

        // ---------------------------------------------------------------- deploy site kinds
        Model.Arena deployable = squareMap(1000);
        deployable.spawns.add(spawn("总部", 0, 100, 100, Model.Spawn.BASE, "G"));
        deployable.spawns.add(spawn("机场", 0, 200, 200, Model.Spawn.AIR, "A"));
        deployable.spawns.add(spawn("红基地", 1, 900, 900, Model.Spawn.BASE, "G"));

        List<Deploy.Site> sites = Deploy.sites(deployable, 0, 0, false, null);
        check(sites.size() == 2, "both blue sites are offered, red's is not");
        check(Model.Spawn.BASE.equals(sites.get(0).kind()), "the base is listed first");
        check(Model.Spawn.AIR.equals(sites.get(1).kind()), "the airfield follows");
        check(sites.get(1).air(), "the airfield reports as air");
        check(!sites.get(0).air(), "the base does not");
        check(sites.get(1).allows('A') && !sites.get(1).allows('G'),
                "an airfield launches aircraft only");

        // A spawn outside the team's zone must not be offered: picking it would drop the player outside
        // their area and immediately start the exit countdown.
        Model.Arena confined = squareMap(1000);
        confined.zones.add(zone(0, 0, 0, 300, 300));
        confined.spawns.add(spawn("在区内", 0, 100, 100, Model.Spawn.BASE, "G"));
        confined.spawns.add(spawn("在区外", 0, 800, 800, Model.Spawn.BASE, "G"));
        List<Deploy.Site> confinedSites = Deploy.sites(confined, 0, 0, false, null);
        check(confinedSites.size() == 1, "only the in-zone spawn is offered");
        check("在区内".equals(confinedSites.get(0).id()), "and it is the right one");

        // ---------------------------------------------------------------- validation
        expectInvalid("a zone must not leave the map", () -> {
            Model.Arena bad = squareMap(1000);
            bad.zones.add(zone(0, 0, 0, 1200, 1200));
            bad.spawns.add(spawn("s", 0, 100, 100, Model.Spawn.BASE, "G"));
            bad.spawns.add(spawn("t", 1, 900, 900, Model.Spawn.BASE, "G"));
            bad.kits.add(new Model.Kit());
            bad.validate();
        });

        expectInvalid("a spawn must be inside its own team's zone", () -> {
            Model.Arena bad = squareMap(1000);
            bad.zones.add(zone(0, 0, 0, 300, 300));
            bad.spawns.add(spawn("s", 0, 800, 800, Model.Spawn.BASE, "G"));
            bad.spawns.add(spawn("t", 1, 900, 900, Model.Spawn.BASE, "G"));
            bad.kits.add(new Model.Kit());
            bad.validate();
        });

        expectInvalid("zones are conquest-only", () -> {
            Model.Arena bad = squareMap(1000);
            bad.mode = "BREAKTHROUGH";
            bad.zones.add(zone(0, 0, 0, 300, 300));
            bad.spawns.add(spawn("s", 0, 100, 100, Model.Spawn.BASE, "G"));
            bad.spawns.add(spawn("t", 1, 900, 900, Model.Spawn.BASE, "G"));
            bad.kits.add(new Model.Kit());
            bad.validate();
        });

        // An unzoned map must still validate: this is the compatibility guarantee for every saved map.
        Model.Arena legacy = squareMap(1000);
        legacy.spawns.add(spawn("s", 0, 100, 100, Model.Spawn.BASE, "G"));
        legacy.spawns.add(spawn("t", 1, 900, 900, Model.Spawn.BASE, "G"));
        legacy.kits.add(new Model.Kit());
        legacy.validate();
        checks++;

        System.out.println("ZoneSim: " + checks + " checks passed");
    }

    private static Model.Arena squareMap(double size) {
        Model.Arena map = new Model.Arena();
        map.mode = "CONQUEST";
        map.boundary = new ArrayList<>(List.of(
                new Model.Vec(0, 0), new Model.Vec(size, 0),
                new Model.Vec(size, size), new Model.Vec(0, size)));
        Model.Point point = new Model.Point();
        point.id = "A";
        point.x = 200;
        point.y = 64;
        point.z = 200;
        point.radius = 10;
        point.height = 10;
        map.points.add(point);
        map.minY = -64;
        map.maxY = 320;
        return map;
    }

    private static Model.Zone zone(int team, double x1, double z1, double x2, double z2) {
        Model.Zone zone = new Model.Zone();
        zone.team = team;
        zone.boundary = new ArrayList<>(List.of(
                new Model.Vec(x1, z1), new Model.Vec(x2, z1),
                new Model.Vec(x2, z2), new Model.Vec(x1, z2)));
        return zone;
    }

    private static Model.Spawn spawn(String id, int team, double x, double z, String kind, String allows) {
        Model.Spawn spawn = new Model.Spawn();
        spawn.id = id;
        spawn.team = team;
        spawn.x = x;
        spawn.y = 64;
        spawn.z = z;
        spawn.sector = 0;
        spawn.kind = kind;
        spawn.allows = allows;
        return spawn;
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
