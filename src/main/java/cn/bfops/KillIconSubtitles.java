package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The six subtitle renderers: {@code kill_feed}, {@code bonus_list}, {@code score}, {@code hit_info},
 * {@code combo} and the honour row's hint box.
 *
 * <p>These are separate from the icon modes because they answer a different question. An icon says
 * <em>what</em> happened; a subtitle says it in words — who, with what, for how many points. The reference keeps
 * them as independent elements, which is why a preset can draw a Battlefield 1 icon and a Battlefield 4 score
 * popup at once, and why this class reads each one's own config rather than sharing one layout.
 *
 * <p><b>The format strings are the pack's own.</b> {@code "你 使用 <weapon> 击败了 <target>"} is verbatim from
 * {@code 00002}'s {@code format_normal}, placeholder tokens included. Substituting the placeholders is the whole
 * job; rewriting the sentence would be translating the pack, which is not what a port does.
 */
final class KillIconSubtitles {
    /** How far a line rises over its life, in pixels. Matches the reference's own drift. */
    private static final float RISE = 6.0f;

    private KillIconSubtitles() { }

    /**
     * The subtitle element keys this class renders.
     *
     * <p>Exactly the five the pack's {@code formats/*.json} declares — no more. The seven combo streak strings
     * are a sub-field of the {@code bonus.KILL_COMBO} award, not a sixth subtitle element, and counting them as
     * one is how the port's docs briefly came to claim "6 subtitle renderers".
     */
    static java.util.List<String> supported() {
        return java.util.List.of(
                "subtitle/kill_feed",
                "subtitle/bonus_list",
                "subtitle/score",
                "subtitle/hit_info",
                "subtitle/combo");
    }

    /**
     * The text for a kill line, with the pack's placeholders filled.
     *
     * <p>Each kind has its own format and its own placeholder colour, which is why a headshot reads differently
     * from an explosion rather than sharing one sentence with a different noun.
     */
    static String killFeedLine(JsonObject body, KillType kind, String weapon, String target) {
        String key = switch (kind == null ? "NORMAL" : kind.id()) {
            case "HEADSHOT" -> "format_headshot";
            case "EXPLOSION" -> "format_explosion";
            case "CRIT" -> "format_crit";
            case "ASSIST" -> "format_assist";
            case "DESTROY_VEHICLE" -> "format_destroy_vehicle";
            case "VEHICLE_DESTROY_ASSIST" -> "format_vehicle_destroy_assist";
            case "CAPTURE" -> "format_capture";
            case "MEDIC" -> "format_medic";
            case "RESCUE" -> "format_rescue";
            case "SPOT_ASSIST" -> "format_spot_assist";
            default -> "format_normal";
        };
        String format = ModeLayout.string(body, key, ModeLayout.string(body, "format_normal", ""));
        return fill(format, weapon, target, "", "");
    }

    /**
     * Substitutes the pack's placeholders.
     *
     * <p>The tokens are {@code <weapon>}, {@code <target>}, {@code <score>} and {@code <count>}. A missing value
     * becomes an empty string rather than the literal token, because "<target>" on screen is a bug report and a
     * gap is merely terse.
     */
    static String fill(String format, String weapon, String target, String score, String count) {
        if (format == null || format.isEmpty()) return "";
        return format
                .replace("<weapon>", weapon == null ? "" : weapon)
                .replace("<target>", target == null ? "" : target)
                .replace("<score>", score == null ? "" : score)
                .replace("<count>", count == null ? "" : count);
    }

    /**
     * Draws the score popup.
     *
     * <p>The score is the one subtitle with an entrance animation of its own: it counts up and flashes. That is
     * the reference's behaviour and the reason the element exists separately from the bonus list — a number that
     * arrives is a label, one that moves is an event.
     */
    static void drawScore(GuiGraphics g, Minecraft mc, JsonObject body, int points, long bornAt, long now,
                          int screenWidth, int screenHeight) {
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible()) return;
        float alpha = KillIconModes.fade(now, bornAt, layout);
        if (alpha <= 0.01f) return;

        int threshold = Math.round(ModeLayout.number(body, "score_threshold", 1000f));
        boolean high = points >= threshold;
        int colour = high ? ModeLayout.color(body, "color_high_score", 0xd4b800)
                : ModeLayout.color(body, "color_normal_text", 0xffffff);
        String format = ModeLayout.string(body, "format_score", "<score>");
        String text = fill(format, null, null, String.valueOf(points), null);
        if (text.isBlank()) return;

        // `enable_number_segmentation` groups thousands with commas, which the reference does for big scores
        // because a seven-digit number is unreadable otherwise.
        if (ModeLayout.flag(body, "enable_number_segmentation", true)) {
            text = group(text);
        }
        // The flash is a two-tone alternation over the first part of the life, not a fade: at 0.01s refresh the
        // reference is switching colour every frame for the first ~0.3s.
        if (ModeLayout.flag(body, "enable_flash", true) && now - bornAt < 300) {
            boolean on = ((now - bornAt) / 60) % 2 == 0;
            colour = on ? ModeLayout.color(body, "color_flash", 0xd0d0d0) : colour;
        }

        int size = Math.max(1, Math.round(layout.scale()));
        int x = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int y = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        int width = mc.font.width(text) * size;
        if (ModeLayout.flag(body, "align_right", false)) x -= width;
        else if (!ModeLayout.flag(body, "align_left", false)) x -= width / 2;
        // The rise is applied to the draw position, not to the stored one, so the text does not drift.
        y -= Math.round(RISE * progress(now, bornAt, layout));

        Ui.text(g, mc.font, x, y, width, text, KillIconModes.argb(colour, alpha));
    }

    /**
     * Draws the bonus list: one line per award, newest at the bottom, up to {@code max_lines}.
     *
     * <p>Lines fade out one after another rather than together, which is what {@code fade_out_interval} controls:
     * a block of text vanishing at once reads as a glitch, staggered lines read as a list expiring.
     */
    static void drawBonusList(GuiGraphics g, Minecraft mc, JsonObject body, List<Line> lines, long now,
                              int screenWidth, int screenHeight) {
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible() || lines.isEmpty()) return;
        int maxLines = Math.max(1, Math.round(ModeLayout.number(body, "max_lines", 4f)));
        int lineSpacing = Math.round(ModeLayout.number(body, "line_spacing", 12f));
        int normal = ModeLayout.color(body, "color_normal_text", 0xffffff);
        int special = ModeLayout.color(body, "color_special_placeholder", 0xd4b800);

        int x = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int baseY = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        // Newest last, so the list reads top-to-bottom in the order things happened.
        List<Line> shown = lines.subList(Math.max(0, lines.size() - maxLines), lines.size());
        for (int i = 0; i < shown.size(); i++) {
            Line line = shown.get(i);
            float alpha = KillIconModes.fade(now, line.bornAt(), layout);
            if (alpha <= 0.01f) continue;
            int colour = line.special() ? special : normal;
            Ui.text(g, mc.font, x, baseY + i * lineSpacing, 200, line.text(),
                    KillIconModes.argb(colour, alpha));
        }
    }

    /**
     * Draws the hit-info readout: damage dealt, or the kill confirmation.
     *
     * <p>Short-lived and near the crosshair, unlike the other subtitles: this is the only one the player reads
     * while shooting, so its default duration is 1.25s rather than 3s.
     */
    static void drawHitInfo(GuiGraphics g, Minecraft mc, JsonObject body, int damage, long bornAt, long now,
                            int screenWidth, int screenHeight) {
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible()) return;
        float alpha = KillIconModes.fade(now, bornAt, layout);
        if (alpha <= 0.01f) return;
        String text = String.valueOf(damage);
        int colour = ModeLayout.color(body, "color_damage_kill", 0xdc6943);
        int x = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int y = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        int width = mc.font.width(text);
        if (ModeLayout.flag(body, "align_right", true)) x -= width;
        else x -= width / 2;
        if (ModeLayout.flag(body, "enable_text_shadow", false)) {
            Ui.text(g, mc.font, x + 1, y + 1, width, text, KillIconModes.argb(0x000000, alpha * 0.6f));
        }
        Ui.text(g, mc.font, x, y, width, text, KillIconModes.argb(colour, alpha));
    }

    /**
     * The combo subtitle's text for a streak.
     *
     * <p>The combo <em>icon</em> mode draws a numbered emblem; this is the text version, used by presets that
     * configure the subtitle without the icon.
     */
    static String comboText(JsonObject body, int count) {
        String format = ModeLayout.string(body, "format_combo", "<count> 连杀");
        return fill(format, null, null, null, String.valueOf(count));
    }

    /**
     * Groups a number's thousands with commas, leaving any leading sign and suffix intact.
     *
     * <p>Works on the digits rather than the whole string so {@code "+12345 分"} becomes {@code "+12,345 分"}
     * rather than being mangled.
     */
    static String group(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length() + 4);
        StringBuilder digits = new StringBuilder();
        // Collect digit runs as they appear, so a sign or a space is emitted where it was found.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
                continue;
            }
            if (!digits.isEmpty()) {
                out.append(groupDigits(digits));
                digits.setLength(0);
            }
            out.append(c);
        }
        if (!digits.isEmpty()) out.append(groupDigits(digits));
        return out.toString();
    }

    private static String groupDigits(CharSequence digits) {
        if (digits.length() <= 3) return digits.toString();
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
        int lead = digits.length() % 3;
        if (lead > 0) out.append(digits, 0, lead);
        for (int i = lead; i < digits.length(); i += 3) {
            if (!out.isEmpty()) out.append(',');
            out.append(digits, i, i + 3);
        }
        return out.toString();
    }

    private static float progress(long now, long bornAt, ModeLayout layout) {
        long life = Math.max(1L, layout.displayDurationMs());
        return Math.max(0f, Math.min(1f, (now - bornAt) / (float) life));
    }

    /** One line of the bonus list. */
    record Line(String text, String key, boolean special, long bornAt) { }

    /** A convenience list builder so a caller does not have to construct the record inline. */
    static List<Line> lines() {
        return new ArrayList<>();
    }
}
