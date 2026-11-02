package cn.bfops;

import java.util.List;

/**
 * The eleven award kinds the 656 kill-icon pack knows about.
 *
 * <p>Ported from {@code gd656killicon 1.3.3}'s {@code KillTypeDefinitionsArea}, which is the authority for
 * every per-kind detail the renderers need. The list is kept as data rather than a Java {@code enum} for the
 * same reason the reference did: the fields are looked up by config key at runtime, and a preset names its
 * colours and textures through those keys. An enum would force a switch at every lookup and lose the ability
 * to say "this kind has no enable switch" without a null-case branch.
 *
 * <p><b>Why the texture key and the filename differ.</b> {@code DESTROY_VEHICLE} has texture key
 * {@code destroy_vehicle}, but the file on disk is {@code killicon_scrolling_destroyvehicle.png} — the
 * reference's {@code normalizeDestroyVehicle} drops the underscore. That mismatch is not cosmetic: looking up
 * the literal key produces a missing texture, and a missing texture in a kill feed is invisible rather than
 * loud, so it survives testing. The normalisation lives in {@link #fileName} so every caller shares it.
 *
 * @param id          stable identifier, used in config and in the network payload
 * @param type        the numeric id the reference used; kept because presets and saved player data reference it
 * @param textureKey  the name the pack's config calls this kind's art
 * @param bf1TextureKey the override for {@code battlefield1}, which reuses {@code default} for several kinds
 * @param formatKey   the {@code formats/*.json} key holding this kind's label
 * @param enableKey   the config switch that can hide this kind entirely, or {@code null} when it cannot be hidden
 * @param ringEnableKey the config switch for this kind's ring effect, or {@code null} when it has none
 * @param placeholderColorKey the config key for the dimmed part of this kind's label
 * @param emphasisColorKey    the config key for the highlighted part
 * @param soundSlot   the sound slot this kind plays, as the pack's {@code sounds} config names it
 */
record KillType(String id, int type, String textureKey, String bf1TextureKey, String formatKey,
                String enableKey, String ringEnableKey, String placeholderColorKey, String emphasisColorKey,
                String soundSlot) {

    private static final List<KillType> ALL = List.of(
            new KillType("NORMAL", 0, "default", "default", "format_normal",
                    "enable_normal_kill", null, "color_normal_placeholder", "color_normal_emphasis",
                    "scrolling_default"),
            new KillType("HEADSHOT", 1, "headshot", "headshot", "format_headshot",
                    "enable_headshot_kill", "enable_ring_effect_headshot",
                    "color_headshot_placeholder", "color_headshot_emphasis", "scrolling_headshot"),
            new KillType("EXPLOSION", 2, "explosion", "explosion", "format_explosion",
                    "enable_explosion_kill", "enable_ring_effect_explosion",
                    "color_explosion_placeholder", "color_explosion_emphasis", "scrolling_explosion"),
            new KillType("CRIT", 3, "crit", "crit", "format_crit",
                    "enable_crit_kill", "enable_ring_effect_crit",
                    "color_crit_placeholder", "color_crit_emphasis", "scrolling_crit"),
            new KillType("ASSIST", 4, "assist", "default", "format_assist",
                    "enable_assist_kill", null, "color_assist_placeholder", "color_assist_emphasis",
                    "scrolling_assist"),
            new KillType("DESTROY_VEHICLE", 5, "destroy_vehicle", "destroy_vehicle", "format_destroy_vehicle",
                    "enable_destroy_vehicle_kill", null,
                    "color_destroy_vehicle_placeholder", "color_destroy_vehicle_emphasis", "scrolling_vehicle"),
            new KillType("CAPTURE", 6, "capture", "default", "format_capture",
                    "enable_capture_kill", null, "color_capture_placeholder", "color_capture_emphasis",
                    "scrolling_assist"),
            new KillType("VEHICLE_DESTROY_ASSIST", 7, "vehicle_destroy_assist", "default", "format_normal",
                    null, null, "color_normal_placeholder", "color_normal_emphasis", "scrolling_assist"),
            new KillType("MEDIC", 8, "medic", "default", "format_normal",
                    null, null, "color_normal_placeholder", "color_normal_emphasis", "scrolling_assist"),
            new KillType("SPOT_ASSIST", 9, "spot_assist", "default", "format_spot_assist",
                    "enable_spot_assist_kill", null, "color_spot_assist_placeholder", "color_spot_assist_emphasis",
                    "scrolling_assist"),
            new KillType("RESCUE", 10, "medic", "default", "format_rescue",
                    "enable_rescue_kill", null, "color_rescue_placeholder", "color_rescue_emphasis",
                    "scrolling_assist"));

    /**
     * Every kind, in the reference's order.
     *
     * <p>Order matters in two places and both are visible: the config editor lists kinds in this order, and
     * {@link #byType} resolves a saved numeric id through the same list.
     */
    static List<KillType> all() {
        return ALL;
    }

    /** The kind with this numeric id, or {@code NORMAL} when the id is unknown. */
    static KillType byType(int type) {
        for (KillType kind : ALL) if (kind.type == type) return kind;
        return ALL.get(0);
    }

    /** The kind with this identifier, or {@code NORMAL} when it is unknown. */
    static KillType byId(String id) {
        if (id != null) for (KillType kind : ALL) if (kind.id.equals(id)) return kind;
        return ALL.get(0);
    }

    /**
     * The texture file stem for this kind, as the pack names files on disk.
     *
     * <p>Only {@code DESTROY_VEHICLE} is affected, and only because the reference's own normaliser is part of
     * the file format: the art ships as {@code *_destroyvehicle.png}. Applying the rule from the data rather
     * than from a special case at each call site means a second exception is a one-line change here.
     */
    String fileNameStem() {
        return "destroy_vehicle".equals(textureKey) ? "destroyvehicle" : textureKey;
    }

    /** The texture key {@code battlefield1} uses for this kind. */
    String bf1Stem() {
        return "destroy_vehicle".equals(bf1TextureKey) ? "destroyvehicle" : bf1TextureKey;
    }

    /** True when a preset can hide this kind; the reference leaves several kinds without a switch. */
    boolean hideable() {
        return enableKey != null;
    }

    /** True when this kind can draw a ring effect. */
    boolean ringable() {
        return ringEnableKey != null;
    }

    /**
     * The texture file for this kind in a given preset and mode.
     *
     * <p>Not every kind has art in every family, and the reference resolves that with per-preset tables rather
     * than by falling back to a single default. Reproducing the tables matters because the presets are the
     * product: {@code 00007} is "Battlefield 5 mode", and its capture/medic/vehicle-assist icons live only in
     * the {@code battlefield5} family, so a generic {@code scrolling_capture} lookup draws nothing at all.
     *
     * <p>The rule, in the reference's own order of precedence:
     *
     * <ol>
     *   <li>{@code spot_assist} maps to {@code scrolling_assist} in every preset except {@code 00037};</li>
     *   <li>presets {@code 00007}, {@code 00001}, {@code 00008} and {@code 00037} each have their own table,
     *       because they are the four "named look" presets that swap whole families;</li>
     *   <li>any other preset uses {@code killicon_scrolling_<stem>}, except {@code capture}, which the pack
     *       only ships as {@code killicon_df_capture}.</li>
     * </ol>
     *
     * @param presetId the preset identifier, as {@link KillIconPresets} numbers them
     */
    String scrollingFile(String presetId) {
        if ("spot_assist".equals(textureKey) && !"00037".equals(presetId)) {
            return "killicon_scrolling_assist.png";
        }
        return switch (presetId == null ? "" : presetId) {
            case "00007" -> switch (textureKey) {
                case "headshot" -> "killicon_battlefield5_headshot.png";
                case "assist" -> "killicon_battlefield5_assist.png";
                case "capture" -> "killicon_battlefield5_capture.png";
                case "destroy_vehicle" -> "killicon_battlefield5_destroyvehicle.png";
                case "vehicle_destroy_assist" -> "killicon_battlefield5_vehicle_destroy_assist.png";
                case "medic" -> "killicon_battlefield5_medic.png";
                default -> "killicon_battlefield5_default.png";
            };
            case "00001" -> switch (textureKey) {
                case "vehicle_destroy_assist" -> "killicon_df_vehicle_destroy_assist.png";
                case "medic" -> "killicon_df_medic.png";
                case "capture" -> "killicon_df_capture.png";
                default -> "killicon_scrolling_" + fileNameStem() + ".png";
            };
            case "00008" -> switch (textureKey) {
                case "headshot" -> "killicon_df_headshot.png";
                case "destroy_vehicle" -> "killicon_df_destroyvehicle.png";
                case "assist" -> "killicon_scrolling_assist.png";
                case "capture" -> "killicon_df_capture.png";
                case "vehicle_destroy_assist" -> "killicon_df_vehicle_destroy_assist.png";
                case "medic" -> "killicon_df_medic.png";
                default -> "killicon_df_default.png";
            };
            case "00037" -> switch (textureKey) {
                case "headshot" -> "killicon_battlefield6_headshot.png";
                case "explosion" -> "killicon_battlefield6_explosion.png";
                case "crit" -> "killicon_battlefield6_crit.png";
                case "destroy_vehicle" -> "killicon_battlefield6_destroyvehicle.png";
                case "assist" -> "killicon_battlefield6_assist.png";
                case "capture" -> "killicon_battlefield6_capture.png";
                case "vehicle_destroy_assist" -> "killicon_battlefield6_vehicle_destroy_assist.png";
                case "medic" -> "killicon_battlefield6_medic.png";
                case "spot_assist" -> "killicon_battlefield6_spot_assist.png";
                default -> "killicon_battlefield6_default.png";
            };
            // Any preset that does not override the family. `capture` has no `scrolling_capture` art in the
            // pack -- it only ships as the `df` variant -- so the default branch has to know that.
            default -> "capture".equals(textureKey)
                    ? "killicon_df_capture.png"
                    : "killicon_scrolling_" + fileNameStem() + ".png";
        };
    }

    /** The texture file for this kind in the {@code battlefield1} family. */
    String battlefield1File() {
        return "killicon_battlefield1_" + bf1Stem() + ".png";
    }

    /** The texture file for this kind in the {@code battlefield6} family, used by the 00037 preset. */
    String battlefield6File() {
        return "killicon_battlefield6_" + fileNameStem() + ".png";
    }

    /** The card-tab file for this kind, or the plain {@code default} card when the kind has no art of its own. */
    String cardFile(String variant) {
        // The card family ships six looks (default / headshot / explosion / crit / light), each in a `ct` and
        // a `t` half. Kinds with no card art fall back to `default`, which is what the reference's card
        // renderer does rather than skipping the icon.
        String look = switch (textureKey) {
            case "headshot" -> "headshot";
            case "explosion" -> "explosion";
            case "crit" -> "crit";
            default -> "default";
        };
        return "killicon_card_" + look + "_" + variant + ".png";
    }

    /** The combo file for this kind: the combo family is indexed by streak length, not by kind. */
    static String comboFile(int streak) {
        int index = Math.max(1, Math.min(6, streak));
        return "killicon_combo_" + index + ".png";
    }

    /**
     * The honour badge file for a {@code kill_icon/honor} format key, or {@code null} when the pack has none.
     *
     * <p>The badges are a separate collection from the scoring awards: there are 49 of them, named
     * {@code honor_<lowercase>.png}, and they key off the {@code formats/*.json} entries {@code format_<name>}.
     * They are <em>not</em> the same vocabulary as {@link AwardCatalog}'s 72 bonus ids — "assault",
     * "pathfinder" and "unstoppable" are honour titles, while "HEADSHOT" and "SQUAD_BEACON_DEPLOY" are scoring
     * events. Confusing the two produces a badge lookup that never resolves, which is why the two lookups are
     * separate methods with separate inputs rather than one "icon for a thing" helper.
     *
     * @param formatKey a key from {@code formats/*.json}, with or without the {@code format_} prefix
     */
    static String honourFile(String formatKey) {
        if (formatKey == null || formatKey.isBlank()) return null;
        String name = formatKey.startsWith("format_") ? formatKey.substring("format_".length()) : formatKey;
        // Badge filenames are all lowercase (`honor_ace_pilot.png`), while award ids are all upper case
        // (`AVENGE`). Lowercasing here is what lets one method serve both callers without a second index.
        name = name.toLowerCase(java.util.Locale.ROOT);
        // `format_best` is the "new personal best" label rather than a badge, and the pack ships no art for it.
        if (name.equals("best")) return null;
        // The title must actually be one of the 49 the pack ships art for. Without this check the method would
        // happily build a path for *any* string -- `honor/headshot.png` for an award id, say -- and a missing
        // texture fails silently by drawing nothing. Validating against the list turns a typo into an obvious
        // "no badge" rather than an invisible one.
        if (!honourTitles().contains(name)) return null;
        return "honor/honor_" + name + ".png";
    }

    /**
     * The 49 honour titles the pack ships badges for.
     *
     * <p>Delegates to {@link HonourCatalog}, which is the table that also carries each honour's condition and
     * score. Keeping a second hand-written list here would be a copy of the answer rather than a check on it,
     * and the two would drift the first time an honour was added.
     */
    static java.util.List<String> honourTitles() {
        return HonourCatalog.all().stream().map(HonourCatalog::id).toList();
    }

    /**
     * The ordered list of files to try for a kind, best first.
     *
     * <p>The pack does not ship art for every kind in every family, and it does not say so anywhere: the
     * reference simply asks the resource manager for a file and draws nothing when it is absent. That silence
     * is a real defect in the source material — a medic award in a non-named preset looks like the award never
     * fired. Listing the fallbacks explicitly means the renderer can find a sensible stand-in and the reason
     * is written down rather than discovered by staring at a blank HUD.
     *
     * <p>Verified absences, from the shipped 177 files:
     *
     * <ul>
     *   <li>{@code scrolling_medic} does not exist; {@code df_medic} and {@code battlefield5/6_medic} do.</li>
     *   <li>{@code scrolling_vehicle_destroy_assist} does not exist; the {@code df} and {@code battlefield5/6}
     *       variants do.</li>
     *   <li>{@code scrolling_capture} does not exist at all; only {@code df_capture} and the named families.</li>
     * </ul>
     *
     * @param presetId the preset being drawn
     * @param family   which family's art is wanted: {@code scrolling}, {@code battlefield1} or {@code battlefield6}
     */
    java.util.List<String> textureCandidates(String presetId, String family) {
        java.util.List<String> files = new java.util.ArrayList<>(3);
        switch (family) {
            case "battlefield1" -> files.add(battlefield1File());
            case "battlefield6" -> files.add(battlefield6File());
            default -> files.add(scrollingFile(presetId));
        }
        // Stand-ins, in the order that keeps the award legible: the same kind in another family first,
        // then the pack's generic `df` art.
        if ("medic".equals(textureKey)) files.add("killicon_df_medic.png");
        if ("vehicle_destroy_assist".equals(textureKey)) files.add("killicon_df_vehicle_destroy_assist.png");
        if ("capture".equals(textureKey)) files.add("killicon_df_capture.png");
        if ("destroy_vehicle".equals(textureKey)) files.add("killicon_df_destroyvehicle.png");

        String fallback = "killicon_"
                + ("battlefield1".equals(family) ? "battlefield1_default" : "scrolling_default") + ".png";

        // De-duplicate, then force the generic default to the end.
        //
        // Both steps matter, and the second one is subtle. Some kinds have no art of their own in a family, so
        // their "primary" candidate *is* that family's `_default.png` -- CAPTURE in battlefield1, NORMAL
        // everywhere. Left in place that file would occupy the first slot, and the chain would end on something
        // more specific than the default, which is backwards: the last resort must be the file guaranteed to
        // exist. Moving it to the end also promotes the `df` stand-in ahead of it, so a capture draws a real
        // capture icon instead of a generic one.
        java.util.List<String> unique = new java.util.ArrayList<>(files.size() + 1);
        for (String file : files) {
            if (!file.equals(fallback) && !unique.contains(file)) unique.add(file);
        }
        unique.add(fallback);
        return unique;
    }
}
