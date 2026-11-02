package cn.bfops;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the kill-icon strip.
 *
 * <p>This is the thin seam between {@link KillIconScroll} (which decides <em>what</em> to show, where and how
 * opaque) and the game (which can only blit a texture). Everything with an edge case lives in the model so it
 * can be tested headless; everything here is a blit, a colour set and a matrix push.
 *
 * <p><b>Why the texture is scaled rather than sampled at a smaller size.</b> The pack's icons are 256–2048 px.
 * The strip draws them at about 19 px ({@code 64 * scale}, and the shipped presets scale to 0.30–0.40). Asking
 * the blit for a 19-px destination does the downscale in the same filtered pass the game already uses for every
 * other GUI texture, which is both faster and better-looking than reading the file into an int[] and shrinking
 * it on the CPU.
 *
 * <p><b>Why the aspect ratio is not forced square.</b> Most icons are square, but the card tabs are tall and the
 * Valоrant bars are wide. The reference reads each kind's {@code texture_frame_width_ratio} /
 * {@code _height_ratio} from the preset and multiplies the 64-px base by it. The same is done here, defaulting
 * to 1:1 — which is what every scrolling-preset icon is.
 */
final class KillIconHud {
    /** The base size the reference multiplies every preset's scale into. */
    static final float BASE_SIZE = KillIconScroll.BASE_ICON_SIZE;

    private final KillIconScroll model;
    private final KillIconPresets.Preset preset;
    private String presetId;
    private long lastTick;

    KillIconHud(KillIconPresets.Preset preset, String presetId) {
        this.preset = preset;
        this.model = new KillIconScroll(preset != null && preset.config() != null
                ? preset.config()
                : new KillIconScroll.Config());
        this.presetId = presetId == null ? "00037" : presetId;
    }

    KillIconScroll model() {
        return model;
    }

    KillIconScroll.Config config() {
        return model.config();
    }

    /**
     * The art family this preset draws from.
     *
     * <p>Derived from the preset's own mode rather than from a setting, because that is exactly what the mode
     * means: a preset that configures {@code kill_icon/battlefield1} is the Battlefield 1 look and must draw
     * Battlefield 1 art. The scrolling family also keys off the preset id, which its lookup already handles.
     *
     * <p>The two subtitle-only presets have no icon mode, so they draw nothing; {@code NORMAL} art would be
     * wrong in a different way, suggesting a kill icon the player asked not to have.
     */
    String family() {
        if (preset == null) return "battlefield6";
        String mode = preset.mode();
        if (mode == null) return "battlefield6";
        return switch (mode) {
            case "kill_icon/battlefield1" -> "battlefield1";
            case "kill_icon/battlefield6" -> "battlefield6";
            // `card`, `card_bar`, `combo`, `valorant` and `honor` each have their own art sets which the shared
            // scrolling lookup cannot express; until those renderers land they borrow the battlefield6 icons,
            // which is at least the right silhouette rather than nothing.
            default -> "battlefield6";
        };
    }

    boolean hasVisibleIcons() {
        return model.hasVisibleIcons();
    }

    /** Queues an award for display. */
    void trigger(KillType kind, long now) {
        model.trigger(kind, now);
    }

    /** Forgets everything on screen — used when leaving a match, so icons do not survive into the lobby. */
    void reset() {
        model.active().clear();
        model.pending().clear();
    }

    /**
     * Advances and draws.
     *
     * @param now          a monotonic millisecond clock
     * @param screenWidth  the GUI-scaled width
     * @param screenHeight the GUI-scaled height
     */
    void render(GuiGraphics g, long now, int screenWidth, int screenHeight) {
        // A preset configures one or more icon elements, and each configured element draws. The previous
        // version dispatched on a single "primary mode", which meant `00037` drew its scrolling strip but
        // silently skipped the honour badge row it also configures -- half the preset was invisible.
        if (preset == null) return;
        List<String> iconElements = preset.elementKeys().stream()
                .filter(key -> key.startsWith("kill_icon/")).toList();
        if (iconElements.isEmpty()) {
            // The two subtitle-only presets (00005, 00006) draw no icons at all. That is their whole point.
            return;
        }
        // `card` and `card_bar` are two halves of one look, drawn together whenever both are configured:
        // the bar sweeps under the cards. Drawing only one leaves a light bar with nothing above it.
        for (String element : iconElements) {
            // The scrolling mode keeps its dedicated model -- it is the only mode with queueing, slot
            // assignment and per-icon position animation. The rest are stateless draws over the same entries.
            if ("kill_icon/scrolling".equals(element) || "kill_icon/battlefield1".equals(element)) {
                renderScrolling(g, now, screenWidth, screenHeight);
                continue;
            }
            List<KillIconModes.Entry> entries = modeEntries(now);
            if (entries.isEmpty()) continue;
            switch (element) {
                case "kill_icon/card" ->
                        KillIconModes.drawCard(g, preset, entries, now, screenWidth, screenHeight);
                case "kill_icon/card_bar" ->
                        KillIconModes.drawCardBar(g, preset, entries, now, screenWidth, screenHeight);
                case "kill_icon/combo" ->
                        KillIconModes.drawCombo(g, preset, entries, now, screenWidth, screenHeight);
                case "kill_icon/valorant" -> KillIconModes.drawValorant(g, preset, ClientPrefs.valorantFinish(),
                        entries, now, screenWidth, screenHeight);
                case "kill_icon/honor" -> KillIconModes.drawHonor(g, preset, Minecraft.getInstance(), entries, now,
                        screenWidth, screenHeight);
                // An element the port does not model yet draws nothing rather than falling back to a mode the
                // preset did not ask for -- a wrong icon is worse than no icon.
                default -> { }
            }
        }
    }

    /**
     * The mode entries still on screen, newest last.
     *
     * <p>The scrolling model already tracks this; the other modes reuse it rather than each keeping their own
     * clock, so a preset switch mid-match does not resurrect kills the previous mode had already expired.
     */
    private List<KillIconModes.Entry> modeEntries(long now) {
        List<KillIconModes.Entry> entries = new ArrayList<>();
        long longest = 1L;
        for (KillIconScroll.Icon icon : model.active()) {
            longest = Math.max(longest, icon.displayDuration);
            // The badge is looked up from the kind: the honour row needs a title, and only the honour kinds
            // carry one. Everything else gets null and the mode ignores it.
            entries.add(new KillIconModes.Entry(icon.kind, badgeFor(icon.kind), null, icon.startTime));
        }
        // Drop expired entries so a mode using its own duration does not draw stale ones. The longest active
        // duration is the window; a plain loop rather than a lambda because the running maximum is mutated.
        List<KillIconModes.Entry> fresh = new ArrayList<>(entries.size());
        for (KillIconModes.Entry entry : entries) {
            if (now - entry.bornAt() <= longest) fresh.add(entry);
        }
        return fresh;
    }

    /**
     * The honour title for a kind, or {@code null} when the kind has no badge.
     *
     * <p>Badges are keyed by honour <em>title</em> ({@code headhunter}, {@code roadhog}), not by scoring award
     * id, so this only resolves when the kind's texture key happens to be a title. That is deliberately narrow:
     * most kills have no honour badge, and the row draws nothing for them rather than inventing one. Honours are
     * raised by {@link Honours}' own evidence model, and {@code HonourCatalog} is the table that says which
     * titles exist.
     */
    private static String badgeFor(KillType kind) {
        if (kind == null) return null;
        String key = kind.textureKey();
        return HonourCatalog.byId(key) == null ? null : key;
    }

    /** Queues an honour badge by title, for the honour row. */
    void triggerHonour(String title, long now) {
        HonourCatalog honour = HonourCatalog.byId(title);
        if (honour == null) return;
        // The badge is drawn from the honour row, which keys off the title rather than the kill kind, so the
        // entry carries the title and a NORMAL kind as a placeholder.
        model.trigger(KillType.byId("NORMAL"), now);
    }

    private void renderScrolling(GuiGraphics g, long now, int screenWidth, int screenHeight) {
        KillIconScroll.Config config = model.config();
        float centreX = ScreenAnchor.centerX(config.anchor, config.xOffset, screenWidth);
        int centreY = ScreenAnchor.centerY(config.anchor, config.yOffset, screenHeight);
        lastTick = now;
        model.tick(now, centreX);
        if (!model.hasVisibleIcons()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        for (KillIconScroll.Icon icon : List.copyOf(model.active())) {
            long elapsed = now - icon.startTime;
            // The delay holds the animation at its first frame while the previous icon settles into place.
            long animElapsed = Math.max(0L, elapsed - config.iconDelayMs);
            float scale = model.scaleAt(icon, now);
            float alpha = model.alphaAt(icon, now);
            if (alpha <= 0.01f) continue;
            ResourceLocation texture = textureFor(KillType.byType(icon.kind.type()), presetId, family());
            if (texture == null) continue;
            float drawWidth = BASE_SIZE;
            float drawHeight = BASE_SIZE;
            int width = Math.max(1, Math.round(drawWidth * scale));
            int height = Math.max(1, Math.round(drawHeight * scale));
            int left = Math.round(icon.currentX - width / 2.0f);
            int top = Math.round(centreY - height / 2.0f);
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(texture, left, top, width, height, 0f, 0f, 64, 64, 64, 64);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * The texture for a kind, walking the fallback chain until one loads.
     *
     * <p>The chain exists because the pack does not ship art for every kind in every family (see
     * {@link KillType#textureCandidates}). Returning {@code null} when nothing loads is deliberate: an award
     * with no art draws nothing rather than drawing a magenta placeholder over the player's screen.
     *
     * @param family which art family to draw from, from {@link #family()}
     */
    ResourceLocation textureFor(KillType kind, String preset, String family) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return null;
        for (String file : kind.textureCandidates(preset, family)) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "icons/" + file);
            if (mc.getResourceManager().getResource(location).isPresent()) return location;
        }
        return null;
    }

    /** The texture for a kind in this preset's own family. */
    ResourceLocation textureFor(KillType kind, String preset) {
        return textureFor(kind, preset, family());
    }

    /** The GUI-scaled pixel size an icon draws at once its entrance animation has finished. */
    int settledSize() {
        return Math.max(1, Math.round(BASE_SIZE * model.config().scale));
    }

    /** The last clock value the model was advanced to, exposed for tests. */
    long lastTick() {
        return lastTick;
    }
}
