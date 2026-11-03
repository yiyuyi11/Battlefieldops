package cn.bfops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the kill-icon port against the pack's own preset corpus.
 *
 * <p>This test reads the 37 shipped presets from the resource tree, which is the only way to catch the class of
 * defect that matters here: the presets are data, they were authored by someone else, and they violate their
 * own schema. Three such violations were found while porting and all three are asserted below, because each one
 * would have produced a plausible-looking wrong result rather than an error:
 *
 * <ul>
 *   <li>{@code scroll_direction} is the <em>string</em> {@code "right"}, which means -1, not +1.</li>
 *   <li>{@code max_visible_icons} is the <em>float</em> {@code 5.0}, which Gson's {@code getAsInt} rejects.</li>
 *   <li>{@code 00005} and {@code 00006} configure no icon mode at all.</li>
 * </ul>
 */
public final class KillIconPortSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of("src", "main", "resources", "assets", "battlefield_ops", "presets", "official");
        check(Files.isDirectory(dir), "preset directory must exist");

        List<KillIconPresets.Preset> parsed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        check(files.size() == 37, "the pack ships 37 presets, found " + files.size());

        int scrollingPresets = 0;
        int otherModes = 0;
        int subtitleOnly = 0;
        for (Path file : files) {
            String id = file.getFileName().toString().replace(".json", "");
            String json = Files.readString(file, StandardCharsets.UTF_8);
            KillIconPresets.Preset preset = KillIconPresets.parse(id, json);
            if (preset == null) {
                failed.add(id);
                continue;
            }
            parsed.add(preset);
            check(preset.id().equals(id), "preset keeps its file id: " + id);
            check(preset.name() != null && !preset.name().isBlank(), "preset has a display name: " + id);
            if (preset.usesScrolling()) {
                scrollingPresets++;
                check(preset.config() != null, "a scrolling preset has a resolved config: " + id);
            } else if (preset.mode() != null && preset.mode().startsWith("kill_icon/")) {
                otherModes++;
            } else {
                subtitleOnly++;
            }
        }
        check(failed.isEmpty(), "every preset parses, failed: " + failed);
        check(parsed.size() == 37, "all 37 presets are represented");
        check(scrollingPresets == 6,
                "six presets use the scrolling mode (00001/00007/00008/00035/00036/00037), found " + scrollingPresets);
        check(otherModes == 29, "twenty-nine presets use another icon mode, found " + otherModes);
        check(subtitleOnly == 2, "00005 and 00006 are subtitle-only and must not be dropped, found " + subtitleOnly);

        KillIconPresets corpus = KillIconPresets.of(parsed);
        check(corpus.size() == 37, "the corpus reports 37 presets");

        // ---- the three schema violations ------------------------------------------------
        KillIconPresets.Preset bf6 = corpus.byId("00037");
        check(bf6 != null, "preset 00037 exists");
        check(bf6.usesScrolling(), "00037 is the Battlefield 6 scrolling preset");
        KillIconScroll.Config c37 = bf6.config();
        check(c37.direction == -1,
                "scroll_direction \"right\" means -1, not +1 (got " + c37.direction + ")");
        check(c37.maxVisible == 5,
                "max_visible_icons 5.0 reads as 5, not a crash (got " + c37.maxVisible + ")");
        check(c37.pinNewest, "00037 pins the newest icon");
        check("center".equals(c37.anchor), "00037 anchors at centre, got " + c37.anchor);
        check(c37.displayDuration == 3300, "00037 holds for 3.3s, got " + c37.displayDuration);
        check(c37.animationDuration == 200, "00037's animation is 200ms, got " + c37.animationDuration);
        check(c37.fadeOutDuration == 200, "00037's fade is 200ms, got " + c37.fadeOutDuration);
        check(c37.scale > 0.29f && c37.scale < 0.30f, "00037's scale is ~0.2968, got " + c37.scale);

        // 00001 authors its duration as a float in seconds (3.25).
        KillIconPresets.Preset own = corpus.byId("00001");
        check(own != null && own.usesScrolling(), "00001 is a scrolling preset");
        check(own.config().displayDuration == 3250,
                "a seconds-valued display_duration becomes 3250ms, got " + own.config().displayDuration);
        check(own.config().direction == 1, "a preset without scroll_direction scrolls +1");
        check(!own.config().pinNewest, "00001 does not pin the newest icon");

        // Every scrolling preset must produce a config whose invariants the renderer relies on.
        for (KillIconPresets.Preset preset : parsed) {
            if (!preset.usesScrolling()) continue;
            KillIconScroll.Config c = preset.config();
            check(c.animationDuration >= 1, preset.id() + " has a positive animation duration");
            check(c.fadeOutDuration >= 1, preset.id() + " has a positive fade-out duration");
            check(c.positionAnimationDuration >= 1, preset.id() + " has a positive position duration");
            check(c.maxVisible >= 1, preset.id() + " shows at least one icon");
            check(c.maxPending >= 0, preset.id() + " has a sane pending cap");
            check(c.displayInterval >= 0, preset.id() + " has a sane display interval");
            check(c.direction == 1 || c.direction == -1, preset.id() + " scrolls in a unit direction");
            check(ScreenAnchor.isValid(c.anchor), preset.id() + " has a valid anchor: " + c.anchor);
            check(c.displayDuration > 0, preset.id() + " holds its icons for a positive time");
        }

        // The four "named look" presets each swap whole texture families. Their art must exist, or the preset
        // silently draws nothing -- the failure mode the fallback chain in KillType exists to prevent.
        for (String id : List.of("00001", "00007", "00008", "00037")) {
            KillIconPresets.Preset preset = corpus.byId(id);
            check(preset != null && preset.usesScrolling(), id + " is a scrolling preset");
            for (KillType kind : KillType.all()) {
                List<String> candidates = kind.textureCandidates(id, "scrolling");
                check(!candidates.isEmpty(), kind.id() + " in " + id + " names at least one candidate");
                check(candidates.get(candidates.size() - 1).endsWith("_default.png"),
                        kind.id() + " in " + id + " ends its chain at a default file");
            }
        }

        // ---- the kill-type table --------------------------------------------------------
        check(KillType.all().size() == 11, "eleven kill types, matching the reference");
        for (int i = 0; i < KillType.all().size(); i++) {
            check(KillType.all().get(i).type() == i, "kill types are ordered by their numeric id at " + i);
        }
        check(KillType.byType(5).id().equals("DESTROY_VEHICLE"), "type 5 is DESTROY_VEHICLE");
        check(KillType.byType(999).id().equals("NORMAL"), "an unknown type falls back to NORMAL");
        check(KillType.byId("nonsense").id().equals("NORMAL"), "an unknown id falls back to NORMAL");
        // The one filename mismatch in the pack, asserted so a future rename cannot silently break it.
        check(KillType.byId("DESTROY_VEHICLE").fileNameStem().equals("destroyvehicle"),
                "destroy_vehicle is stored on disk as destroyvehicle");
        check(KillType.byId("DESTROY_VEHICLE").scrollingFile("other")
                        .equals("killicon_scrolling_destroyvehicle.png"),
                "and the scrolling file uses the normalised stem");
        check(KillType.byId("CAPTURE").scrollingFile("other").equals("killicon_df_capture.png"),
                "capture has no scrolling art and resolves to the df variant");
        check(KillType.byId("SPOT_ASSIST").scrollingFile("00008").equals("killicon_scrolling_assist.png"),
                "spot_assist borrows the assist icon outside 00037");
        check(KillType.byId("SPOT_ASSIST").scrollingFile("00037")
                        .equals("killicon_battlefield6_spot_assist.png"),
                "but has its own icon in 00037");
        // Kinds without art of their own in a family still name a real file first in their chain.
        for (KillType kind : KillType.all()) {
            for (String family : List.of("scrolling", "battlefield1", "battlefield6")) {
                List<String> chain = kind.textureCandidates("00002", family);
                // The invariant is not "at least two steps": several kinds draw the family's `default` art even
                // in their own family (ASSIST in battlefield1, NORMAL everywhere), so their chain correctly
                // collapses to a single entry. What must hold is that the chain is non-empty, has no repeated
                // lookups, and always ends at a `_default` texture -- the one file every family ships.
                check(!chain.isEmpty(), kind.id() + " in " + family + " names at least one file");
                check(chain.stream().distinct().count() == chain.size(),
                        kind.id() + " in " + family + " has no duplicate lookups: " + chain);
                check(chain.get(chain.size() - 1).endsWith("_default.png"),
                        kind.id() + " in " + family + " ends at a default texture: " + chain);
            }
        }
        check(!KillType.byId("NORMAL").ringable(), "a normal kill has no ring effect");
        check(KillType.byId("HEADSHOT").ringable(), "a headshot does");
        check(KillType.byId("MEDIC").hideable() == false, "medic cannot be hidden by config");

        // ---- anchors --------------------------------------------------------------------
        check(ScreenAnchor.ids().size() == 9, "nine anchors");
        check(ScreenAnchor.isValid("bottom_center"), "bottom_center is valid");
        check(!ScreenAnchor.isValid("middle_centre"), "the British spelling is not an anchor");
        check(ScreenAnchor.orDefault(null).equals("bottom_center"), "a null anchor falls back");
        check(ScreenAnchor.orDefault("nope").equals("bottom_center"), "an unknown anchor falls back");
        for (String anchor : ScreenAnchor.ids()) {
            check(ScreenAnchor.isValid(anchor), anchor + " round-trips");
        }
        // y_offset grows upward: a positive offset moves the element up the screen.
        int cy0 = ScreenAnchor.centerY("bottom_center", 0, 1080);
        int cy120 = ScreenAnchor.centerY("bottom_center", 120, 1080);
        check(cy120 < cy0, "a positive y_offset moves the element up");
        check(cy0 - cy120 == 120, "and by exactly that many pixels");
        check(ScreenAnchor.centerX("center", 0, 1920) == 960, "centre anchors at the screen centre");
        check(ScreenAnchor.centerX("center", 7, 1920) == 967, "x_offset shifts right");
        check(ScreenAnchor.centerY("top_left", 0, 1080) == 0, "top anchors at y=0");
        check(ScreenAnchor.centerY("bottom_left", 0, 1080) == 1080, "bottom anchors at y=height");
        // Changing the anchor must not move the element.
        int movedX = ScreenAnchor.translateXOffset("bottom_center", "bottom_left", 40, 1920);
        check(ScreenAnchor.centerX("bottom_left", movedX, 1920)
                        == ScreenAnchor.centerX("bottom_center", 40, 1920),
                "translating the x offset preserves screen position across anchors");
        int movedY = ScreenAnchor.translateYOffset("bottom_center", "center", 120, 1080);
        check(ScreenAnchor.centerY("center", movedY, 1080)
                        == ScreenAnchor.centerY("bottom_center", 120, 1080),
                "translating the y offset preserves screen position across anchors");

        // ---- icon sharpness -------------------------------------------------------------
        // The ported icon art is far larger than the 64 px quad it is drawn on. That fact is what makes the
        // filtering path worth auditing at all, so pin it: if the art is ever resized down to near its draw
        // size, this is the check that notices the premise changed.
        java.nio.file.Path iconRoot = java.nio.file.Path.of("src/main/resources/assets/battlefield_ops/icons");
        int largest = 0;
        String largestName = "";
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(iconRoot)) {
            for (java.nio.file.Path png : paths.filter(p -> p.toString().endsWith(".png")).toList()) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(png)) {
                    byte[] header = in.readNBytes(24);
                    if (header.length < 24 || header[0] != (byte) 0x89) continue;
                    int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                            | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                    if (w > largest) {
                        largest = w;
                        largestName = png.getFileName().toString();
                    }
                }
            }
        } catch (java.io.IOException ex) {
            check(false, "icon art must be readable: " + ex.getMessage());
        }
        check(largest > 0, "the icon art was found on disk");
        check(largest >= 4 * 64,
                "at least one icon is drawn well below its source resolution (largest " + largest + " px, "
                        + largestName + ")");

        // The sharpness question is settled by the upload path, not by wishing: DynamicTexture allocates mip
        // level 0 only, so every filtering call must pass mipmap=false. Passing true would set
        // GL_LINEAR_MIPMAP_LINEAR and sample levels that were never filled, drawing the icon black. This
        // asserts the source keeps that argument false, and explains why in the failure message.
        String textureManager = readSource("src/main/java/cn/bfops/killicon/client/textures/ExternalTextureManager.java");
        String filterEffect = readSource(
                "src/main/java/cn/bfops/killicon/client/render/effect/IconTextureFilterEffect.java");
        check(!textureManager.contains("isEnableIconAntialiasing(), true)"),
                "the texture loader must not request mipmapped filtering: DynamicTexture only allocates mip "
                        + "level 0, so GL_LINEAR_MIPMAP_LINEAR would sample undefined levels and render icons black");
        check(filterEffect.contains("apply(textureLocation, false)"),
                "IconTextureFilterEffect defaults to mipmap=false for the same reason");

        System.out.println("KillIconPortSim: " + checks + " checks passed");
    }

    /** Reads a workspace source file as text, for the checks that pin an argument that must not change. */
    private static String readSource(String relativePath) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(relativePath));
        } catch (java.io.IOException ex) {
            check(false, "source must be readable: " + relativePath + " (" + ex.getMessage() + ")");
            return "";
        }
    }
}
