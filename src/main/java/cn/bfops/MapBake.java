package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The static half of the tactical map, baked into one texture.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link Minimap} drew every line as a run of one-pixel {@code fill} calls: a boundary of a few
 * hundred pixels was a few hundred fills, and each capture point's range ring was another forty segments
 * on top. None of that content changes while the player walks — the boundary, the sectors, the point
 * markers and their rings are all properties of the arena, not of the frame — yet all of it was being
 * rebuilt from scratch sixty-plus times a second.
 *
 * <p>So it is drawn once into an off-screen texture and then blitted. The saving is not a micro
 * optimisation: a typical arena was on the order of a thousand fill calls per frame, and the baked path
 * replaces them with a single {@code blit}. What is left per frame is only the genuinely dynamic content
 * — teammates, vehicles, marks, the view cone and the player's own arrow.
 *
 * <h2>When it is rebuilt</h2>
 *
 * <p>Only when something the bake actually depends on changes:
 *
 * <ul>
 *   <li>the map's world-to-screen transform (centre or span),</li>
 *   <li>the panel's pixel size,</li>
 *   <li>which arena or dimension is shown,</li>
 *   <li>battle-area geometry changes, including edits that keep the same vertex count,</li>
 *   <li>which points are active in the current stage.</li>
 * </ul>
 *
 * <p>The centre comparison uses a threshold rather than equality. An exact compare against a smoothed,
 * continuously-moving centre would rebuild on every single frame — the texture would be re-rasterised
 * just as often as before, only with more machinery. A movement threshold is what makes the smoothing in
 * {@link MapFollow} free to be smooth.
 */
final class MapBake implements AutoCloseable {
    /** Registry-name counter, so two maps on screen at once cannot register over each other. */
    private static int counter;

    /**
     * Grid step of the baked texture, in pixels.
     *
     * <p>Baked at half the panel's resolution and scaled up by the blit. The static layer is flat colours
     * and one-pixel lines, so at full resolution it would be four times the texture memory for a
     * difference nobody can see behind the markers; at half it is still sharper than the lines it
     * replaces, because those were snapped to whole pixels anyway.
     */
    private static final int DOWNSAMPLE = 2;

    /** Half a pixel of the baked texture, in blocks, is the rebuild threshold's natural unit. */
    private static final double CENTRE_EPSILON_BLOCKS = 0.5;

    private final String name = "bfops_mapbake_" + (++counter);
    private DynamicTexture texture;
    private ResourceLocation location;
    private int tw, th;

    // Everything the current bake depends on. Compared field by field in #stale.
    private double cx = Double.NaN, cz;
    private double span;
    private int panelW = -1, panelH = -1;
    private String dimension = "";
    private String arenaId = "";
    private int stage = Integer.MIN_VALUE;
    private int team = Integer.MIN_VALUE;
    private int signature;

    /**
     * A cheap value that changes whenever anything drawn into the bake changes.
     *
     * <p>Hash actual polygon coordinates, not list identities or counts. Ownership and progress are
     * rendered live and must not invalidate the static layer on every server tick.
     */
    private static int signatureOf(Model.Arena arena, JsonObject state, int stage, int team) {
        int hash = 17;
        if (arena != null) {
            hash = 31*hash + java.util.Objects.hashCode(arena.id);
            hash = 31*hash + java.util.Objects.hashCode(arena.dimension);
            hash = 31*hash + java.util.Objects.hashCode(arena.mode);
            if(arena.boundary!=null)for(var v:arena.boundary)hash=31*hash+v.hashCode();
            if(arena.zones!=null)for(var zone:arena.zones){
                if(zone==null)continue;
                hash=31*hash+zone.team;
                hash=31*hash+zone.boundary.size();
                for(var v:zone.boundary)hash=31*hash+v.hashCode();
            }
            if(arena.sectors!=null)for(var sector:arena.sectors) {
                hash=31*hash+sector.boundary.size();
                for(var v:sector.boundary)hash=31*hash+v.hashCode();
            }
        }
        hash=31*hash+stage;
        hash=31*hash+team;
        return hash;
    }

    private boolean stale(double centreX, double centreZ, double newSpan, int w, int h,
                          String dim, Model.Arena arena, JsonObject state, int newStage, int newTeam) {
        if (texture == null || tw != w || th != h) return true;
        if (panelW != w || panelH != h) return true;
        if (Math.abs(centreX - cx) > CENTRE_EPSILON_BLOCKS) return true;
        if (Math.abs(centreZ - cz) > CENTRE_EPSILON_BLOCKS) return true;
        if (newSpan != span) return true;
        if (!dim.equals(dimension)) return true;
        String id = arena == null || arena.id == null ? "" : arena.id;
        if (!id.equals(arenaId)) return true;
        if (newStage != stage || newTeam != team) return true;
        return signatureOf(arena, state, newStage, newTeam) != signature;
    }

    /**
     * Ensures the texture matches the requested view, rebuilding it when it does not.
     *
     * @return the texture to blit, or {@code null} when there is nothing to draw yet
     */
    ResourceLocation ensure(Model.Arena arena, JsonObject state, double centreX, double centreZ,
                            double newSpan, int w, int h, String dim, int newStage, int newTeam) {
        if (w <= 0 || h <= 0) return null;
        int btw = Math.max(1, w / DOWNSAMPLE);
        int bth = Math.max(1, h / DOWNSAMPLE);
        if (stale(centreX, centreZ, newSpan, btw, bth, dim, arena, state, newStage, newTeam)) {
            if (texture == null || tw != btw || th != bth) {
                close();
                tw = btw;
                th = bth;
                texture = new DynamicTexture(tw, th, false);
                // Nearest filtering: the static layer is flat colour and single-pixel rules, so smoothing
                // it on the upscale would blur exactly the boundary lines that carry the information.
                texture.setFilter(false, false);
                location = Minecraft.getInstance().getTextureManager().register(name, texture);
            }
            bake(arena, state, centreX, centreZ, newSpan, dim, newStage);
            cx = centreX;
            cz = centreZ;
            span = newSpan;
            panelW = btw;
            panelH = bth;
            dimension = dim;
            arenaId = arena == null || arena.id == null ? "" : arena.id;
            stage = newStage;
            team = newTeam;
            signature = signatureOf(arena, state, newStage, newTeam);
            texture.upload();
        }
        return location;
    }

    /**
     * Rasterises the static layer.
     *
     * <p>Reuses {@link Minimap} to do the drawing so the baked layer and every un-baked consumer — the
     * map picker, the editor canvas, the offline preview tools — cannot drift apart. The bake is a
     * {@code Minimap} whose paint lands in an int array instead of a framebuffer.
     */
    private void bake(Model.Arena arena, JsonObject state, double centreX, double centreZ,
                      double newSpan, String dim, int stage) {
        int[] pixels = new int[tw * th];
        Minimap.Painter painter = Minimap.painter(pixels, tw, th);
        Minimap map = new Minimap(0, 0, tw, th, centreX, centreZ, newSpan);
        // Spawn markers are excluded from the bake: they are filtered to the viewer's team, and the bake
        // cannot know who is looking at it. Drawing both sides' spawns into a shared texture would put the
        // enemy's spawn points on the map, which is a gameplay leak, not a cosmetic one.
        // The bake is only used by the HUD minimap, which always has a terrain layer underneath, so its
        // panel must be transparent or it would paint over that terrain.
        map.paintStatic(painter, arena, state, stage, false, true);
        for (int i = 0; i < pixels.length; i++) {
            int x = i % tw, y = i / tw;
            texture.getPixels().setPixelRGBA(x, y, pixels[i]);
        }
    }

    /** Pixel width of the baked texture, needed by the blit destination. */
    int width() {
        return tw;
    }

    /** Pixel height of the baked texture. */
    int height() {
        return th;
    }

    /** Releases the registered texture. Safe to call repeatedly. */
    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        location = null;
        // Force a rebuild on next use even if the view happens to match the stale one.
        cx = Double.NaN;
        panelW = -1;
        stage = Integer.MIN_VALUE;
    }
}
