package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The five icon modes the scrolling strip does not cover: {@code card}, {@code card_bar}, {@code combo},
 * {@code valorant} and {@code honor}.
 *
 * <p>Each is a different answer to "show that a kill happened", taken from the pack's art:
 *
 * <ul>
 *   <li><b>card</b> — a CS2-style card that slides in and fades, one per kill, stacked up to a limit. The pack
 *       ships a {@code _ct} and a {@code _t} variant of each, so the side you are on changes the art.</li>
 *   <li><b>card_bar</b> — the thin light bar that sweeps under the card. It is a separate element because the
 *       pack treats it as one: {@code 00003} configures both, and the bar has its own width and colour.</li>
 *   <li><b>combo</b> — six escalating CF-style combo emblems, one per consecutive kill, wrapping after six. The
 *       art is numbered, so the count is the artwork rather than a number drawn over it.</li>
 *   <li><b>valorant</b> — the game's kill banner: a skin's bar and emblem, optionally with a halo ring.</li>
 *   <li><b>honor</b> — a row of honour badges, one per award, with an optional hint box naming it.</li>
 * </ul>
 *
 * <p>All five share {@link ModeLayout} for placement and timing, so this class is only the parts that differ.
 */
final class KillIconModes {
    /** Card art is authored at 512px and drawn at this fraction of the pack's own scale. */
    private static final float CARD_BASE = 96.0f;
    /** Combo art is 1080px; the emblems are already sized for the screen, so they draw near 1:1. */
    private static final float COMBO_BASE = 108.0f;
    /** Valorant emblems are 132–300px, so they are drawn at their own scale rather than a shared base. */
    private static final float VALORANT_BASE = 64.0f;
    /** Honour badges are 49 small icons; this is the size the hint box is laid out around. */
    private static final float HONOR_BASE = 32.0f;

    private KillIconModes() { }

    // ---- card ------------------------------------------------------------------------------------

    /**
     * Draws the card stack.
     *
     * <p>A card is centred on its anchor and stacked upward, newest at the bottom: the eye lands on the newest
     * kill, and older cards rise out of the way. That ordering is the opposite of the scrolling strip's, which
     * is deliberate — a scrolling strip is read left-to-right as a timeline, a card stack is read like a pile.
     */
    static void drawCard(GuiGraphics g, KillIconPresets.Preset preset, List<KillIconModes.Entry> entries,
                         long now, int screenWidth, int screenHeight) {
        JsonObject body = preset == null ? null : preset.element("kill_icon/card");
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible() || entries.isEmpty()) return;

        // `max_stack_count` is how many cards the reference keeps; beyond that the oldest is dropped silently.
        int maxStack = Math.max(1, Math.round(ModeLayout.number(body, "max_stack_count", 6f)));
        boolean dynamicStyle = ModeLayout.flag(body, "dynamic_card_style", true);
        String fixedTeam = ModeLayout.string(body, "team", "ct");

        int centreX = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int centreY = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        int size = Math.max(8, Math.round(CARD_BASE * layout.scale()));

        List<KillIconModes.Entry> visible = entries.subList(Math.max(0, entries.size() - maxStack), entries.size());
        for (int i = 0; i < visible.size(); i++) {
            KillIconModes.Entry entry = visible.get(i);
            long elapsed = now - entry.bornAt();
            if (elapsed > layout.displayDurationMs()) continue;
            float alpha = fade(now, entry.bornAt(), layout);
            if (alpha <= 0.01f) continue;
            // Newest at the bottom: index 0 of the visible window is the oldest and sits highest.
            int top = centreY - (visible.size() - 1 - i) * (size + 4);
            String team = dynamicStyle ? entry.teamOrDefault(fixedTeam) : fixedTeam;
            ResourceLocation texture = cardTexture(preset, entry.kind(), team);
            if (texture != null) {
                g.setColor(1f, 1f, 1f, alpha);
                g.blit(texture, centreX - size / 2, top, size, size, 0f, 0f, 64, 64, 64, 64);
                g.setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    /**
     * The card texture for a kind and team.
     *
     * <p>The pack ships six card kinds ({@code default}, {@code headshot}, {@code explosion}, {@code crit},
     * {@code light}, {@code bar}) in two teams, so a kind with no card of its own falls back to
     * {@code default} rather than to nothing — a plain-kill card is the right thing to show for a kind the card
     * mode has no art for.
     */
    static ResourceLocation cardTexture(KillIconPresets.Preset preset, KillType kind, String team) {
        String side = "t".equals(team) ? "t" : "ct";
        String stem = kind == null ? "default" : kind.cardFile(side);
        // `cardFile` already falls back to `default`, but it does not know the team suffix, so try the
        // specific kind first and then the plain default, both in the chosen team.
        List<String> candidates = new ArrayList<>(3);
        candidates.add("killicon_card_" + stem + "_" + side + ".png");
        if (kind != null) candidates.add("killicon_card_" + kind.cardFile("ct") + "_" + side + ".png");
        candidates.add("killicon_card_default_" + side + ".png");
        return firstPresent(candidates);
    }

    // ---- card bar --------------------------------------------------------------------------------

    /**
     * Draws the light bar that sweeps under the card stack.
     *
     * <p>It is drawn as a plain rectangle rather than as the pack's bar texture: the bar's art is a 512px square
     * gradient, and stretching it to an arbitrary width would blur it. The reference builds it from its own
     * width/height/colour settings too, so a rectangle is the faithful reading.
     */
    static void drawCardBar(GuiGraphics g, KillIconPresets.Preset preset, List<KillIconModes.Entry> entries,
                            long now, int screenWidth, int screenHeight) {
        JsonObject body = preset == null ? null : preset.element("kill_icon/card_bar");
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible() || entries.isEmpty()) return;
        if (!ModeLayout.flag(body, "show_light", true)) return;

        float width = ModeLayout.number(body, "light_width", 350f) * layout.scale();
        float height = Math.max(1f, ModeLayout.number(body, "light_height", 1f) * layout.scale());
        String fixedTeam = ModeLayout.string(body, "team", "ct");
        boolean dynamicStyle = ModeLayout.flag(body, "dynamic_card_style", true);

        // The bar fades with the newest card, not on its own clock: it is one element of a two-part animation.
        KillIconModes.Entry newest = entries.get(entries.size() - 1);
        float alpha = fade(now, newest.bornAt(), layout);
        if (alpha <= 0.01f) return;
        String team = dynamicStyle ? newest.teamOrDefault(fixedTeam) : fixedTeam;
        int colour = ModeLayout.color(body, "t".equals(team) ? "color_light_t" : "color_light_ct", 0x9cc1eb);

        // The bar takes its own anchor, which is why it is a separate element: it sits below the cards.
        int centreX = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int centreY = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        int w = Math.max(1, Math.round(width));
        int h = Math.max(1, Math.round(height));
        g.fill(centreX - w / 2, centreY, centreX + w / 2, centreY + h, argb(colour, alpha));
    }

    // ---- combo -----------------------------------------------------------------------------------

    /**
     * Draws the combo emblem for the current streak.
     *
     * <p>The art is six numbered emblems, so the streak length <em>is</em> the picture: 1–6 select a file and 7
     * wraps back to 1. The reference wraps rather than staying on six, because the sixth emblem is the
     * "six or more" one and holding it would stop the feedback acknowledging further kills.
     */
    static void drawCombo(GuiGraphics g, KillIconPresets.Preset preset, List<KillIconModes.Entry> entries,
                          long now, int screenWidth, int screenHeight) {
        JsonObject body = preset == null ? null : preset.element("kill_icon/combo");
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible() || entries.isEmpty()) return;

        // The streak is the number of kills inside one display window; a gap longer than that starts over.
        KillIconModes.Entry newest = entries.get(entries.size() - 1);
        if (now - newest.bornAt() > layout.displayDurationMs()) return;
        int streak = 0;
        for (KillIconModes.Entry entry : entries) {
            if (newest.bornAt() - entry.bornAt() <= layout.displayDurationMs()) streak++;
        }
        float alpha = fade(now, newest.bornAt(), layout);
        if (alpha <= 0.01f) return;

        int index = ((streak - 1) % 6 + 6) % 6;
        ResourceLocation texture = firstPresent(List.of("killicon_combo_" + (index + 1) + ".png"));
        if (texture == null) return;
        int size = Math.max(8, Math.round(COMBO_BASE * layout.scale()));
        int centreX = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int centreY = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(texture, centreX - size / 2, centreY - size / 2, size, size, 0f, 0f, 64, 64, 64, 64);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ---- valorant --------------------------------------------------------------------------------

    /**
     * Draws the Valorant kill banner: the skin's emblem, its bar, and optionally a halo ring.
     *
     * <p>The finish comes from the player's {@code valorantFinish} preference rather than from the preset, because
     * the 26 Valorant presets exist to pick a skin and 10 of them are genuinely distinct — the other 16 are
     * colourways of those. Selecting a colourway by preset would work too, but the finish setting is what the
     * previous build exposed and what the preference file stores, so both routes agree.
     */
    static void drawValorant(GuiGraphics g, KillIconPresets.Preset preset, String finish,
                             List<KillIconModes.Entry> entries, long now, int screenWidth, int screenHeight) {
        JsonObject body = preset == null ? null : preset.element("kill_icon/valorant");
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible() || entries.isEmpty()) return;

        KillIconModes.Entry newest = entries.get(entries.size() - 1);
        float alpha = fade(now, newest.bornAt(), layout);
        if (alpha <= 0.01f) return;

        int centreX = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int centreY = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);
        int size = Math.max(8, Math.round(VALORANT_BASE * layout.scale()));

        // Halo ring: a filled circle behind the emblem, so the banner reads at a distance.
        if (ModeLayout.flag(body, "enable_halo_ring", true)) {
            int radius = Math.round(ModeLayout.number(body, "halo_ring_radius", 30f) * layout.scale());
            int colour = ModeLayout.color(body, "color_halo_ring", 0xffffff);
            ring(g, centreX, centreY, radius,
                    Math.max(1, Math.round(ModeLayout.number(body, "halo_ring_width", 1.5f))),
                    argb(colour, alpha * 0.7f));
        }

        ResourceLocation emblem = valorantTexture(finish, "emblem", newest.kind());
        if (emblem != null) {
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(emblem, centreX - size / 2, centreY - size / 2, size, size, 0f, 0f, 64, 64, 64, 64);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * A Valorant texture for a finish and part, walking down to the shared art.
     *
     * <p>The finishes are unevenly supplied: {@code prime} has an emblem and a frame but no bar, {@code gaia} has
     * all three, and the colourways only recolour the bar and emblem. So the lookup tries the finish's part, then
     * the finish's emblem, then the pack's shared {@code headshot} emblem — which always exists.
     */
    static ResourceLocation valorantTexture(String finish, String part, KillType kind) {
        String skin = finish == null || finish.isBlank() ? "base" : finish;
        List<String> candidates = new ArrayList<>(4);
        candidates.add("killicon_valorant_" + skin + "_" + part + ".png");
        // A colourway only recolours some parts; the base skin supplies the rest.
        String base = skin.replaceAll("_?v\\d$", "");
        if (!base.equals(skin)) candidates.add("killicon_valorant_" + base + "_" + part + ".png");
        candidates.add("killicon_valorant_" + skin + "_emblem.png");
        candidates.add("killicon_valorant_" + kinEmblem(kind) + ".png");
        return firstPresent(candidates);
    }

    /** The pack's shared emblem for a kind: it ships one headshot emblem and one generic icon. */
    private static String kinEmblem(KillType kind) {
        return kind != null && "HEADSHOT".equals(kind.id()) ? "headshot" : "icon";
    }

    // ---- honor -----------------------------------------------------------------------------------

    /**
     * Draws the honour badge row.
     *
     * <p>Badges are keyed by honour <em>title</em>, not by scoring award id — see {@link KillType#honourFile} for
     * why those are different vocabularies. A row is laid out like the scrolling strip (so {@code direction}
     * means the same thing) but each badge carries a hint box with the title's name, because a badge on its own
     * says "something good" and not what.
     */
    static void drawHonor(GuiGraphics g, KillIconPresets.Preset preset, Minecraft mc,
                          List<KillIconModes.Entry> entries, long now, int screenWidth, int screenHeight) {
        JsonObject body = preset == null ? null : preset.element("kill_icon/honor");
        ModeLayout layout = ModeLayout.of(body);
        if (!layout.visible() || entries.isEmpty()) return;

        // `direction` is a string in the pack, and "right" means the row grows to the left -- the same sign
        // convention the scrolling mode uses, so the helper is shared rather than re-derived.
        int direction = "right".equals(ModeLayout.string(body, "direction", "right")) ? -1 : 1;
        boolean hintBox = ModeLayout.flag(body, "hint_box_enabled", true);
        float badge = HONOR_BASE * layout.scale();
        float spacing = badge * ModeLayout.number(body, "hint_box_text_gap", 8f) / 8f;

        int centreX = ScreenAnchor.centerX(layout.anchor(), Math.round(layout.xOffset()), screenWidth);
        int centreY = ScreenAnchor.centerY(layout.anchor(), Math.round(layout.yOffset()), screenHeight);

        int shown = 0;
        for (int i = entries.size() - 1; i >= 0 && shown < 8; i--, shown++) {
            KillIconModes.Entry entry = entries.get(i);
            float alpha = fade(now, entry.bornAt(), layout);
            if (alpha <= 0.01f) continue;
            String badgeFile = KillType.honourFile(entry.badge());
            if (badgeFile == null) continue;
            ResourceLocation texture = firstPresent(List.of(badgeFile.substring("honor/".length())));
            if (texture == null) continue;
            // Newest at the anchor, older badges stepping away in `direction`.
            float x = centreX + direction * shown * (badge + spacing);
            int left = Math.round(x - badge / 2);
            int top = Math.round(centreY - badge / 2);
            int box = Math.max(6, Math.round(badge));
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(texture, left, top, box, box, 0f, 0f, 64, 64, 64, 64);
            g.setColor(1f, 1f, 1f, 1f);

            if (hintBox && mc != null && mc.font != null) {
                String label = Honours.name(entry.badge());
                if (label != null && !label.isBlank()) {
                    int colour = argb(0xffffff, alpha *
                            Math.min(1f, ModeLayout.number(body, "hint_box_max_alpha", 20f) / 20f + 0.4f));
                    Ui.textCentered(g, mc.font, left + box / 2, top + box + 2, box + 40, label, colour);
                }
            }
        }
    }

    // ---- shared ----------------------------------------------------------------------------------

    /**
     * One queued icon, as the modes need it.
     *
     * @param kind    what happened, for the art lookup
     * @param badge   the honour title, for the honour row; {@code null} when not an honour
     * @param team    {@code ct} or {@code t}, for the card modes
     * @param bornAt  when it was triggered
     */
    record Entry(KillType kind, String badge, String team, long bornAt) {
        String teamOrDefault(String fallback) {
            return team == null ? fallback : team;
        }
    }

    /**
     * The fade curve shared by the modes: hold, then fade out over {@code fade_out_duration}.
     *
     * <p>Unlike the scrolling strip this has no blink segment and no entrance animation of its own — each mode
     * animates its geometry separately, and stacking a second curve on top would make the two fight.
     */
    static float fade(long now, long bornAt, ModeLayout layout) {
        long elapsed = now - bornAt;
        if (elapsed < 0) return 0f;
        long hold = Math.max(0L, layout.displayDurationMs() - layout.fadeOutDurationMs());
        if (elapsed <= hold) return 1f;
        long fadeMs = Math.max(1L, layout.fadeOutDurationMs());
        float remaining = 1f - (elapsed - hold) / (float) fadeMs;
        return Math.max(0f, Math.min(1f, remaining));
    }

    /** The first candidate that exists as a texture, or {@code null}. */
    static ResourceLocation firstPresent(List<String> files) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return null;
        for (String file : files) {
            if (file == null) continue;
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "icons/" + file);
            if (mc.getResourceManager().getResource(location).isPresent()) return location;
        }
        return null;
    }

    /** A ring outline, drawn as four arcs of one-pixel lines so it needs no texture. */
    static void ring(GuiGraphics g, int cx, int cy, int radius, int thickness, int colour) {
        for (int i = 0; i < 360; i += 4) {
            double radians = Math.toRadians(i);
            int x = cx + (int) Math.round(Math.cos(radians) * radius);
            int y = cy + (int) Math.round(Math.sin(radians) * radius);
            g.fill(x - thickness / 2, y - thickness / 2,
                    x - thickness / 2 + Math.max(1, thickness), y - thickness / 2 + Math.max(1, thickness),
                    colour);
        }
    }

    static int argb(int rgb, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (rgb & 0xffffff);
    }
}
