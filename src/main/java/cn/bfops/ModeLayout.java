package cn.bfops;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The layout knobs every icon mode shares: where it sits, how big it is, how long it stays.
 *
 * <p>The pack's modes look different but answer the same six questions, so the answers are read once here
 * rather than six times in six renderers. Mode-specific settings (the card's team colours, the honour row's
 * hint box, the Valorant halo) stay in {@link KillIconHud} where they are used.
 *
 * <p><b>Why every field has a default.</b> A preset configures a subset of these keys — the pack's newest presets
 * configure more than its oldest ones — and the reference treats an absent key as "use the built-in default"
 * rather than as an error. Reproducing that means a preset written before a setting existed still renders in the
 * place and size it was authored for.
 *
 * <p><b>Why the numbers are read defensively.</b> The pack's own values are not consistently typed: it writes
 * {@code "max_visible_icons": 5.0} where an int is meant, and {@code "direction": "right"} where a sign is meant.
 * Gson's {@code getAsInt()} throws on the first and would have taken the whole preset down, so every read goes
 * through a converter that tolerates the shape the pack actually uses.
 */
record ModeLayout(
        boolean visible,
        float scale,
        float xOffset,
        float yOffset,
        String anchor,
        long displayDurationMs,
        long animationDurationMs,
        long fadeOutDurationMs,
        String team) {

    /** The reference's own defaults, taken from the presets that configure the fewest keys. */
    static final ModeLayout DEFAULT =
            new ModeLayout(true, 1.0f, 0f, 0f, ScreenAnchor.DEFAULT, 3000L, 200L, 200L, "ct");

    /**
     * Reads a layout out of a mode's config object.
     *
     * @param body the mode's config object, or {@code null} for a mode the preset does not configure
     */
    static ModeLayout of(JsonObject body) {
        if (body == null) return DEFAULT;
        return new ModeLayout(
                flag(body, "visible", true),
                number(body, "scale", 1.0f),
                number(body, "x_offset", 0f),
                number(body, "y_offset", 0f),
                // The anchor key is `screen_anchor` in some modes and absent in others; the default is the
                // screen centre, which is where every mode that omits it is authored to appear.
                string(body, "screen_anchor", ScreenAnchor.DEFAULT),
                // Durations are authored in seconds with decimals (`3.3`, `4.5`), so they are read as a float
                // and scaled, not parsed as a long.
                Math.round(number(body, "display_duration", 3.0f) * 1000f),
                Math.round(number(body, "animation_duration", 0.2f) * 1000f),
                Math.round(number(body, "fade_out_duration", 0.2f) * 1000f),
                string(body, "team", "ct"));
    }

    /** The layout with a different anchor, for a mode that reads its anchor from elsewhere. */
    ModeLayout withAnchor(String newAnchor) {
        return new ModeLayout(visible, scale, xOffset, yOffset,
                ScreenAnchor.orDefault(newAnchor), displayDurationMs, animationDurationMs,
                fadeOutDurationMs, team);
    }

    // ---- typed reads that tolerate the pack's actual JSON -----------------------------------------

    /**
     * A float from a number or a numeric string.
     *
     * <p>{@code getAsFloat()} already accepts both, but it throws on an object or an array; the pack has been
     * seen to write a string where a number belongs, so a failure falls back rather than aborting the preset.
     */
    static float number(JsonObject body, String key, float fallback) {
        if (body == null || key == null || !body.has(key)) return fallback;
        JsonElement value = body.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isNumber()) return value.getAsFloat();
                if (value.getAsJsonPrimitive().isString()) return Float.parseFloat(value.getAsString());
                if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean() ? 1f : 0f;
            }
        } catch (RuntimeException ex) {
            // A malformed number is a preset defect, not a reason to drop the look.
            return fallback;
        }
        return fallback;
    }

    static boolean flag(JsonObject body, String key, boolean fallback) {
        if (body == null || key == null || !body.has(key)) return fallback;
        JsonElement value = body.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
                // `"true"` and `1` both appear in configs translated by hand.
                if (value.getAsJsonPrimitive().isNumber()) return value.getAsFloat() != 0f;
                if (value.getAsJsonPrimitive().isString()) return Boolean.parseBoolean(value.getAsString());
            }
        } catch (RuntimeException ex) {
            return fallback;
        }
        return fallback;
    }

    static String string(JsonObject body, String key, String fallback) {
        if (body == null || key == null || !body.has(key)) return fallback;
        JsonElement value = body.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsString();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /**
     * A colour from {@code "#RRGGBB"}, or {@code fallback} when unparseable.
     *
     * <p>The pack writes colours as hex strings. Alpha is added separately by the renderer, so this returns the
     * RGB triple only; a colour with an alpha channel would otherwise silently double-apply the fade.
     */
    static int color(JsonObject body, String key, int fallback) {
        String text = string(body, key, null);
        if (text == null) return fallback;
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() == 8) hex = hex.substring(2);   // drop a leading AA, which is applied separately
        if (hex.length() != 6) return fallback;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
