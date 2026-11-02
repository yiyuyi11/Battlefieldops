package cn.bfops.killicon.client.config;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ValorantStyleCatalog {
   public static final String STYLE_PRIME = "prime";
   public static final String STYLE_GLITCHPOP = "glitchpop";
   public static final String STYLE_SINGULARITY_V1 = "singularity_v1";
   public static final String STYLE_SINGULARITY_V2 = "singularity_v2";
   public static final String STYLE_SINGULARITY_V3 = "singularity_v3";
   public static final String STYLE_GAIA = "gaia";
   public static final String STYLE_GAIA_V1 = "gaia_v1";
   public static final String STYLE_GAIA_V2 = "gaia_v2";
   public static final String STYLE_GAIA_V3 = "gaia_v3";
   public static final String STYLE_BUBBLEGUM_DEATHWISH = "bubblegum_deathwish";
   public static final String STYLE_BUBBLEGUM_DEATHWISH_V1 = "bubblegum_deathwish_v1";
   public static final String STYLE_BUBBLEGUM_DEATHWISH_V2 = "bubblegum_deathwish_v2";
   public static final String STYLE_BUBBLEGUM_DEATHWISH_V3 = "bubblegum_deathwish_v3";
   public static final String STYLE_CHAMPIONS_2021 = "champions_2021";
   public static final String STYLE_PRELUDE_TO_CHAOS_V1 = "prelude_to_chaos_v1";
   public static final String STYLE_PRELUDE_TO_CHAOS_V2 = "prelude_to_chaos_v2";
   public static final String STYLE_PRELUDE_TO_CHAOS_V3 = "prelude_to_chaos_v3";
   public static final String STYLE_PRIMORDIUM = "primordium";
   public static final String STYLE_PRIMORDIUM_V1 = "primordium_v1";
   public static final String STYLE_PRIMORDIUM_V2 = "primordium_v2";
   public static final String STYLE_PRIMORDIUM_V3 = "primordium_v3";
   public static final String STYLE_RADIANT_CRISIS_001 = "radiant_crisis_001";
   public static final String STYLE_RGX_11Z_PRO = "rgx_11z_pro";
   public static final String STYLE_RGX_11Z_PRO_V1 = "rgx_11z_pro_v1";
   public static final String STYLE_RGX_11Z_PRO_V2 = "rgx_11z_pro_v2";
   public static final String STYLE_RGX_11Z_PRO_V3 = "rgx_11z_pro_v3";
   public static final String TEXTURE_ICON = "icon";
   public static final String TEXTURE_FRAME = "frame";
   public static final String TEXTURE_RING = "ring";
   public static final String TEXTURE_BLADE = "blade";
   public static final String TEXTURE_BAR = "bar";
   public static final String TEXTURE_HEADSHOT = "headshot";
   public static final String TEXTURE_BASE_PARTICLE = "base_particle";
   public static final String TEXTURE_HERO_FLAME = "hero_flame";
   public static final String TEXTURE_LARGE_SPARKS = "large_sparks";
   public static final String TEXTURE_X_SPARKS = "x_sparks";
   private static final Map<String, ValorantStyleCatalog.StyleSpec> STYLE_SPECS = new LinkedHashMap<>();
   private static final Map<String, ValorantStyleCatalog.StyleSpec> PRESET_SPECS = new LinkedHashMap<>();

   private ValorantStyleCatalog() {
   }

   private static ValorantStyleCatalog.StyleSpec style(
      String presetId,
      String styleId,
      String displayName,
      int accentColor,
      String comboSoundPrefix,
      int headshotOffsetX,
      int headshotOffsetY,
      boolean emblemOnlyEntryMotion,
      String frameBladeTexture,
      String emblemTexture,
      String frameTexture,
      String barTexture
   ) {
      Map<String, String> textures = new LinkedHashMap<>();
      textures.put("icon", emblemTexture);
      textures.put("frame", frameTexture);
      textures.put("ring", getDefaultRingTexture(styleId));
      if (frameBladeTexture != null && !frameBladeTexture.isBlank()) {
         textures.put("blade", frameBladeTexture);
      }

      textures.put("bar", barTexture);
      textures.put("headshot", "killicon_valorant_headshot.png");
      textures.put("base_particle", "killicon_valorant_particle_base_t1.png");
      textures.put("hero_flame", "killicon_valorant_particle_hero_flame.png");
      textures.put("large_sparks", "killicon_valorant_particle_large_sparks.png");
      textures.put("x_sparks", "killicon_valorant_particle_x_sparks.png");
      return new ValorantStyleCatalog.StyleSpec(
         presetId,
         styleId,
         displayName,
         accentColor,
         comboSoundPrefix,
         headshotOffsetX,
         headshotOffsetY,
         emblemOnlyEntryMotion,
         frameBladeTexture,
         Map.copyOf(textures)
      );
   }

   private static void register(ValorantStyleCatalog.StyleSpec spec) {
      STYLE_SPECS.put(spec.styleId(), spec);
      PRESET_SPECS.put(spec.presetId(), spec);
   }

   public static ValorantStyleCatalog.StyleSpec getStyleSpec(String styleId) {
      return STYLE_SPECS.get(normalizeStyleId(styleId));
   }

   public static ValorantStyleCatalog.StyleSpec getPresetSpec(String presetId) {
      return PRESET_SPECS.get(presetId);
   }

   public static List<ValorantStyleCatalog.StyleSpec> getOfficialPresetSpecs() {
      return new ArrayList<>(PRESET_SPECS.values());
   }

   public static List<ValorantStyleCatalog.StyleSpec> getDefinitions() {
      return getOfficialPresetSpecs();
   }

   public static String getDefaultStyleForPreset(String presetId) {
      ValorantStyleCatalog.StyleSpec spec = getPresetSpec(presetId);
      return spec != null ? spec.styleId() : "prime";
   }

   public static int getHeadshotOffsetX(String styleId) {
      return 0;
   }

   public static int getHeadshotOffsetY(String styleId) {
      return 0;
   }

   public static String getFrameBladeTextureFileName(String styleId) {
      if (!usesBlade(styleId)) {
         return null;
      } else {
         ValorantStyleCatalog.StyleSpec spec = getStyleSpec(styleId);
         if (spec == null) {
            return null;
         } else {
            String texture = spec.texture("blade");
            return texture != null ? texture : spec.frameBladeTexture();
         }
      }
   }

   public static boolean isBubblegumDeathwishStyle(String styleId) {
      return "bubblegum_deathwish".equals(styleId)
         || "bubblegum_deathwish_v1".equals(styleId)
         || "bubblegum_deathwish_v2".equals(styleId)
         || "bubblegum_deathwish_v3".equals(styleId);
   }

   public static boolean usesBlade(String styleId) {
      return isBubblegumDeathwishStyle(styleId);
   }

   public static boolean useEmblemOnlyEntryMotion(String styleId) {
      ValorantStyleCatalog.StyleSpec spec = getStyleSpec(styleId);
      return spec != null && spec.emblemOnlyEntryMotion();
   }

   public static String normalizeStyleId(String styleId) {
      if (styleId != null && !styleId.isBlank()) {
         String normalized = styleId.toLowerCase(Locale.ROOT);

         return switch (normalized) {
            case "prime" -> "prime";
            case "glitchpop" -> "glitchpop";
            case "singularity", "singularity_v1" -> "singularity_v1";
            case "singularity_v2" -> "singularity_v2";
            case "singularity_v3" -> "singularity_v3";
            case "gaia", "gaia_vengeance" -> "gaia";
            case "gaia_v1", "gaia_vengeance_v1" -> "gaia_v1";
            case "gaia_v2", "gaia_vengeance_v2" -> "gaia_v2";
            case "gaia_v3", "gaia_vengeance_v3" -> "gaia_v3";
            default -> STYLE_SPECS.containsKey(normalized) ? normalized : "prime";
         };
      } else {
         return "prime";
      }
   }

   public static String resolveStyleId(String presetId, JsonObject config) {
      return getDefaultStyleForPreset(presetId);
   }

   public static String getOfficialTextureFileName(String presetId, String textureKey) {
      ValorantStyleCatalog.StyleSpec spec = getPresetSpec(presetId);
      if (spec == null) {
         spec = getPresetSpec("00009");
      }

      return spec != null ? spec.texture(normalizeTextureKey(textureKey)) : null;
   }

   public static String getOfficialTextureFileNameForStyle(String styleId, String textureKey) {
      ValorantStyleCatalog.StyleSpec spec = getStyleSpec(styleId);
      if (spec == null) {
         spec = getStyleSpec("prime");
      }

      return spec != null ? spec.texture(normalizeTextureKey(textureKey)) : null;
   }

   public static String getComboSoundBaseName(String presetId, JsonObject config, int comboTier) {
      ValorantStyleCatalog.StyleSpec spec = getStyleSpec(resolveStyleId(presetId, config));
      int tier = Math.max(1, Math.min(comboTier, 5));
      String prefix = spec != null ? spec.comboSoundPrefix() : "valorant_primekill_";
      return prefix + tier;
   }

   public static String getDefaultParticleColorHex(String presetId, JsonObject config) {
      ValorantStyleCatalog.StyleSpec spec = getStyleSpec(resolveStyleId(presetId, config));
      int color = spec != null ? spec.accentColor() : 9473229;
      return String.format("#%06X", color & 16777215);
   }

   private static String getDefaultRingTexture(String styleId) {
      String normalized = normalizeStyleId(styleId);

      return switch (normalized) {
         case "bubblegum_deathwish", "bubblegum_deathwish_v1", "bubblegum_deathwish_v2", "bubblegum_deathwish_v3" -> "killicon_valorant_bubblegum_deathwish_ring.png";
         case "glitchpop" -> "killicon_valorant_glitchpop_ring.png";
         case "rgx_11z_pro", "rgx_11z_pro_v1", "rgx_11z_pro_v2", "rgx_11z_pro_v3" -> "killicon_valorant_rgx_11z_pro_ring.png";
         default -> "killicon_valorant_base_ring.png";
      };
   }

   private static String normalizeTextureKey(String textureKey) {
      return "emblem".equals(textureKey) ? "icon" : textureKey;
   }

   static {
      register(
         style(
            "00009",
            "prime",
            "Prime",
            9473229,
            "valorant_primekill_",
            0,
            -16,
            false,
            null,
            "killicon_valorant_prime_emblem.png",
            "killicon_valorant_prime_frame.png",
            "killicon_valorant_bar.png"
         )
      );
      register(
         style(
            "00010",
            "glitchpop",
            "Glitchpop",
            2529269,
            "valorant_glitchpopkill_",
            0,
            -16,
            false,
            null,
            "killicon_valorant_glitchpop_emblem.png",
            "killicon_valorant_glitchpop_frame.png",
            "killicon_valorant_glitchpop_bar.png"
         )
      );
      register(
         style(
            "00011",
            "singularity_v1",
            "Singularity V1",
            14646857,
            "valorant_singularitykill_",
            0,
            -10,
            false,
            null,
            "killicon_valorant_singularity_v1_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_singularity_v1_bar.png"
         )
      );
      register(
         style(
            "00012",
            "singularity_v2",
            "Singularity V2",
            14469489,
            "valorant_singularitykill_",
            0,
            -10,
            false,
            null,
            "killicon_valorant_singularity_v2_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_singularity_v2_bar.png"
         )
      );
      register(
         style(
            "00013",
            "singularity_v3",
            "Singularity V3",
            8298204,
            "valorant_singularitykill_",
            0,
            -10,
            false,
            null,
            "killicon_valorant_singularity_v3_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_singularity_v3_bar.png"
         )
      );
      register(
         style(
            "00014",
            "gaia",
            "Gaia's Vengeance",
            16340062,
            "valorant_gaiavengeancekill_",
            -2,
            -20,
            false,
            null,
            "killicon_valorant_gaia_emblem.png",
            "killicon_valorant_gaia_frame.png",
            "killicon_valorant_gaia_bar.png"
         )
      );
      register(
         style(
            "00015",
            "gaia_v1",
            "Gaia's Vengeance V1",
            2653939,
            "valorant_gaiavengeancekill_",
            -2,
            -20,
            false,
            null,
            "killicon_valorant_gaia_v1_emblem.png",
            "killicon_valorant_gaia_v1_frame.png",
            "killicon_valorant_gaia_v1_bar.png"
         )
      );
      register(
         style(
            "00016",
            "gaia_v2",
            "Gaia's Vengeance V2",
            2602824,
            "valorant_gaiavengeancekill_",
            -2,
            -20,
            false,
            null,
            "killicon_valorant_gaia_v2_emblem.png",
            "killicon_valorant_gaia_v2_frame.png",
            "killicon_valorant_gaia_v2_bar.png"
         )
      );
      register(
         style(
            "00017",
            "gaia_v3",
            "Gaia's Vengeance V3",
            16216356,
            "valorant_gaiavengeancekill_",
            -2,
            -20,
            false,
            null,
            "killicon_valorant_gaia_v3_emblem.png",
            "killicon_valorant_gaia_v3_frame.png",
            "killicon_valorant_gaia_v3_bar.png"
         )
      );
      register(
         style(
            "00018",
            "bubblegum_deathwish",
            "Bubblegum Deathwish",
            13193145,
            "valorant_bubblegumdeathwishkill_",
            0,
            -12,
            true,
            "killicon_valorant_bubblegum_deathwish_blade.png",
            "killicon_valorant_bubblegum_deathwish_emblem.png",
            "killicon_valorant_bubblegum_deathwish_frame.png",
            "killicon_valorant_bubblegum_deathwish_bar.png"
         )
      );
      register(
         style(
            "00019",
            "bubblegum_deathwish_v1",
            "Bubblegum Deathwish V1",
            13209164,
            "valorant_bubblegumdeathwishkill_",
            0,
            -12,
            true,
            "killicon_valorant_bubblegum_deathwish_blade.png",
            "killicon_valorant_bubblegum_deathwish_v3_emblem.png",
            "killicon_valorant_bubblegum_deathwish_frame.png",
            "killicon_valorant_bubblegum_deathwish_v1_bar.png"
         )
      );
      register(
         style(
            "00020",
            "bubblegum_deathwish_v2",
            "Bubblegum Deathwish V2",
            10302255,
            "valorant_bubblegumdeathwishkill_",
            0,
            -12,
            true,
            "killicon_valorant_bubblegum_deathwish_blade.png",
            "killicon_valorant_bubblegum_deathwish_v2_emblem.png",
            "killicon_valorant_bubblegum_deathwish_frame.png",
            "killicon_valorant_bubblegum_deathwish_v2_bar.png"
         )
      );
      register(
         style(
            "00021",
            "bubblegum_deathwish_v3",
            "Bubblegum Deathwish V3",
            7254071,
            "valorant_bubblegumdeathwishkill_",
            0,
            -12,
            true,
            "killicon_valorant_bubblegum_deathwish_blade.png",
            "killicon_valorant_bubblegum_deathwish_v1_emblem.png",
            "killicon_valorant_bubblegum_deathwish_frame.png",
            "killicon_valorant_bubblegum_deathwish_v3_bar.png"
         )
      );
      register(
         style(
            "00022",
            "champions_2021",
            "Champions 2021",
            9728070,
            "valorant_champions2021kill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_champions_2021_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_champions_2021_bar.png"
         )
      );
      register(
         style(
            "00023",
            "prelude_to_chaos_v1",
            "Prelude to Chaos V1",
            16019031,
            "valorant_preludetochaoskill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_prelude_to_chaos_v1_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_prelude_to_chaos_v1_bar.png"
         )
      );
      register(
         style(
            "00024",
            "prelude_to_chaos_v2",
            "Prelude to Chaos V2",
            1098000,
            "valorant_preludetochaoskill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_prelude_to_chaos_v2_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_prelude_to_chaos_v2_bar.png"
         )
      );
      register(
         style(
            "00025",
            "prelude_to_chaos_v3",
            "Prelude to Chaos V3",
            1140929,
            "valorant_preludetochaoskill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_prelude_to_chaos_v3_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_prelude_to_chaos_v3_bar.png"
         )
      );
      register(
         style(
            "00026",
            "primordium",
            "Primordium",
            9387569,
            "valorant_primordiumkill_",
            0,
            -14,
            false,
            null,
            "killicon_valorant_primordium_emblem.png",
            "killicon_valorant_primordium_frame.png",
            "killicon_valorant_primordium_bar.png"
         )
      );
      register(
         style(
            "00027",
            "primordium_v1",
            "Primordium V1",
            3701329,
            "valorant_primordiumkill_",
            0,
            -14,
            false,
            null,
            "killicon_valorant_primordium_v1_emblem.png",
            "killicon_valorant_primordium_frame.png",
            "killicon_valorant_primordium_v1_bar.png"
         )
      );
      register(
         style(
            "00028",
            "primordium_v2",
            "Primordium V2",
            3238020,
            "valorant_primordiumkill_",
            0,
            -14,
            false,
            null,
            "killicon_valorant_primordium_v2_emblem.png",
            "killicon_valorant_primordium_frame.png",
            "killicon_valorant_primordium_v2_bar.png"
         )
      );
      register(
         style(
            "00029",
            "primordium_v3",
            "Primordium V3",
            9269059,
            "valorant_primordiumkill_",
            0,
            -14,
            false,
            null,
            "killicon_valorant_primordium_v3_emblem.png",
            "killicon_valorant_primordium_frame.png",
            "killicon_valorant_primordium_v3_bar.png"
         )
      );
      register(
         style(
            "00030",
            "radiant_crisis_001",
            "Radiant Crisis 001",
            7585988,
            "valorant_radiantcrisis001kill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_radiant_crisis_001_emblem.png",
            "killicon_valorant_base_frame.png",
            "killicon_valorant_radiant_crisis_001_bar.png"
         )
      );
      register(
         style(
            "00031",
            "rgx_11z_pro",
            "RGX 11z Pro",
            12710721,
            "valorant_rgx11zprokill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_rgx_11z_pro_emblem.png",
            "killicon_valorant_rgx_11z_pro_frame.png",
            "killicon_valorant_rgx_11z_pro_bar.png"
         )
      );
      register(
         style(
            "00032",
            "rgx_11z_pro_v1",
            "RGX 11z Pro V1",
            15941962,
            "valorant_rgx11zprokill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_rgx_11z_pro_v1_emblem.png",
            "killicon_valorant_rgx_11z_pro_frame.png",
            "killicon_valorant_rgx_11z_pro_v1_bar.png"
         )
      );
      register(
         style(
            "00033",
            "rgx_11z_pro_v2",
            "RGX 11z Pro V2",
            4307699,
            "valorant_rgx11zprokill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_rgx_11z_pro_v2_emblem.png",
            "killicon_valorant_rgx_11z_pro_frame.png",
            "killicon_valorant_rgx_11z_pro_v2_bar.png"
         )
      );
      register(
         style(
            "00034",
            "rgx_11z_pro_v3",
            "RGX 11z Pro V3",
            15968065,
            "valorant_rgx11zprokill_",
            0,
            -12,
            false,
            null,
            "killicon_valorant_rgx_11z_pro_v3_emblem.png",
            "killicon_valorant_rgx_11z_pro_frame.png",
            "killicon_valorant_rgx_11z_pro_v3_bar.png"
         )
      );
   }

   public static record StyleSpec(
      String presetId,
      String styleId,
      String displayName,
      int accentColor,
      String comboSoundPrefix,
      int headshotOffsetX,
      int headshotOffsetY,
      boolean emblemOnlyEntryMotion,
      String frameBladeTexture,
      Map<String, String> textures
   ) {
      public String texture(String textureKey) {
         return this.textures.get(textureKey);
      }
   }
}
