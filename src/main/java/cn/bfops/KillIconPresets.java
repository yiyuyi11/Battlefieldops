package cn.bfops;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 37 official presets, loaded from the pack's own {@code assets/.../presets/official/}.
 *
 * <p>A preset is a bag of per-element config objects. The reference's {@code ConfigManager} keyed them by
 * {@code "<category>/<name>"} strings and read them on every frame; this port parses each preset once into
 * {@link KillIconScroll.Config} plus the handful of shared values the renderers need, and keeps the raw JSON
 * around for the keys the port does not model.
 *
 * <p><b>Why the raw JSON is kept.</b> A preset has 1,162 distinct (mode, key) pairs across all modes. The
 * port models the ones that change what the player sees for the modes it implements; the rest are card
 * animation timings, per-kind colour overrides and editor-only knobs. Keeping the source object means an
 * unmodelled key degrades to "the pack's default applies" rather than to "the preset failed to load", and it
 * means a key added by a future pack still round-trips.
 *
 * <p><b>Why parsing is separated from resource loading.</b> {@link #parse} takes a JSON string, so the whole
 * preset corpus can be validated in the offline test harness without a resource manager, a client, or a game.
 * {@link #load} is the thin I/O wrapper.
 */
final class KillIconPresets {

    /**
     * One loaded preset.
     *
     * @param id       the file number as a string, e.g. {@code "00037"}; stable and ordered
     * @param name     the preset's display name, taken from its {@code display_name}
     * @param mode     the primary icon element, e.g. {@code kill_icon/scrolling}; never {@code null} once a
     *                 preset has any icon element, and a bare prefix such as {@code subtitle} for the two
     *                 subtitle-only presets
     * @param config   the resolved scrolling config, or {@code null} for a preset of another mode
     * @param raw      the primary element's body, kept for keys the port does not model
     * @param elements every element the preset configures, keyed by its full key ({@code kill_icon/card_bar},
     *                 {@code subtitle/score}, …)
     */
    record Preset(String id, String name, String mode, KillIconScroll.Config config, JsonObject raw,
                  Map<String, JsonObject> elements) {
        boolean usesScrolling() {
            return "kill_icon/scrolling".equals(mode);
        }

        /**
         * The body of one element, or {@code null} when the preset does not configure it.
         *
         * <p>This is what makes a preset more than one thing. A real preset frequently configures several
         * elements at once — {@code 00003} draws both a {@code card} and a {@code card_bar}, {@code 00037} draws
         * a {@code scrolling} strip <em>and</em> an {@code honor} badge row — and a model that kept only the
         * first would silently drop half the look.
         */
        JsonObject element(String elementKey) {
            // A null key must read as "not configured" rather than throwing: `Map.copyOf` rejects null keys,
            // and a caller assembling a key from optional config would otherwise crash on a missing piece.
            if (elementKey == null || elements == null) return null;
            return elements.get(elementKey);
        }

        /** Whether this preset configures the given element at all. */
        boolean has(String elementKey) {
            return element(elementKey) != null;
        }

        /** The element keys this preset configures, sorted, for tests and for the config GUI. */
        java.util.List<String> elementKeys() {
            return elements == null ? java.util.List.of()
                    : elements.keySet().stream().sorted().toList();
        }
    }

    private final List<Preset> presets;
    private final Map<String, Preset> byId;

    private KillIconPresets(List<Preset> presets) {
        this.presets = List.copyOf(presets);
        Map<String, Preset> index = new LinkedHashMap<>();
        for (Preset preset : presets) index.put(preset.id(), preset);
        this.byId = Map.copyOf(index);
    }

    static KillIconPresets empty() {
        return new KillIconPresets(List.of());
    }

    List<Preset> all() {
        return presets;
    }

    int size() {
        return presets.size();
    }

    Preset byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /**
     * The preset to draw when none was chosen, or when the chosen one is not in this build.
     *
     * <p>Falls back to {@code 00037} — the pack's "Battlefield 6 模式", which is the newest complete look and
     * the one this port's own default style was already modelled on — and then to whatever the pack's first
     * preset happens to be.
     */
    Preset fallback() {
        Preset preferred = byId("00037");
        if (preferred != null) return preferred;
        return presets.isEmpty() ? null : presets.get(0);
    }

    /**
     * Parses one preset document.
     *
     * <p>The document may configure several elements at once (a real preset does: an icon mode plus two or
     * three subtitle modes). The first {@code kill_icon/*} element becomes the preset's {@link Preset#mode},
     * because the icon mode is what identifies the look; the rest stay reachable through {@link Preset#raw}
     * only as their first element, which is the limitation of modelling one mode per preset rather than a
     * hierarchy of them.
     *
     * @return the preset, or {@code null} when the document is not a preset at all
     */
    static Preset parse(String id, String json) {
        if (json == null || json.isBlank()) return null;
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) return null;
            root = element.getAsJsonObject();
        } catch (RuntimeException ex) {
            return null;
        }
        String name = root.has("display_name") ? root.get("display_name").getAsString() : id;
        // Collect every element the preset configures, keyed by its full key. This is the whole look: a preset
        // routinely draws several things at once, so keeping only one element loses half of it.
        Map<String, JsonObject> elements = new LinkedHashMap<>();
        for (String key : root.keySet()) {
            if (!key.contains("/")) continue;
            JsonElement value = root.get(key);
            if (!value.isJsonObject()) continue;
            elements.put(key, value.getAsJsonObject());
        }

        String mode = null;
        JsonObject body = null;
        for (String key : elements.keySet()) {
            if (!key.startsWith("kill_icon/")) continue;
            // Prefer scrolling when a preset configures several icon modes, because it is the mode with the
            // most complete implementation here; otherwise the first icon mode in document order wins, which
            // is the pack's own ordering and therefore stable between loads.
            if (mode == null || "kill_icon/scrolling".equals(key)) {
                mode = key;
                body = elements.get(key);
            }
            if ("kill_icon/scrolling".equals(key)) break;
        }
        // Two shipped presets (00005, 00006) configure only subtitle elements and no icon mode at all. They are
        // real, selectable looks in the reference -- "subtitle only" is a legitimate choice -- so discarding
        // them would silently shrink the preset list by two. They are kept with a null mode, meaning "this
        // preset draws no kill icon"; the renderer treats that as "nothing to draw" rather than as an error.
        if (mode == null) {
            for (String key : elements.keySet()) {
                mode = key.substring(0, key.indexOf('/'));
                body = elements.get(key);
                break;
            }
        }
        if (mode == null) return null;
        KillIconScroll.Config config = "kill_icon/scrolling".equals(mode) ? scrollConfig(body) : null;
        return new Preset(id, name, mode, config, body, Map.copyOf(elements));
    }

    /**
     * Reads a scrolling config out of a preset object.
     *
     * <p>Every key is optional and every absence falls back to the reference's own default, which is why this
     * is a long list of {@code has} checks rather than a schema. A preset that ships only {@code scale} must
     * still look like the reference's default; a preset that ships everything must look exactly as authored.
     */
    static KillIconScroll.Config scrollConfig(JsonObject body) {
        KillIconScroll.Config config = new KillIconScroll.Config();
        if (body == null) return config;
        config.scale = number(body, "scale", config.scale);
        config.xOffset = (int) number(body, "x_offset", config.xOffset);
        config.yOffset = (int) number(body, "y_offset", config.yOffset);
        config.anchor = body.has("screen_anchor")
                ? ScreenAnchor.orDefault(body.get("screen_anchor").getAsString())
                : ScreenAnchor.DEFAULT;
        // `scroll_direction` is a _string_ in the shipped presets, and "right" means the strip moves the other
        // way from the default: the reference maps "right" to -1 and everything else to +1. Reading it as an
        // int, or assuming "right" means +1, both produce a mirrored strip that still looks plausible, which is
        // why this is asserted by a test rather than left to inspection.
        config.direction = body.has("scroll_direction")
                && "right".equals(body.get("scroll_direction").getAsString()) ? -1 : 1;
        config.pinNewest = flag(body, "pin_newest_icon", false);
        config.blinkFade = flag(body, "blink_fade_animation", false);
        config.iconDelayMs = (long) number(body, "icon_delay_ms", 0);
        config.displayDuration = (long) number(body, "display_duration_ms",
                number(body, "display_duration_seconds", 0) > 0
                        ? number(body, "display_duration_seconds", 0) * 1000L
                        : KillIconScroll.DEFAULT_DISPLAY_DURATION);
        // Several presets author the duration in seconds as a float (`display_duration: 3.25`).
        if (!body.has("display_duration_ms") && body.has("display_duration")) {
            config.displayDuration = Math.round(number(body, "display_duration", 3.25f) * 1000.0f);
        }
        config.animationDuration = Math.round(number(body, "animation_duration",
                KillIconScroll.DEFAULT_ANIMATION_DURATION / 1000.0f) * 1000.0f);
        config.fadeOutDuration = Math.round(number(body, "fade_out_duration",
                KillIconScroll.DEFAULT_FADE_OUT_DURATION / 1000.0f) * 1000.0f);
        config.positionAnimationDuration = Math.round(number(body, "position_animation_duration",
                KillIconScroll.DEFAULT_POSITION_ANIMATION_DURATION / 1000.0f) * 1000.0f);
        config.startScale = number(body, "start_scale", KillIconScroll.DEFAULT_START_SCALE);
        config.iconSpacing = number(body, "icon_spacing", KillIconScroll.DEFAULT_ICON_SPACING);
        // `max_visible_icons` is `5.0` in preset 00037 -- a JSON float where the reference calls `getAsInt()`.
        // Gson's `getAsInt` on "5.0" throws NumberFormatException, so the reference itself would fail to load
        // that preset's icon count. Reading through a float and rounding is both tolerant and correct.
        config.maxVisible = Math.round(number(body, "max_visible_icons", KillIconScroll.DEFAULT_MAX_VISIBLE));
        config.maxPending = Math.round(number(body, "max_pending_icons", KillIconScroll.DEFAULT_MAX_PENDING));
        config.displayInterval = (long) number(body, "display_interval_ms",
                KillIconScroll.DEFAULT_DISPLAY_INTERVAL);
        // The durations above are authored in seconds in every shipped preset, so an animation shorter than a
        // frame would be a bug in the preset rather than an intent. Guard the divisions that follow.
        config.animationDuration = Math.max(1L, config.animationDuration);
        config.fadeOutDuration = Math.max(1L, config.fadeOutDuration);
        config.positionAnimationDuration = Math.max(1L, config.positionAnimationDuration);
        config.maxVisible = Math.max(1, config.maxVisible);
        config.maxPending = Math.max(0, config.maxPending);
        config.displayInterval = Math.max(0L, config.displayInterval);
        return config;
    }

    private static float number(JsonObject body, String key, float fallback) {
        if (body == null || !body.has(key)) return fallback;
        try {
            return body.get(key).getAsFloat();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static boolean flag(JsonObject body, String key, boolean fallback) {
        if (body == null || !body.has(key)) return fallback;
        try {
            return body.get(key).getAsBoolean();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** Builds a set from already-parsed presets, in the order given. */
    static KillIconPresets of(List<Preset> presets) {
        return new KillIconPresets(presets == null ? List.of() : new ArrayList<>(presets));
    }
}
