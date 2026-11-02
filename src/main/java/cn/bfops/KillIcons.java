package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The bridge between this mod's award names and the 656 pack's icon kinds.
 *
 * <p>This mod says {@code "headshot"}, {@code "vehicle"}, {@code "mark"}; the pack says {@code HEADSHOT},
 * {@code DESTROY_VEHICLE}, {@code SPOT_ASSIST}. Something has to translate, and it must be one place: two
 * translations is how the feed and the overlay end up disagreeing about what a kill looks like.
 *
 * <p><b>Why there is still a bitmap fallback.</b> The pack does not have art for every award this mod emits —
 * {@code mark} and {@code streak} are this mod's own additions — and asking for a missing texture draws
 * nothing at all, which reads as "the award did not happen". {@link FeedIcons} is a small hand-built glyph set
 * that predates the pack and costs nothing to keep; it fills exactly those gaps. The texture path is tried
 * first, so the pack's art always wins where it exists.
 */
final class KillIcons {
    /** Below this box size the bitmap is used: a 1024px emblem at 11px is a grey smudge, the glyph is not. */
    static final int MIN_TEXTURE_SIZE = 16;

    private KillIcons() { }

    /**
     * This mod's award names, mapped to the pack's kinds.
     *
     * <p>The left-hand names are what {@link Honours} and {@link Feeds} actually emit; the right-hand values are
     * {@link KillType} names. Several of this mod's awards collapse onto one pack kind because the pack has no
     * separate art for them, and that is the honest outcome: an aerial kill still reads as a kill.
     */
    static KillType kindFor(String award) {
        String name = award == null ? "" : award;
        return switch (name) {
            case "headshot" -> KillType.byId("HEADSHOT");
            case "crit" -> KillType.byId("CRIT");
            case "explosion" -> KillType.byId("EXPLOSION");
            case "assist" -> KillType.byId("ASSIST");
            case "capture" -> KillType.byId("CAPTURE");
            case "medic", "heal" -> KillType.byId("MEDIC");
            case "rescue" -> KillType.byId("RESCUE");
            case "vehicle", "vehicle_destroy" -> KillType.byId("DESTROY_VEHICLE");
            case "vehicle_assist" -> KillType.byId("VEHICLE_DESTROY_ASSIST");
            case "spot_assist", "mark" -> KillType.byId("SPOT_ASSIST");
            // Everything else -- a plain kill, a streak, an aerial, a revenge, this mod's own honour shapes --
            // borrows the plain-kill icon. There is no art that would be more accurate, and inventing a
            // different icon for each would make the feed noisier without telling the player anything.
            default -> KillType.byId("NORMAL");
        };
    }

    /** The award's icon, honouring the player's chosen preset. */
    static ResourceLocation textureFor(String award, String presetId) {
        KillType kind = kindFor(award);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return null;
        for (String file : kind.textureCandidates(presetId, "scrolling")) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "icons/" + file);
            if (mc.getResourceManager().getResource(location).isPresent()) return location;
        }
        return null;
    }

    /**
     * Draws one award icon, texture first and bitmap second.
     *
     * @return the width actually drawn, so the caller can lay text out beside it
     */
    static int drawAward(GuiGraphics g, String award, int x, int y, int size, double alpha) {
        int box = Math.max(1, size);
        if (box >= MIN_TEXTURE_SIZE) {
            ResourceLocation texture = textureFor(award, ClientPrefs.killIconPreset());
            if (texture != null) {
                g.setColor(1f, 1f, 1f, (float) Math.max(0, Math.min(1, alpha)));
                g.blit(texture, x, y, box, box, 0f, 0f, 64, 64, 64, 64);
                g.setColor(1f, 1f, 1f, 1f);
                return box;
            }
        }
        // Bitmap fallback: "something good happened" is still true, and the label beside it says what.
        int color = argb(tint(award), alpha);
        String glyph = bitmapKind(award);
        int scale = Math.max(1, box / Math.max(1, FeedIcons.height(glyph)));
        FeedIcons.draw(g, glyph, x, y, color, scale);
        return FeedIcons.width(glyph) * scale;
    }

    /**
     * The bitmap glyph for an award, for the small-size fallback.
     *
     * <p>The bitmap vocabulary is nine shapes, narrower than the pack's 11 kinds, so several awards project onto
     * one glyph. A vehicle kill borrows the explosion burst because there is no vehicle bitmap and a burst is
     * the closest read; this matches what the previous renderer did, and it is the honest limit of a 9px glyph.
     * Every value here must be a key {@link FeedIcons#defined} accepts — the test asserts that.
     */
    static String bitmapKind(String award) {
        String name = award == null ? "" : award;
        return switch (name) {
            case "headshot", "crit" -> "headshot";
            case "assist", "medic", "heal", "rescue" -> "assist";
            case "capture" -> "capture";
            case "vehicle", "vehicle_destroy", "vehicle_assist" -> "explosion";
            case "spot_assist", "mark" -> "mark";
            case "streak" -> "streak";
            case "revenge" -> "revenge";
            case "aerial" -> "aerial";
            default -> "kill";
        };
    }

    /** Bitmap colour for an award, used only by the fallback. A texture carries its own colour. */
    static int tint(String award) {
        String name = award == null ? "" : award;
        return switch (name) {
            case "headshot", "crit" -> 0xffff5c68;
            case "kill" -> 0xffffffff;
            default -> 0xffffd479;
        };
    }

    private static int argb(int color, double alpha) {
        return ((int) (255 * Math.max(0, Math.min(1, alpha))) << 24) | (color & 0xffffff);
    }
}
