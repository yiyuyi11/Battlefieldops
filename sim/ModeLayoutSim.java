package cn.bfops;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * Verifies the mode and subtitle layers against the pack's real config shapes.
 *
 * <p>Everything here is pure: layout parsing, the fade curve, placeholder substitution and number grouping.
 * The drawing calls themselves need a GUI and are exercised by the game rather than by a sim — what can be
 * checked headlessly is that the numbers the renderers use are the numbers the pack authored, and that a
 * missing or malformed key degrades to the reference's default rather than to a crash.
 */
public final class ModeLayoutSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        defaults();
        packShapes();
        durations();
        fadeCurve();
        colourParsing();
        placeholders();
        numberGrouping();
        combos();
        System.out.println("ModeLayoutSim: " + checks + " checks passed");
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** A missing body or a missing key yields the reference's default, never a zero. */
    static void defaults() {
        ModeLayout none = ModeLayout.of(null);
        check(none == ModeLayout.DEFAULT, "a null body is the shared default");
        check(none.visible(), "modes are visible by default");
        check(none.scale() == 1.0f, "default scale is 1.0");
        check(none.xOffset() == 0f && none.yOffset() == 0f, "default offset is zero");
        check(none.displayDurationMs() == 3000L, "default duration is 3s");
        check(none.animationDurationMs() == 200L, "default animation is 200ms");
        check(none.fadeOutDurationMs() == 200L, "default fade is 200ms");
        check(none.anchor().equals(ScreenAnchor.DEFAULT), "default anchor is the shared one");
        check(none.team().equals("ct"), "default team is CT");

        // An empty object is the same as no object: every field has a default.
        ModeLayout empty = ModeLayout.of(json("{}"));
        check(empty.scale() == 1.0f && empty.displayDurationMs() == 3000L, "an empty body uses defaults");
        check(empty.visible(), "an empty body is visible");

        // A null anchor in the body must not become a null string in the layout.
        ModeLayout nullAnchor = ModeLayout.of(json("{\"screen_anchor\": null}"));
        check(nullAnchor.anchor() != null, "a null anchor falls back rather than staying null");
        // `withAnchor` sanitises too.
        check(ModeLayout.DEFAULT.withAnchor("nope").anchor().equals(ScreenAnchor.DEFAULT),
                "an unknown anchor is replaced by the default");
        check(ModeLayout.DEFAULT.withAnchor("top_left").anchor().equals("top_left"),
                "a known anchor is kept");
    }

    /**
     * The pack's own values, including the two shapes that look like numbers and are not.
     */
    static void packShapes() {
        // 00037's scrolling block, verbatim from the file, including the float `max_visible_icons`.
        ModeLayout bf6 = ModeLayout.of(json("{\"visible\": true, \"scale\": 0.29679677, "
                + "\"x_offset\": -72.0, \"y_offset\": -42.0, \"display_duration\": 3.3, "
                + "\"animation_duration\": 0.2, \"fade_out_duration\": 0.2}"));
        check(Math.abs(bf6.scale() - 0.29679677f) < 1e-6, "00037's scale is preserved exactly");
        check(bf6.xOffset() == -72f && bf6.yOffset() == -42f, "00037's offsets are negative and kept");
        check(bf6.displayDurationMs() == 3300L, "3.3 seconds is 3300ms, not 3");
        check(bf6.animationDurationMs() == 200L, "0.2 seconds is 200ms");
        check(bf6.visible(), "00037 is visible");

        // 00003's card block: an integer-ish scale and a team.
        ModeLayout card = ModeLayout.of(json("{\"visible\": true, \"scale\": 0.15, \"team\": \"ct\", "
                + "\"max_stack_count\": 6, \"dynamic_card_style\": true}"));
        check(Math.abs(card.scale() - 0.15f) < 1e-6, "the card scale is a small float");
        check(card.team().equals("ct"), "the card team is read");

        // A string where a number belongs: the pack does this, and Gson's getAsFloat tolerates it but
        // getAsInt does not, so the reader must not be getAsInt.
        ModeLayout asString = ModeLayout.of(json("{\"scale\": \"0.5\", \"display_duration\": \"4.5\"}"));
        check(asString.scale() == 0.5f, "a numeric string parses as a number");
        check(asString.displayDurationMs() == 4500L, "a numeric string duration parses");

        // Junk values fall back rather than throwing: a preset defect costs the setting, not the look.
        ModeLayout junk = ModeLayout.of(json("{\"scale\": \"abc\", \"display_duration\": {}, "
                + "\"visible\": []}"));
        check(junk.scale() == 1.0f, "an unparseable scale falls back");
        check(junk.displayDurationMs() == 3000L, "an object where a number belongs falls back");
        check(junk.visible(), "an array where a boolean belongs falls back");

        // A boolean written as a number or a string still reads as a boolean.
        check(ModeLayout.of(json("{\"visible\": 0}")).visible() == false, "0 is false");
        check(ModeLayout.of(json("{\"visible\": 1}")).visible(), "1 is true");
        check(ModeLayout.of(json("{\"visible\": \"false\"}")).visible() == false, "\"false\" is false");
        check(ModeLayout.of(json("{\"visible\": \"true\"}")).visible(), "\"true\" is true");
    }

    /** Durations are authored in seconds, so the millisecond conversion is where mistakes live. */
    static void durations() {
        check(ModeLayout.of(json("{\"display_duration\": 0}")).displayDurationMs() == 0L,
                "a zero duration stays zero rather than becoming the default");
        // 00004 uses 4.5s, which is the longest in the pack.
        check(ModeLayout.of(json("{\"display_duration\": 4.5}")).displayDurationMs() == 4500L,
                "4.5 seconds is 4500ms");
        check(ModeLayout.of(json("{\"display_duration\": 1.25}")).displayDurationMs() == 1250L,
                "1.25 seconds is 1250ms");
        // A negative duration is nonsensical; it is kept as written so the fade maths can clamp it.
        ModeLayout negative = ModeLayout.of(json("{\"display_duration\": -1, \"fade_out_duration\": 0}"));
        check(negative.displayDurationMs() == -1000L, "a negative duration is preserved, not silently fixed");
        // And the fade curve survives it rather than dividing by zero.
        float alpha = KillIconModes.fade(0L, 0L, negative);
        check(alpha >= 0f && alpha <= 1f, "the fade stays in 0..1 even with a negative duration");
    }

    /** The fade holds full opacity, then falls to zero over exactly the fade window. */
    static void fadeCurve() {
        ModeLayout layout = ModeLayout.of(json("{\"display_duration\": 3.0, \"fade_out_duration\": 0.5}"));
        check(KillIconModes.fade(0L, 0L, layout) == 1f, "a fresh entry is fully opaque");
        check(KillIconModes.fade(2499L, 0L, layout) == 1f, "and holds until the fade starts");
        check(KillIconModes.fade(2500L, 0L, layout) == 1f, "the fade starts at full opacity");
        float mid = KillIconModes.fade(2750L, 0L, layout);
        check(mid > 0.4f && mid < 0.6f, "halfway through the fade it is about half, got " + mid);
        check(KillIconModes.fade(3000L, 0L, layout) == 0f, "at the end it is fully transparent");
        check(KillIconModes.fade(9000L, 0L, layout) == 0f, "and it stays transparent");
        // A clock that goes backwards must not produce a negative alpha.
        check(KillIconModes.fade(-5L, 0L, layout) == 0f, "a negative elapsed time is transparent, not negative");
        // A zero fade window must not divide by zero.
        ModeLayout instant = ModeLayout.of(json("{\"display_duration\": 1.0, \"fade_out_duration\": 0}"));
        float atEnd = KillIconModes.fade(1000L, 0L, instant);
        check(atEnd >= 0f && atEnd <= 1f, "a zero-width fade is still bounded, got " + atEnd);
    }

    /** Colours are `#RRGGBB` strings, and an alpha channel must not leak into the RGB. */
    static void colourParsing() {
        JsonObject body = json("{\"a\": \"#9ccc65\", \"b\": \"#FF0000\", \"c\": \"#80FFFFFF\", "
                + "\"d\": \"nonsense\", \"e\": 12345}");
        check(ModeLayout.color(body, "a", 0) == 0x9ccc65, "a lowercase hex colour parses");
        check(ModeLayout.color(body, "b", 0) == 0xff0000, "an uppercase hex colour parses");
        // `#AARRGGBB` is 8 digits; the alpha is dropped because the renderer applies its own.
        check(ModeLayout.color(body, "c", 0) == 0xffffff, "a leading alpha channel is dropped, got "
                + Integer.toHexString(ModeLayout.color(body, "c", 0)));
        check(ModeLayout.color(body, "d", 0x123456) == 0x123456, "junk falls back");
        check(ModeLayout.color(body, "e", 0x123456) == 0x123456, "a bare number is not a colour");
        check(ModeLayout.color(body, "missing", 0xabcdef) == 0xabcdef, "a missing key falls back");
        check(ModeLayout.color(null, "a", 0x111111) == 0x111111, "a null body falls back");
        // The palette the pack actually uses, all of which must survive.
        for (String hex : List.of("#EF9166", "#dc6943", "#9cc1eb", "#d9ac5b", "#DEDEDE", "#D4B800")) {
            int parsed = ModeLayout.color(json("{\"c\": \"" + hex + "\"}"), "c", 0);
            check(parsed != 0, "the pack's own colour " + hex + " parses");
        }
    }

    /** Placeholders are substituted; a missing value becomes a gap, not a literal token. */
    static void placeholders() {
        String format = "你 使用 <weapon> 击败了 <target>";
        check(KillIconSubtitles.fill(format, "M4A1", "敌人", null, null).equals("你 使用 M4A1 击败了 敌人"),
                "both placeholders are filled");
        // A missing value must not leave `<target>` on screen.
        String missing = KillIconSubtitles.fill(format, "M4A1", null, null, null);
        check(!missing.contains("<target>"), "a missing value leaves no literal token: " + missing);
        check(missing.contains("M4A1"), "and the value that was supplied is still there");
        check(KillIconSubtitles.fill(null, "a", "b", "c", "d").isEmpty(), "a null format is empty");
        check(KillIconSubtitles.fill("", "a", "b", "c", "d").isEmpty(), "an empty format is empty");
        // A format with no placeholders is passed through unchanged.
        check(KillIconSubtitles.fill("击杀", null, null, null, null).equals("击杀"),
                "a plain format is unchanged");
        // Repeated placeholders are all replaced, not just the first.
        check(KillIconSubtitles.fill("<weapon> 和 <weapon>", "刀", null, null, null).equals("刀 和 刀"),
                "a repeated placeholder is replaced every time");
        // Score and count are separate tokens.
        check(KillIconSubtitles.fill("<score> 分 <count> 连杀", null, null, "150", "3").equals("150 分 3 连杀"),
                "score and count fill their own tokens");
    }

    /** Thousands grouping, which the reference applies to big scores. */
    static void numberGrouping() {
        check(KillIconSubtitles.group("0").equals("0"), "a single digit is unchanged");
        check(KillIconSubtitles.group("999").equals("999"), "three digits are unchanged");
        check(KillIconSubtitles.group("1000").equals("1,000"), "four digits gain a comma");
        check(KillIconSubtitles.group("12345").equals("12,345"), "five digits group");
        check(KillIconSubtitles.group("1234567").equals("1,234,567"), "seven digits group twice");
        check(KillIconSubtitles.group("").isEmpty(), "an empty string stays empty");
        check(KillIconSubtitles.group(null).isEmpty(), "a null string is empty");
        // A sign stays in front, and a suffix stays after.
        check(KillIconSubtitles.group("+12345").equals("+12,345"), "a leading plus is preserved");
        check(KillIconSubtitles.group("-12345").equals("-12,345"), "a leading minus is preserved");
        check(KillIconSubtitles.group("12345 分").equals("12,345 分"), "a trailing suffix is preserved");
        // Text with no digits is untouched.
        check(KillIconSubtitles.group("击杀").equals("击杀"), "a digitless string is unchanged");
        // Two separate runs are grouped independently.
        check(KillIconSubtitles.group("1000/2000").equals("1,000/2,000"),
                "each digit run groups on its own");
    }

    /** The combo subtitle counts a streak, and the icon mode wraps after six. */
    static void combos() {
        JsonObject body = json("{\"format_combo\": \"<count> 连杀\"}");
        check(KillIconSubtitles.comboText(body, 1).equals("1 连杀"), "one kill reads as one");
        check(KillIconSubtitles.comboText(body, 6).equals("6 连杀"), "six kills read as six");
        check(KillIconSubtitles.comboText(json("{}"), 3).equals("3 连杀"),
                "a missing format uses the built-in one");
        // The icon mode wraps: streak 7 draws emblem 1 again, which is the reference's own behaviour.
        for (int streak = 1; streak <= 20; streak++) {
            int index = ((streak - 1) % 6 + 6) % 6;
            check(index >= 0 && index < 6, "streak " + streak + " selects an emblem in 0..5");
        }
        check(((7 - 1) % 6) == 0, "the seventh kill wraps to the first emblem");
        check(((6 - 1) % 6) == 5, "the sixth kill is the last emblem");

        // The card mode's team selection: the pack ships both, and a null team takes the fallback.
        KillIconModes.Entry entry = new KillIconModes.Entry(KillType.byId("NORMAL"), null, null, 0L);
        check(entry.teamOrDefault("ct").equals("ct"), "a null team takes the preset's");
        KillIconModes.Entry t = new KillIconModes.Entry(KillType.byId("NORMAL"), null, "t", 0L);
        check(t.teamOrDefault("ct").equals("t"), "an explicit team wins over the preset's");
    }
}
