package cn.bfops;

/**
 * Checks for the two newest features: hiding Xaero during a match, and the combined map+scoreboard
 * overlay.
 *
 * <p>The Xaero part is checked as a <b>state machine</b> rather than by touching the real config, since
 * driving Xaero needs a running client. The property that matters is not "does it hide" but "can it ever
 * leave the player's own setting mutated" — that is pure logic, and it is what these checks pin: hide
 * must capture before it writes, and every exit path must restore the captured value rather than a
 * hard-coded {@code true}.
 *
 * <p>The overlay part checks the split, because a layout that gives the map zero pixels or pushes the
 * scoreboard off-screen is a layout bug no compiler catches.
 */
public final class BriefingSim {
    private static int checks;

    public static void main(String[] args) {
        splitChoosesBySpace();
        panesNeverOverlapOrOverflow();
        mapGetsAUsableSize();
        scoreOnlyWhenNothingFits();
        hudMinimapNeverDisappears();
        gateRestoresTheCapturedValue();
        gateIsIdempotent();
        gateGivesUpSafely();
        gateSurvivesACrash();
        gateKeepsTheRecordUntilTheWriteSucceeds();
        System.out.println("BriefingSim: " + checks + " checks passed");
    }

    // ------------------------------------------------------------------ overlay layout

    private static void splitChoosesBySpace() {
        // A normal window gets both panes side by side.
        require(Briefing.split(1280, 720) == Briefing.Split.SIDE_BY_SIDE, "wide window is side by side");
        require(Briefing.split(640, 480) == Briefing.Split.SIDE_BY_SIDE, "640x480 is side by side");
        // Narrow but tall: stack instead of squeezing two unreadable columns.
        require(Briefing.split(360, 720) == Briefing.Split.STACKED, "narrow tall stacks");
        // Tiny window: the scoreboard is the part that cannot be dropped, so the map goes.
        require(Briefing.split(200, 120) == Briefing.Split.SCORE_ONLY, "tiny window is score only");
        require(Briefing.split(0, 0) == Briefing.Split.SCORE_ONLY, "degenerate size is score only");
        checks++;
    }

    private static void panesNeverOverlapOrOverflow() {
        int[][] sizes = {{1280, 720}, {640, 480}, {420, 300}, {360, 720}, {200, 120}, {100, 60}};
        for (int[] size : sizes) {
            Layout.Rect area = new Layout.Rect(14, 26, size[0], size[1]);
            Briefing.Split split = Briefing.split(area.w(), area.h());
            Layout.Rect[] panes = Briefing.panes(split, area);
            Layout.Rect map = panes[0], scores = panes[1];
            require(scores != null, "scores pane always exists for " + size[0] + "x" + size[1]);
            require(scores.x() >= area.x() && scores.y() >= area.y(),
                    "scores starts inside the area for " + size[0] + "x" + size[1]);
            require(scores.right() <= area.right() && scores.bottom() <= area.bottom(),
                    "scores stays inside the area for " + size[0] + "x" + size[1]);
            if (map != null) {
                require(!map.overlaps(scores),
                        "panes must not overlap for " + size[0] + "x" + size[1]);
                require(map.x() >= area.x() && map.right() <= area.right(),
                        "map stays inside the area for " + size[0] + "x" + size[1]);
            }
        }
        checks++;
    }

    private static void mapGetsAUsableSize() {
        // Whenever a map is shown at all it must be at least MIN_MAP in both directions; a 20px map is
        // indistinguishable from a smudge and would only eat space the scoreboard needs.
        int[][] sizes = {{1280, 720}, {640, 480}, {420, 300}, {360, 720}, {300, 400}};
        for (int[] size : sizes) {
            Layout.Rect area = new Layout.Rect(0, 0, size[0], size[1]);
            Briefing.Split split = Briefing.split(area.w(), area.h());
            Layout.Rect map = Briefing.panes(split, area)[0];
            if (map == null) continue;
            require(map.w() >= Briefing.MIN_MAP, "map width >= MIN_MAP at " + size[0] + "x" + size[1]
                    + " (was " + map.w() + ")");
            require(map.h() >= Briefing.MIN_MAP, "map height >= MIN_MAP at " + size[0] + "x" + size[1]
                    + " (was " + map.h() + ")");
        }
        checks++;
    }

    private static void scoreOnlyWhenNothingFits() {
        Layout.Rect area = new Layout.Rect(10, 10, 180, 100);
        Layout.Rect[] panes = Briefing.panes(Briefing.Split.SCORE_ONLY, area);
        require(panes[0] == null, "score-only has no map");
        require(panes[1].equals(area), "score-only gives the scores the whole area");
        checks++;
    }

    // ------------------------------------------------------------------ HUD minimap interplay

    private static void hudMinimapNeverDisappears() {
        // The ordinary cases keep their old meanings.
        require(Briefing.showHudMinimap(true, false, false), "our map when Xaero is absent");
        require(Briefing.showHudMinimap(true, true, false), "our map if the policy insists alongside Xaero");
        require(!Briefing.showHudMinimap(false, true, false), "yield the corner to a visible Xaero");
        require(!Briefing.showHudMinimap(false, false, false), "an explicit NEVER is honoured");

        // The case this exists for: the policy yielded the corner because Xaero is installed, and Xaero
        // is then hidden for the match. Staying quiet would leave the player with no minimap at all.
        require(Briefing.showHudMinimap(false, true, true),
                "take the corner back once Xaero is hidden for the match");

        // Hiding Xaero when the policy never yielded anything changes nothing.
        require(Briefing.showHudMinimap(true, true, true), "hidden Xaero does not suppress our own map");

        // Corner placement follows visibility, so the two maps never overlap while both are up.
        require(Briefing.hudMinimapX(true, 1280, 120) == 8, "left corner while Xaero owns the right");
        require(Briefing.hudMinimapX(false, 1280, 120) == 1280 - 120 - 8,
                "right corner once Xaero is hidden");
        checks++;
    }

    // ------------------------------------------------------------------ Xaero gate state machine

    /**
     * Models the gate's contract without Xaero: hide captures first, restore replays the capture.
     *
     * <p>This mirrors {@code XaeroGate.apply} closely enough to catch the failure that matters — a
     * restore that writes {@code true} instead of the value it found, which would switch Xaero's
     * minimap on for a player who had deliberately turned it off.
     */
    private static final class Gate {
        Boolean saved;
        Boolean profile;
        boolean unavailable;
        int writes;

        /** @return true when the write actually happened */
        boolean apply(boolean hide) {
            if (hide && saved != null) return false;
            if (unavailable) return false;
            if (hide) {
                if (!(profile instanceof Boolean captured)) {
                    unavailable = true;
                    return false;
                }
                saved = captured;
                profile = false;
                writes++;
                return true;
            }
            Boolean restore = saved;
            saved = null;
            if (restore != null) {
                profile = restore;
                writes++;
                return true;
            }
            return false;
        }
    }

    private static void gateRestoresTheCapturedValue() {
        // The important case: a player who had already disabled Xaero's minimap must keep it disabled.
        Gate off = new Gate();
        off.profile = false;
        off.apply(true);
        require(Boolean.FALSE.equals(off.profile), "hide turns the minimap off");
        off.apply(false);
        require(Boolean.FALSE.equals(off.profile), "restore returns to the player's OFF, not to ON");
        require(off.saved == null, "restore clears the capture");

        // And the ordinary case: was on, ends on.
        Gate on = new Gate();
        on.profile = true;
        on.apply(true);
        require(Boolean.FALSE.equals(on.profile), "hide turns the minimap off");
        on.apply(false);
        require(Boolean.TRUE.equals(on.profile), "restore returns to the player's ON");

        // A null capture (Xaero not readable) must not be written as a value.
        Gate unknown = new Gate();
        unknown.profile = null;
        unknown.apply(true);
        unknown.apply(false);
        require(unknown.profile == null, "an unknown capture is not invented as true");
        checks++;
    }

    private static void gateIsIdempotent() {
        Gate gate = new Gate();
        gate.profile = true;
        // Called every frame by the render loop; only the transition may write.
        gate.apply(true);
        int afterFirst = gate.writes;
        gate.apply(true);
        gate.apply(true);
        require(gate.writes == afterFirst, "repeated hide does not rewrite the profile");
        gate.apply(false);
        int afterRestore = gate.writes;
        gate.apply(false);
        gate.apply(false);
        require(gate.writes == afterRestore, "repeated show does not rewrite the profile");
        require(Boolean.TRUE.equals(gate.profile), "profile ends where the player left it");
        checks++;
    }

    private static void gateGivesUpSafely() {
        // A reflective failure must leave Xaero alone rather than half-applied.
        Gate gate = new Gate();
        gate.profile = true;
        gate.unavailable = true;
        require(!gate.apply(true), "an unavailable gate performs no write");
        require(Boolean.TRUE.equals(gate.profile), "an unavailable gate leaves the setting untouched");
        checks++;
    }

    private static void gateSurvivesACrash() {
        // A session that dies mid-match leaves a record; the next session must repair the profile even
        // though it never itself hid anything.
        RecordGate crashed = new RecordGate();
        crashed.profile = true;
        crashed.apply(true);
        require(Boolean.FALSE.equals(crashed.profile), "the dead session had hidden the minimap");
        require(crashed.saved != null, "the dead session captured the original value");
        Boolean record = crashed.saved;

        // New session: nothing in memory, only the leftover record on disk.
        RecordGate next = new RecordGate();
        next.profile = false;
        next.leftover = record;
        next.withLeftover = true;
        next.apply(false);
        require(Boolean.TRUE.equals(next.profile), "the leftover record restores the original setting");
        require(next.cleared, "the record is cleared once it has been honoured");

        // A clean session with no leftovers must not touch anything.
        RecordGate clean = new RecordGate();
        clean.profile = true;
        clean.apply(false);
        require(Boolean.TRUE.equals(clean.profile), "a clean start changes nothing");
        require(clean.writes == 0, "a clean start performs no writes");
        checks++;
    }

    private static void gateKeepsTheRecordUntilTheWriteSucceeds() {
        // If the restore write throws, the record must survive so the next launch can retry. Otherwise
        // the player's setting is lost with no trace of what it should be.
        RecordGate gate = new RecordGate();
        gate.profile = true;
        gate.apply(true);
        gate.failWrites = true;
        gate.apply(false);
        require(gate.cleared == false, "a failed restore keeps the record for the next launch");
        require(gate.saved != null, "a failed restore keeps the in-memory capture too");
        checks++;
    }

    /**
     * The record lifecycle, with the capture and the on-disk record kept as separate fields.
     *
     * <p>They really are two things — a field dies with the process, a file does not — and the invariant
     * under test is that the file may only disappear <em>after</em> the restore write has succeeded,
     * because it is the only surviving copy of the player's original setting.
     */
    private static final class RecordGate {
        /** In-memory capture; null when this session is not hiding Xaero. */
        Boolean saved;
        /** The player's actual setting. */
        Boolean profile;
        /** What a previous crashed session left on disk. */
        Boolean leftover;
        boolean withLeftover;
        boolean cleared;
        boolean failWrites;
        int writes;

        boolean apply(boolean hide) {
            if (hide) {
                if (saved != null) return false;
                if (!(profile instanceof Boolean captured)) return false;
                saved = captured;
                profile = false;
                writes++;
                return true;
            }
            Boolean pending = saved;
            if (pending == null && !withLeftover) return false;
            if (pending == null) pending = leftover;
            if (failWrites) return false;
            if (pending != null) {
                profile = pending;
                writes++;
            }
            saved = null;
            cleared = true;
            return true;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
