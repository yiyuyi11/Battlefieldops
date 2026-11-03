package cn.bfops;

import java.util.Locale;

/**
 * One place that decides which teammate marker art a blip gets, and what it falls back to.
 *
 * <h2>Why the art is tinted rather than coloured twice</h2>
 *
 * <p>The reference pack ships the class and death glyphs pre-coloured: {@code marker_class_*_team} is blue
 * (70,200,255) and {@code marker_class_*_squad} is green (110,251,145). This mod's palette is cyan, blue and
 * red by user requirement, so shipping the green squad variant as-is would put a colour in the HUD that
 * belongs to nothing here. The masks are pure white wherever they are opaque, so the tint is exact: a white
 * mask multiplied by {@code TacticalColors.FRIEND} is the same glyph in this mod's blue, and one multiplied by
 * {@code TacticalColors.MATE} is the same glyph in squad teal.
 *
 * <p>Death glyphs are shipped with a <em>black</em> palette and alpha-only coverage, which multiplied by a
 * colour would come out black. {@code marker_death_mask.png} is the alpha channel re-emitted as white and
 * is what actually gets drawn.
 *
 * <h2>Keeping it testable</h2>
 *
 * <p>The decision is a pure function of three facts (class id, squad, downed) returning a resource path or
 * {@code null}. Resource <em>existence</em> is checked at draw time only, so {@code MateMarkerSim} can pin
 * the naming rule without a client. That matters because the failure mode here is silent: a wrong format
 * string produces a missing texture and a marker that quietly falls back to a dot.
 */
final class MateMarker {
    /** Class glyph, tinted white mask; {@code %s} is the class stem, then {@code team}/{@code squad}. */
    static final String CLASS_PATH = "textures/gui/class_%s.png";
    /** Downed glyph; alpha-only coverage re-emitted as a white mask. */
    static final String DEATH_PATH = "textures/gui/marker_death_mask.png";

    /** The class ids this mod issues in the editor, and the reference pack's file stem for each. */
    private static final String[][] STEMS = {
            {"突击兵", "assault"},
            {"支援兵", "support"},
            {"工程兵", "engineer"},
            {"侦察兵", "recon"},
    };

    private MateMarker() { }

    /**
     * The file stem for a class id, or {@code null} when the class has no art.
     *
     * <p>Fixed class identity is shared with the editor/server. Legacy reference aliases also resolve to
     * the same asset rather than silently displaying another class.
     */
    static String stem(String kitId) {
        return FixedClasses.stem(kitId);
    }

    /** Every stem this mod ships art for, for diagnostics and tests. */
    static java.util.List<String> stems() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String[] pair : STEMS) out.add(pair[1]);
        return java.util.List.copyOf(out);
    }

    /**
     * The texture path for one teammate marker, relative to this mod's asset root, or {@code null}.
     *
     * @param kitId a class id from the snapshot, possibly empty
     * @param squad same class as the viewer, from the snapshot's {@code squad} field
     * @param downed downed teammates get the death glyph instead of their class
     */
    static String path(String kitId, boolean squad, boolean downed) {
        if (downed) return DEATH_PATH;
        String stem = stem(kitId);
        if (stem == null) return null;
        return String.format(Locale.ROOT, CLASS_PATH, stem);
    }

    /**
     * The tint for a marker.
     *
     * <p>Shape carries "downed" (the death glyph) and colour carries the relationship, which is the same
     * split the rest of this mod's markers use — so a downed teammate is still squad teal or teammate blue,
     * not a third colour that would have to be learned. Only the label underneath switches to amber.
     */
    static int tint(boolean squad, boolean downed) {
        return squad ? TacticalColors.MATE : TacticalColors.FRIEND;
    }

    /** The label colour for a teammate: amber when downed, the marker's own tint otherwise. */
    static int labelTint(boolean squad, boolean downed) {
        return downed ? TacticalColors.DOWNED : tint(squad, downed);
    }

    /**
     * Finds a teammate's snapshot entry by name.
     *
     * <p>By name because {@link Nameplates} has a live entity and this mod's member record has a UUID: the
     * entity carries no field for the roster entry, and the snapshot's blip already sends {@code name} for
     * exactly this lookup. Kept next to the art so the two lookups cannot drift apart.
     */
    static com.google.gson.JsonObject find(com.google.gson.JsonObject state, String playerName) {
        if (state == null || playerName == null || !state.has("mates")
                || !state.get("mates").isJsonArray()) return null;
        for (var element : state.getAsJsonArray("mates")) {
            if (!element.isJsonObject()) continue;
            com.google.gson.JsonObject mate = element.getAsJsonObject();
            if (mate.has("name") && playerName.equals(mate.get("name").getAsString())) return mate;
        }
        return null;
    }

    /**
     * Draws the marker texture centred on {@code (cx, cy)}, tinted.
     *
     * @return {@code true} when something was drawn; {@code false} means the caller should keep its own
     *         shape glyph (no art on the classpath, or a class with no glyph)
     */
    static boolean draw(net.minecraft.client.gui.GuiGraphics graphics, String kitId, boolean squad,
                        boolean downed, int cx, int cy, int size) {
        String path = path(kitId, squad, downed);
        if (path == null || size <= 0) return false;
        var location = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("battlefield_ops", path);
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.getResourceManager().getResource(location).isEmpty()) return false;
        int color = tint(squad, downed);
        graphics.setColor(((color >> 16) & 0xff) / 255f, ((color >> 8) & 0xff) / 255f,
                (color & 0xff) / 255f, ((color >>> 24) & 0xff) / 255f);
        if(downed)Bf1Ui.icon(graphics,"death_hud_default",cx-size/2,cy-size/2,size);
        else AssetIcons.draw(graphics,"role",kitId,cx-size/2,cy-size/2,size,size);
        graphics.setColor(1f, 1f, 1f, 1f);
        return true;
    }
}
