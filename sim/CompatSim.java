package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline tests for the coexistence policy.
 *
 * <p>These rules protect against a failure mode that is invisible in a crash log: TAB on the proxy
 * and this mod both writing the sidebar or team membership produces flicker, not an exception. That
 * makes them exactly the kind of thing that must be tested rather than eyeballed.
 */
public final class CompatSim {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        sidebarTests();
        teamTests();
        minimapTests();
        anchorTests();
        stickinessTests();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("ALL COMPAT TESTS PASSED");
            System.out.println("passed: " + passed);
        } else {
            System.out.println("FAILURES: " + failures.size());
            for (String failure : failures) System.out.println("  FAIL " + failure);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ sidebar

    private static void sidebarTests() {
        // A free slot is ours even in AUTO: the common case must not be pessimised.
        CompatPolicy free = new CompatPolicy();
        check("AUTO takes a free sidebar", free.mayUseSidebar(CompatPolicy.Mode.AUTO, null, "bfops"));
        check("free sidebar is not recorded as foreign", !free.foreignSidebar());

        // Our own objective on the slot is still ours — re-writing our sidebar every second must
        // not be mistaken for a conflict, or the sidebar would disable itself one tick after start.
        CompatPolicy own = new CompatPolicy();
        check("AUTO keeps its own sidebar", own.mayUseSidebar(CompatPolicy.Mode.AUTO, "bfops", "bfops"));
        check("own objective is not foreign", !own.foreignSidebar());

        // TAB's objective: we yield.
        CompatPolicy tab = new CompatPolicy();
        check("AUTO yields to a foreign sidebar", !tab.mayUseSidebar(CompatPolicy.Mode.AUTO, "tab", "bfops"));
        check("foreign sidebar recorded", tab.foreignSidebar());

        // A different objective name from any other plugin must also count as foreign.
        CompatPolicy other = new CompatPolicy();
        check("yields to any foreign name", !other.mayUseSidebar(CompatPolicy.Mode.AUTO, "someplugin", "bfops"));

        // Overrides must beat the observation, in both directions.
        CompatPolicy forced = new CompatPolicy();
        check("ALWAYS overrides a foreign sidebar",
                forced.mayUseSidebar(CompatPolicy.Mode.ALWAYS, "tab", "bfops"));
        check("ALWAYS still records the conflict truthfully", forced.foreignSidebar());

        CompatPolicy off = new CompatPolicy();
        check("NEVER refuses even a free sidebar",
                !off.mayUseSidebar(CompatPolicy.Mode.NEVER, null, "bfops"));
        // NEVER must not claim a slot, but it also must not falsely report a conflict.
        check("NEVER does not inspect the slot", !off.foreignSidebar());
    }

    // -------------------------------------------------------------------- teams

    private static void teamTests() {
        // No team at all: we may assign.
        CompatPolicy none = new CompatPolicy();
        check("AUTO assigns an unteamed player", none.mayUseTeams(CompatPolicy.Mode.AUTO, null, "bfops_"));
        check("unteamed player is not foreign", !none.foreignTeams());

        // Already in our own team: re-applying is normal, not a conflict.
        CompatPolicy ours = new CompatPolicy();
        check("AUTO keeps its own team", ours.mayUseTeams(CompatPolicy.Mode.AUTO, "bfops_arena_blue", "bfops_"));
        check("own team is not foreign", !ours.foreignTeams());

        // In TAB's team: yield, because a player can only be in one team.
        CompatPolicy tab = new CompatPolicy();
        check("AUTO yields when TAB owns the team", !tab.mayUseTeams(CompatPolicy.Mode.AUTO, "tab_team", "bfops_"));
        check("foreign team recorded", tab.foreignTeams());

        CompatPolicy forced = new CompatPolicy();
        check("ALWAYS overrides a foreign team",
                forced.mayUseTeams(CompatPolicy.Mode.ALWAYS, "tab_team", "bfops_"));

        CompatPolicy off = new CompatPolicy();
        check("NEVER never assigns teams", !off.mayUseTeams(CompatPolicy.Mode.NEVER, null, "bfops_"));

        // A prefix collision must not be read as ownership: "bfops_extra" is ours by prefix only if
        // another plugin happens to use it, which is why team names are namespaced and checked.
        CompatPolicy near = new CompatPolicy();
        check("prefix match still counts as ours",
                near.mayUseTeams(CompatPolicy.Mode.AUTO, "bfops_other_blue", "bfops_"));
    }

    // ------------------------------------------------------------------ minimap

    private static void minimapTests() {
        CompatPolicy xaero = new CompatPolicy();
        check("AUTO yields when Xaero is assumed", !xaero.mayUseMinimap(CompatPolicy.Mode.AUTO, true));
        check("Xaero assumption recorded", xaero.xaeroDetected());

        CompatPolicy clean = new CompatPolicy();
        check("AUTO draws when no Xaero", clean.mayUseMinimap(CompatPolicy.Mode.AUTO, false));
        check("no Xaero assumption recorded", !clean.xaeroDetected());

        CompatPolicy forced = new CompatPolicy();
        check("ALWAYS draws despite Xaero", forced.mayUseMinimap(CompatPolicy.Mode.ALWAYS, true));

        CompatPolicy off = new CompatPolicy();
        check("NEVER never draws", !off.mayUseMinimap(CompatPolicy.Mode.NEVER, false));
    }

    // ------------------------------------------------------------------- anchor

    private static void anchorTests() {
        CompatPolicy policy = new CompatPolicy();

        // With Xaero present we take the top-left, which is the corner it does not use by default.
        int[] left = policy.anchor(true, 1920, 1080, 120, -1, -1);
        check("Xaero present moves minimap to the left", left[0] == 8);
        check("Xaero present keeps it at the top", left[1] == 8);

        // Without Xaero we use the conventional top-right.
        int[] right = policy.anchor(false, 1920, 1080, 120, -1, -1);
        check("no Xaero uses the right corner", right[0] == 1920 - 120 - 8);

        // An explicit config position wins and is clamped on screen.
        int[] explicit = policy.anchor(true, 1920, 1080, 120, 400, 300);
        check("explicit position honoured", explicit[0] == 400 && explicit[1] == 300);

        int[] clamped = policy.anchor(true, 300, 200, 120, 9999, 9999);
        check("explicit position clamped to width", clamped[0] == 300 - 120);
        check("explicit position clamped to height", clamped[1] == 200 - 120);

        // A window smaller than the minimap must not produce a negative origin.
        int[] tiny = policy.anchor(true, 100, 80, 120, -1, -1);
        check("tiny window does not go negative", tiny[0] >= 0 && tiny[1] >= 0);

        int[] tinyExplicit = policy.anchor(true, 100, 80, 120, 50, 50);
        check("tiny window clamps explicit x", tinyExplicit[0] == 0);
    }

    // --------------------------------------------------------------- stickiness

    private static void stickinessTests() {
        // The important property: one observation of TAB must not be undone by the next free tick.
        // Without stickiness the two sides trade the slot every second and the player sees flicker.
        CompatPolicy policy = new CompatPolicy();
        check("first tick yields", !policy.mayUseSidebar(CompatPolicy.Mode.AUTO, "tab", "bfops"));
        check("stays yielded after the slot looks free",
                !policy.mayUseSidebar(CompatPolicy.Mode.AUTO, null, "bfops"));
        check("still yielded when it looks free again",
                !policy.mayUseSidebar(CompatPolicy.Mode.AUTO, "bfops", "bfops"));

        // Same for teams: once TAB is known to own them, do not take them back mid-match.
        CompatPolicy teams = new CompatPolicy();
        check("teams yielded", !teams.mayUseTeams(CompatPolicy.Mode.AUTO, "tab_team", "bfops_"));
        check("teams stay yielded", !teams.mayUseTeams(CompatPolicy.Mode.AUTO, null, "bfops_"));

        // reset() is the documented escape hatch, used when a new match starts.
        policy.reset();
        check("reset restores ownership", policy.mayUseSidebar(CompatPolicy.Mode.AUTO, null, "bfops"));
        check("reset clears the flag", !policy.foreignSidebar());

        teams.reset();
        check("reset restores team assignment", teams.mayUseTeams(CompatPolicy.Mode.AUTO, null, "bfops_"));
    }

    // -------------------------------------------------------------------- utils

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name);
        }
    }
}
