package cn.bfops;

import com.google.gson.JsonObject;

/**
 * The coexistence decision core, deliberately free of every Minecraft type so it can be exercised
 * headlessly with a bare JDK.
 *
 * <p>This split is not decoration. The rules here are exactly the kind that fail silently in
 * production — a surface claimed and re-claimed by two plugins looks like "flicker" or "the sidebar
 * resets", never like a crash — so they need tests, and tests cannot boot a server.
 *
 * <p>Semantics: {@link Mode#AUTO} observes and yields; {@link Mode#ALWAYS} claims the surface
 * regardless; {@link Mode#NEVER} never touches it. Yielding is sticky, because re-claiming a surface
 * the other side owns is precisely what creates the visible fight.
 */
final class CompatPolicy {
    /** Ownership mode for one presentation surface. */
    enum Mode { AUTO, ALWAYS, NEVER }

    /** Someone else is displaying a sidebar objective that is not ours. */
    private boolean foreignSidebar;
    /** A player we manage is already in a team that is not ours. */
    private boolean foreignTeams;
    /** We assume the Xaero minimap is present in the pack. */
    private boolean xaeroDetected;

    /** Clears every sticky decision; called when a new match starts. */
    void reset() {
        foreignSidebar = false;
        foreignTeams = false;
        xaeroDetected = false;
    }

    boolean foreignSidebar() {
        return foreignSidebar;
    }

    boolean foreignTeams() {
        return foreignTeams;
    }

    boolean xaeroDetected() {
        return xaeroDetected;
    }

    /**
     * May we write {@code DisplaySlot.SIDEBAR}?
     *
     * <p>Observation happens even under {@link Mode#ALWAYS} so the GUI can still report who else is
     * writing — an operator who forced the sidebar on deserves to know why it looks wrong.
     *
     * @param shownObjectiveName objective currently on the sidebar slot, or {@code null} when free
     * @param ours               the objective name this mod owns
     */
    boolean mayUseSidebar(Mode mode, String shownObjectiveName, String ours) {
        if (mode == Mode.NEVER) return false;
        if (shownObjectiveName != null && !shownObjectiveName.equals(ours)) foreignSidebar = true;
        if (mode == Mode.ALWAYS) return true;
        return !foreignSidebar;
    }

    /**
     * May we put a player into one of our scoreboard teams?
     *
     * <p>Vanilla allows exactly one team per player. Adding a player who is already in TAB's team
     * would evict them from it, breaking TAB's nametag and tab-list formatting; TAB would then put
     * them back on its next refresh. Neither side wins and the player sees a flickering name.
     *
     * @param currentTeamName     team the player is in right now, or {@code null}
     * @param ownedTeamNamePrefix our namespaced prefix, e.g. {@code bfops_}
     */
    boolean mayUseTeams(Mode mode, String currentTeamName, String ownedTeamNamePrefix) {
        if (mode == Mode.NEVER) return false;
        if (currentTeamName != null && !currentTeamName.startsWith(ownedTeamNamePrefix)) {
            foreignTeams = true;
        }
        if (mode == Mode.ALWAYS) return true;
        return !foreignTeams;
    }

    /**
     * May we draw our own minimap?
     *
     * <p>Xaero is a client-side mod, so no server-side probe can see it. The only honest inputs are
     * "the pack ships it" and "the operator said otherwise".
     */
    boolean mayUseMinimap(Mode mode, boolean xaeroAssumed) {
        if (mode == Mode.NEVER) return false;
        if (mode == Mode.ALWAYS) return true;
        xaeroDetected = xaeroAssumed;
        return !xaeroAssumed;
    }

    /**
     * Where to anchor a minimap so it does not sit under Xaero's.
     *
     * @param configuredX explicit X from config, or negative for "choose for me"
     * @param configuredY explicit Y from config, or negative for "choose for me"
     * @return {@code {x, y}} in GUI pixels, clamped on screen for a non-degenerate window
     */
    int[] anchor(boolean xaeroPresent, int width, int height, int size, int configuredX, int configuredY) {
        if (configuredX >= 0 && configuredY >= 0) {
            // Clamp anyway: a position written on a large monitor must not vanish on a small one.
            return new int[]{
                    Math.max(0, Math.min(configuredX, Math.max(0, width - size))),
                    Math.max(0, Math.min(configuredY, Math.max(0, height - size)))};
        }
        // Xaero defaults to the top-right, so the free corner is the top-left — which is also clear
        // of vanilla's hotbar and of the boss bar.
        int x = xaeroPresent ? 8 : Math.max(8, width - size - 8);
        return new int[]{x, 8};
    }

    JsonObject report(Mode sidebar, Mode teams, Mode minimap) {
        JsonObject out = new JsonObject();
        out.addProperty("sidebar", sidebar.name());
        out.addProperty("teams", teams.name());
        out.addProperty("minimap", minimap.name());
        out.addProperty("foreignSidebar", foreignSidebar);
        out.addProperty("foreignTeams", foreignTeams);
        out.addProperty("xaero", xaeroDetected);
        return out;
    }
}
