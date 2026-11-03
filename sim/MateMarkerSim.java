package cn.bfops;

/**
 * Headless checks for the teammate marker naming rule, tint selection and the class-id contract.
 *
 * <p>Nothing here touches a {@code GuiGraphics}: {@link MateMarker#path} and {@link MateMarker#tint} are pure
 * functions precisely so this can run without a client. The failure this guards against is silent — a wrong
 * format string yields a missing texture and a marker that quietly falls back to a dot, which looks like
 * "the class markers were never implemented" rather than like a bug.
 */
public final class MateMarkerSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        stems();
        paths();
        tints();
        fallbacks();
        System.out.println("MateMarkerSim: " + checks + " checks passed");
    }

    /** Every class the editor ships maps to a reference pack file stem, and nothing else does. */
    private static void stems() {
        check("assault".equals(MateMarker.stem("突击兵")), "突击兵 -> assault");
        check("support".equals(MateMarker.stem("支援兵")), "支援兵 -> support");
        check("engineer".equals(MateMarker.stem("工程兵")), "工程兵 -> engineer");
        check("recon".equals(MateMarker.stem("侦察兵")), "侦察兵 -> recon");
        check("assault".equals(MateMarker.stem("assault")), "the English stem is accepted too");
        check("recon".equals(MateMarker.stem("Recon")), "stem matching ignores case");
        check(MateMarker.stem(null) == null, "null class has no art");
        check(MateMarker.stem("") == null, "empty class has no art");
        check(MateMarker.stem("   ") == null, "blank class has no art");
        check(MateMarker.stem("自定义兵种") == null, "a custom class name has no art");
        check(MateMarker.stems().size() == 4, "exactly four classes ship a glyph");
        check(!MateMarker.stems().contains("medic"), "no invented class in the sheet list");
    }

    /** Paths are the real files this mod ships. */
    private static void paths() {
        check("textures/gui/class_assault.png".equals(MateMarker.path("突击兵", false, false)),
                "living teammate -> class_assault.png");
        check("textures/gui/class_assault.png".equals(MateMarker.path("突击兵", true, false)),
                "squad membership does not change the path (the mask is tinted, not swapped)");
        check("textures/gui/class_support.png".equals(MateMarker.path("支援兵", false, false)),
                "support uses its own glyph");
        check("textures/gui/marker_death_mask.png".equals(MateMarker.path("突击兵", false, true)),
                "downed -> the death mask, not the class glyph");
        check("textures/gui/marker_death_mask.png".equals(MateMarker.path("", false, true)),
                "downed with no class still gets the death mask");
        check("textures/gui/marker_death_mask.png".equals(MateMarker.path("自定义兵种", true, true)),
                "downed beats the missing-class case");
        check(MateMarker.path("自定义兵种", false, false) == null,
                "a custom class has no path and the caller falls back to a dot");
    }

    /** Colour carries the relationship; shape carries downed. */
    private static void tints() {
        check(MateMarker.tint(true, false) == TacticalColors.MATE, "your own class is squad teal");
        check(MateMarker.tint(false, false) == TacticalColors.FRIEND, "another class is teammate blue");
        check(MateMarker.tint(true, true) == TacticalColors.MATE,
                "a downed teammate keeps their relationship colour (the glyph says downed)");
        check(MateMarker.tint(false, true) == TacticalColors.FRIEND,
                "a downed stranger keeps teammate blue");
        check(MateMarker.labelTint(false, true) == TacticalColors.DOWNED,
                "only the label turns amber for a downed teammate");
        check(MateMarker.labelTint(false, false) == TacticalColors.FRIEND,
                "a living teammate's label uses the marker colour");
        check(MateMarker.labelTint(true, false) == TacticalColors.MATE,
                "a living same-class teammate's label is squad teal");
        check(TacticalColors.MATE != TacticalColors.FRIEND, "squad and team are distinguishable");
        check(TacticalColors.MATE != TacticalColors.ENEMY, "squad is not enemy red");
        check(TacticalColors.DOWNED != TacticalColors.ENEMY,
                "the downed label is not the enemy colour (a teammate must never read as hostile)");
    }

    /** The snapshot lookup that feeds the art, and its refusal to guess. */
    private static void fallbacks() {
        com.google.gson.JsonObject state = new com.google.gson.JsonObject();
        check(MateMarker.find(state, "Alex") == null, "no mates array -> no entry");
        com.google.gson.JsonArray mates = new com.google.gson.JsonArray();
        state.add("mates", mates);
        check(MateMarker.find(state, "Alex") == null, "empty mates array -> no entry");
        com.google.gson.JsonObject mate = new com.google.gson.JsonObject();
        mate.addProperty("name", "Alex");
        mate.addProperty("kit", "突击兵");
        mate.addProperty("squad", true);
        mates.add(mate);
        check(MateMarker.find(state, "Alex") == mate, "a name match returns the entry");
        check(MateMarker.find(state, "Steve") == null, "a different name finds nothing");
        check(MateMarker.find(state, null) == null, "a null name finds nothing");
        check(MateMarker.find(null, "Alex") == null, "a null snapshot finds nothing");
        // An older server sends no kit at all, which must read as "unknown class", not as a crash.
        com.google.gson.JsonObject legacy = new com.google.gson.JsonObject();
        legacy.addProperty("name", "Alex");
        mates.add(legacy);
        check(MateMarker.find(state, "Alex") == mate, "an earlier matching entry still wins");
        check(Engine.str(legacy, "kit", "").isEmpty(), "a pre-0.6.7 blip reads as an empty class");
        check(!legacy.has("squad"), "a pre-0.6.7 blip has no squad field (read as false, never as true)");
    }
}
