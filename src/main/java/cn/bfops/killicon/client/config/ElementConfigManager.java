package cn.bfops.killicon.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.resources.language.I18n;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.sounds.ExternalSoundManager;
import cn.bfops.killicon.client.textures.ExternalTextureManager;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.client.util.ConfigFileUtil;
import cn.bfops.killicon.client.util.I18nCompat;
import cn.bfops.killicon.common.bonus.BonusRegistry;
import cn.bfops.killicon.common.config.ElementConfigRegistry;
import cn.bfops.killicon.common.honor.HonorRegistry;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class ElementConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File CONFIG_DIR = ClientBridge.loader().getConfigDir().resolve("battlefield_ops").toFile();
   private static final File PRESETS_DIR = new File(CONFIG_DIR, "presets");
   private static final int FORMAT_VERSION = 1;
   private static final Map<String, ElementConfigManager.ElementPreset> PRESETS = new HashMap<>();
   private static Map<String, ElementConfigManager.ElementPreset> TEMP_PRESETS = null;
   private static boolean isEditing = false;

   public static void startEditing() {
      if (!isEditing) {
         TEMP_PRESETS = new HashMap<>();

         for (Entry<String, ElementConfigManager.ElementPreset> entry : PRESETS.entrySet()) {
            ElementConfigManager.ElementPreset preset = new ElementConfigManager.ElementPreset();
            preset.setDisplayName(entry.getValue().getDisplayName());

            for (Entry<String, JsonObject> elementEntry : entry.getValue().elementConfigs.entrySet()) {
               preset.addElementConfig(elementEntry.getKey(), elementEntry.getValue().deepCopy());
            }

            TEMP_PRESETS.put(entry.getKey(), preset);
         }

         isEditing = true;
      }
   }

   public static void saveChanges() {
      if (isEditing) {
         PRESETS.clear();
         PRESETS.putAll(TEMP_PRESETS);
         isEditing = false;
         TEMP_PRESETS = null;
         saveConfig();
      }
   }

   public static void discardChanges() {
      if (isEditing) {
         isEditing = false;
         TEMP_PRESETS = null;
      }
   }

   public static boolean hasUnsavedChanges() {
      if (isEditing && TEMP_PRESETS != null) {
         if (TEMP_PRESETS.size() != PRESETS.size()) {
            return true;
         } else {
            for (Entry<String, ElementConfigManager.ElementPreset> entry : PRESETS.entrySet()) {
               String key = entry.getKey();
               if (!TEMP_PRESETS.containsKey(key)) {
                  return true;
               }

               if (!TEMP_PRESETS.get(key).equals(entry.getValue())) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static void addElement(String presetId, String elementId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset != null) {
         if (preset.getConfig(elementId) == null) {
            JsonObject safeDefaults = getDefaultElementConfig(presetId, elementId);
            if (!safeDefaults.entrySet().isEmpty()) {
               preset.addElementConfig(elementId, safeDefaults);
               if (!isEditing) {
                  saveConfig();
                  ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.element_added", elementId, presetId);
               }
            }
         }
      }
   }

   public static boolean deletePreset(String presetId) {
      if (isOfficialPreset(presetId)) {
         ClientMessageLogger.chatWarn("battlefield_ops.client.config.element.delete_fail_official");
         return false;
      } else {
         Map<String, ElementConfigManager.ElementPreset> presets = getActivePresets();
         if (presets.containsKey(presetId)) {
            presets.remove(presetId);
            if (!isEditing) {
               saveConfig();
               ClientMessageLogger.chatInfo("battlefield_ops.client.config.element.delete_success", presetId);
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public static boolean renamePresetId(String oldId, String newId) {
      if (isOfficialPreset(oldId)) {
         return false;
      } else if (oldId.equals(newId)) {
         return true;
      } else {
         Map<String, ElementConfigManager.ElementPreset> presets = getActivePresets();
         if (!presets.containsKey(oldId)) {
            return false;
         } else if (presets.containsKey(newId)) {
            return false;
         } else {
            ElementConfigManager.ElementPreset preset = presets.remove(oldId);
            presets.put(newId, preset);
            if (!isEditing) {
               saveConfig();
            }

            return true;
         }
      }
   }

   public static boolean presetExists(String presetId) {
      return getActivePresets().containsKey(presetId);
   }

   public static String createNewPreset() {
      String newId;
      do {
         int randomNum = (int)(Math.random() * 100000.0);
         newId = String.format("%05d", randomNum);
      } while (presetExists(newId) || isOfficialPreset(newId));

      ElementConfigManager.ElementPreset preset = new ElementConfigManager.ElementPreset();
      String defaultNameKey = "battlefield_ops.client.config.preset.new_preset_default_name";
      String defaultName = I18nCompat.exists(defaultNameKey) ? I18n.get(defaultNameKey, new Object[0]) : "New Custom Preset";
      preset.setDisplayName(defaultName);
      getActivePresets().put(newId, preset);
      ensurePresetAssets(newId);
      if (!isEditing) {
         saveConfig();
         ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.preset_created", newId);
      }

      return newId;
   }

   public static ElementConfigManager.ElementPreset getPreset(String presetId) {
      return getActivePresets().get(presetId);
   }

   public static Map<String, ElementConfigManager.ElementPreset> getActivePresets() {
      return isEditing && TEMP_PRESETS != null ? TEMP_PRESETS : PRESETS;
   }

   public static boolean isOfficialPreset(String presetId) {
      return DefaultConfigRegistry.isOfficialPreset(presetId);
   }

   public static void init() {
      if (!CONFIG_DIR.exists()) {
         CONFIG_DIR.mkdirs();
      }

      loadConfig();
   }

   public static void loadConfig() {
      if (!PRESETS_DIR.exists()) {
         PRESETS_DIR.mkdirs();
      }

      HonorRegistry.getIds();
      KillTypeRegistry.getAll();
      BonusRegistry.getAll();
      File[] existingFiles = PRESETS_DIR.listFiles((dir, name) -> name.endsWith(".json"));
      if (existingFiles == null || existingFiles.length == 0) {
         migrateLegacySingleFile();
      }

      ensureOfficialPresetFiles();
      PRESETS.clear();
      File[] files = PRESETS_DIR.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null && files.length != 0) {
         for (File file : files) {
            String presetId = file.getName().substring(0, file.getName().length() - 5);

            try {
               JsonObject presetJson = (JsonObject)GSON.fromJson(ConfigFileUtil.readText(file), JsonObject.class);
               PRESETS.put(presetId, parsePresetJson(presetJson, presetId));
            } catch (JsonSyntaxException var8) {
               ClientMessageLogger.error("battlefield_ops.client.config.element.load_fail_json");
               ClientMessageLogger.chatWarn("battlefield_ops.client.config.element.preset_skipped", presetId, var8.getMessage());
            } catch (Exception var9) {
               ClientMessageLogger.error("battlefield_ops.client.config.element.load_fail", var9.getMessage());
            }
         }

         if (PRESETS.isEmpty()) {
            createDefaultConfig();
         } else {
            boolean restored = false;

            for (String officialId : DefaultConfigRegistry.getOfficialPresetIds()) {
               if (!PRESETS.containsKey(officialId)) {
                  ElementConfigManager.ElementPreset officialPreset = loadOfficialPresetFromJar(officialId);
                  if (officialPreset != null) {
                     PRESETS.put(officialId, officialPreset);
                     restored = true;
                     ClientMessageLogger.info("battlefield_ops.client.config.element.restored_official", officialId);
                  }
               }
            }

            if (ClientConfigManager.getBonusFormatMigrated() < 3) {
               BonusFormatMigrator.migrate();
               ClientConfigManager.setBonusFormatMigrated(3);
            }

            boolean normalized = normalizePresets();
            if (normalized || restored) {
               saveConfig();
            }

            for (String presetId : PRESETS.keySet()) {
               ensurePresetAssets(presetId);
            }

            ClientMessageLogger.info("battlefield_ops.client.config.element.load_success");
         }
      } else {
         createDefaultConfig();
      }
   }

   private static ElementConfigManager.ElementPreset parsePresetJson(JsonObject presetJson, String presetId) {
      ElementConfigManager.ElementPreset preset = new ElementConfigManager.ElementPreset();
      if (presetJson != null && presetJson.has("display_name")) {
         preset.setDisplayName(presetJson.get("display_name").getAsString());
      } else {
         preset.setDisplayName(DefaultConfigRegistry.getOfficialPresetDisplayName(presetId));
      }

      if (presetJson == null) {
         return preset;
      } else {
         for (Entry<String, JsonElement> elementEntry : presetJson.entrySet()) {
            String elementKey = elementEntry.getKey();
            if (!elementKey.equals("display_name") && !elementKey.equals("format_version") && elementEntry.getValue().isJsonObject()) {
               preset.addElementConfig(elementKey, elementEntry.getValue().getAsJsonObject());
            }
         }

         return preset;
      }
   }

   private static void ensureOfficialPresetFiles() {
      for (String officialId : DefaultConfigRegistry.getOfficialPresetIds()) {
         File file = new File(PRESETS_DIR, officialId + ".json");
         if (needsRegenerate(file)) {
            String jarContent = readJarResource("/assets/battlefield_ops/presets/official/" + officialId + ".json");
            if (jarContent != null) {
               try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                  writer.write(jarContent);
               } catch (IOException var9) {
                  ClientMessageLogger.error("battlefield_ops.client.config.element.save_fail", officialId, var9.getMessage());
               }

               ClientMessageLogger.info("battlefield_ops.client.config.element.restored_official", officialId);
            }
         }
      }
   }

   private static boolean needsRegenerate(File file) {
      if (!file.exists()) {
         return true;
      } else {
         int fileVersion = 0;

         try {
            JsonObject json = (JsonObject)GSON.fromJson(ConfigFileUtil.readText(file), JsonObject.class);
            if (json != null && json.has("format_version")) {
               fileVersion = json.get("format_version").getAsInt();
            }
         } catch (Exception var3) {
            return true;
         }

         return fileVersion > 0 && fileVersion < 1;
      }
   }

   private static ElementConfigManager.ElementPreset loadOfficialPresetFromJar(String presetId) {
      String content = readJarResource("/assets/battlefield_ops/presets/official/" + presetId + ".json");
      if (content == null) {
         return null;
      } else {
         JsonObject json = (JsonObject)GSON.fromJson(content, JsonObject.class);
         return parsePresetJson(json, presetId);
      }
   }

   private static String readJarResource(String path) {
      try {
         String var5;
         try (InputStream in = ElementConfigManager.class.getResourceAsStream(path)) {
            if (in == null) {
               return null;
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];

            int n;
            while ((n = in.read(data)) != -1) {
               buffer.write(data, 0, n);
            }

            var5 = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
         }

         return var5;
      } catch (Exception var8) {
         return null;
      }
   }

   private static void migrateLegacySingleFile() {
      File legacyFile = new File(CONFIG_DIR, "element_config.json");
      if (legacyFile.exists()) {
         try {
            JsonObject json = (JsonObject)GSON.fromJson(ConfigFileUtil.readText(legacyFile), JsonObject.class);
            if (json == null) {
               return;
            }

            for (Entry<String, JsonElement> entry : json.entrySet()) {
               if (entry.getValue().isJsonObject()) {
                  writePresetFile(new File(PRESETS_DIR, entry.getKey() + ".json"), parsePresetJson(entry.getValue().getAsJsonObject(), entry.getKey()));
               }
            }

            legacyFile.renameTo(new File(CONFIG_DIR, "element_config.json.migrated"));
            ClientMessageLogger.info("battlefield_ops.client.config.element.legacy_migrated");
         } catch (Exception var4) {
            ClientMessageLogger.error("battlefield_ops.client.config.element.legacy_migrate_fail", var4.getMessage());
         }
      }
   }

   public static Set<String> completeMissingKeys(String presetId, String elementId, JsonObject config) {
      if (config == null) {
         return Collections.emptySet();
      } else {
         JsonObject defaults = getDefaultElementConfig(presetId, elementId);
         if (defaults.entrySet().isEmpty()) {
            return Collections.emptySet();
         } else {
            Set<String> completed = new HashSet<>();

            for (Entry<String, JsonElement> defaultEntry : defaults.entrySet()) {
               if (!config.has(defaultEntry.getKey())) {
                  config.add(defaultEntry.getKey(), defaultEntry.getValue().deepCopy());
                  completed.add(defaultEntry.getKey());
               }
            }

            return completed;
         }
      }
   }

   private static boolean normalizePresets() {
      boolean changed = false;

      for (Entry<String, ElementConfigManager.ElementPreset> presetEntry : PRESETS.entrySet()) {
         String presetId = presetEntry.getKey();
         ElementConfigManager.ElementPreset preset = presetEntry.getValue();

         for (Entry<String, JsonObject> elementEntry : preset.elementConfigs.entrySet()) {
            String elementId = elementEntry.getKey();
            JsonObject config = elementEntry.getValue();
            if ("kill_icon/valorant".equals(elementId)) {
               changed |= migrateValorantConfig(presetId, config);
            }

            Set<String> completed = completeMissingKeys(presetId, elementId, config);
            if (!completed.isEmpty()) {
               changed = true;

               for (String key : completed) {
                  ClientMessageLogger.chatWarn("battlefield_ops.client.config.element.restored_missing", presetId, elementId, key);
               }
            }
         }
      }

      return changed;
   }

   private static boolean migrateValorantConfig(String presetId, JsonObject config) {
      boolean changed = false;
      if (config == null) {
         return false;
      } else {
         if (config.has("color_gaia_accent")) {
            String legacyAccent = config.get("color_gaia_accent").getAsString();
            if (!config.has("color_accent")) {
               config.addProperty("color_accent", legacyAccent);
               changed = true;
            }

            if (!config.has("enable_accent_tint")) {
               boolean accentCustomized = legacyAccent != null && !"#E2505C".equalsIgnoreCase(legacyAccent);
               config.addProperty("enable_accent_tint", accentCustomized);
               changed = true;
            }
         }

         if (config.has("skin_style")) {
            config.remove("skin_style");
            changed = true;
         }

         if (config.has("base_particle_speed")) {
            config.remove("base_particle_speed");
            changed = true;
         }

         if (config.has("hero_flame_speed")) {
            config.remove("hero_flame_speed");
            changed = true;
         }

         if (config.has("large_sparks_speed")) {
            config.remove("large_sparks_speed");
            changed = true;
         }

         if (config.has("x_sparks_speed")) {
            config.remove("x_sparks_speed");
            changed = true;
         }

         if (config.has("sound_volume")) {
            config.remove("sound_volume");
            changed = true;
         }

         if (config.has("headshot_sound_volume")) {
            config.remove("headshot_sound_volume");
            changed = true;
         }

         if (config.has("enable_icon_antialiasing")) {
            config.remove("enable_icon_antialiasing");
            changed = true;
         }

         if (config.has("display_duration")) {
            config.remove("display_duration");
            changed = true;
         }

         String[] legacyParticleConfigKeys = new String[]{
            "base_particle_scale",
            "base_particle_x_offset",
            "base_particle_y_offset",
            "base_particle_opacity",
            "base_particle_center_x_offset",
            "hero_flame_scale",
            "hero_flame_x_offset",
            "hero_flame_y_offset",
            "hero_flame_opacity",
            "large_sparks_scale",
            "large_sparks_x_offset",
            "large_sparks_y_offset",
            "large_sparks_opacity",
            "x_sparks_scale",
            "x_sparks_x_offset",
            "x_sparks_y_offset",
            "x_sparks_opacity",
            "enable_custom_color_base_particle",
            "enable_custom_color_hero_flame",
            "enable_custom_color_large_sparks",
            "enable_custom_color_x_sparks",
            "contrast",
            "icon_entry_curve",
            "color_headshot_anim_flicker",
            "headshot_anim_flicker_speed",
            "headshot_anim_scale_curve",
            "bar_entry_scale_curve",
            "icon_x_offset",
            "icon_y_offset",
            "frame_scale",
            "enable_ring",
            "ring_scale",
            "blade_scale",
            "bar_scale",
            "headshot_scale",
            "frame_x_offset",
            "frame_y_offset",
            "ring_x_offset",
            "ring_y_offset",
            "blade_x_offset",
            "blade_y_offset",
            "headshot_x_offset",
            "headshot_y_offset"
         };

         for (String key : legacyParticleConfigKeys) {
            if (config.has(key)) {
               config.remove(key);
               changed = true;
            }
         }

         if (!config.has("enable_blade_rotation_effect")) {
            config.addProperty("enable_blade_rotation_effect", true);
            changed = true;
         }

         if (!config.has("blade_deceleration_window")) {
            config.addProperty("blade_deceleration_window", 2.0F);
            changed = true;
         }

         if (!config.has("enable_math_particle_effect")) {
            config.addProperty("enable_math_particle_effect", false);
            changed = true;
         }

         String styleId = ValorantStyleCatalog.resolveStyleId(presetId, config);
         if (!config.has("enable_blade_effect")) {
            config.addProperty("enable_blade_effect", ValorantStyleCatalog.usesBlade(styleId));
            changed = true;
         }

         JsonObject valorantDefaults = ElementConfigRegistry.buildElementDefaults("kill_icon/valorant");
         int expectedBaseParticleYOffset = 45;
         if (valorantDefaults != null && valorantDefaults.has("anim_base_particle_texture_y_offset")) {
            expectedBaseParticleYOffset = valorantDefaults.get("anim_base_particle_texture_y_offset").getAsInt();
         }

         boolean expectedEnableBaseParticle = valorantDefaults == null
            || !valorantDefaults.has("enable_base_particle")
            || valorantDefaults.get("enable_base_particle").getAsBoolean();
         boolean expectedEnableHeroFlame = valorantDefaults == null
            || !valorantDefaults.has("enable_hero_flame")
            || valorantDefaults.get("enable_hero_flame").getAsBoolean();
         boolean expectedEnableLargeSparks = valorantDefaults == null
            || !valorantDefaults.has("enable_large_sparks")
            || valorantDefaults.get("enable_large_sparks").getAsBoolean();
         boolean expectedEnableXSparks = valorantDefaults == null
            || !valorantDefaults.has("enable_x_sparks")
            || valorantDefaults.get("enable_x_sparks").getAsBoolean();
         if (!config.has("enable_base_particle")) {
            config.addProperty("enable_base_particle", expectedEnableBaseParticle);
            changed = true;
         }

         if (!config.has("enable_hero_flame")) {
            config.addProperty("enable_hero_flame", expectedEnableHeroFlame);
            changed = true;
         }

         if (!config.has("enable_large_sparks")) {
            config.addProperty("enable_large_sparks", expectedEnableLargeSparks);
            changed = true;
         }

         if (!config.has("enable_x_sparks")) {
            config.addProperty("enable_x_sparks", expectedEnableXSparks);
            changed = true;
         }

         if (!config.has("anim_base_particle_texture_y_offset")) {
            config.addProperty("anim_base_particle_texture_y_offset", expectedBaseParticleYOffset);
            changed = true;
         }

         for (String textureKey : ElementTextureDefinition.getTextures("kill_icon/valorant")) {
            String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
            String officialKey = ElementTextureDefinition.getOfficialTextureKey(textureKey);
            String customKey = ElementTextureDefinition.getCustomTextureKey(textureKey);
            String expectedFile = ValorantStyleCatalog.getOfficialTextureFileNameForStyle(styleId, textureKey);
            String mode = config.has(modeKey) ? config.get(modeKey).getAsString() : "official";
            if (!"official".equalsIgnoreCase(mode) && !"custom".equalsIgnoreCase(mode) && !"vanilla".equalsIgnoreCase(mode)) {
               config.addProperty(modeKey, "official");
               changed = true;
               mode = "official";
            }

            if ("custom".equalsIgnoreCase(mode)) {
               String customTexture = config.has(customKey) ? config.get(customKey).getAsString() : "";
               if (customTexture == null || customTexture.isBlank()) {
                  config.addProperty(modeKey, "official");
                  changed = true;
               }
            }

            if (expectedFile != null && !expectedFile.isBlank()) {
               String officialTexture = config.has(officialKey) ? config.get(officialKey).getAsString() : null;
               if (officialTexture == null || officialTexture.isBlank() || !isValidValorantOfficialTexture(textureKey, officialTexture)) {
                  config.addProperty(officialKey, expectedFile);
                  changed = true;
               }
            }
         }

         return changed;
      }
   }

   private static boolean isValidValorantOfficialTexture(String textureKey, String fileName) {
      if (!ExternalTextureManager.isOfficialTextureName(fileName)) {
         return false;
      } else {
         for (ValorantStyleCatalog.StyleSpec definition : ValorantStyleCatalog.getDefinitions()) {
            String allowed = ValorantStyleCatalog.getOfficialTextureFileNameForStyle(definition.styleId(), textureKey);
            if (fileName.equals(allowed)) {
               return true;
            }
         }

         if ("emblem".equals(textureKey)) {
            return "killicon_valorant_icon.png".equals(fileName) || "killicon_valorant_gaia_icon.png".equals(fileName);
         } else {
            return !"bar".equals(textureKey) ? false : "killicon_valorant_bar.png".equals(fileName) || "killicon_valorant_gaia_bar.png".equals(fileName);
         }
      }
   }

   public static void createDefaultConfig() {
      PRESETS.clear();

      for (String officialId : DefaultConfigRegistry.getOfficialPresetIds()) {
         ElementConfigManager.ElementPreset preset = loadOfficialPresetFromJar(officialId);
         if (preset != null) {
            PRESETS.put(officialId, preset);
         }
      }

      saveConfig();
      if (!PRESETS.isEmpty()) {
         loadConfig();
      }
   }

   public static void resetPresetConfig(String presetId) {
      ElementConfigManager.ElementPreset currentPreset = getActivePresets().get(presetId);
      if (currentPreset == null) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.preset_not_found", presetId);
      } else {
         boolean updated = false;
         if (isOfficialPreset(presetId)) {
            ElementConfigManager.ElementPreset jarPreset = loadOfficialPresetFromJar(presetId);
            if (jarPreset != null) {
               currentPreset.elementConfigs.clear();
               currentPreset.elementConfigs.putAll(jarPreset.elementConfigs);
               updated = true;
            }
         } else {
            Set<String> currentElements = new HashSet<>(currentPreset.elementConfigs.keySet());
            if (currentElements.isEmpty()) {
               ClientMessageLogger.chatInfo("battlefield_ops.client.config.element.preset_empty", presetId);
               return;
            }

            for (String elementId : currentElements) {
               JsonObject safeDefaults = getDefaultElementConfig(elementId);
               if (!safeDefaults.entrySet().isEmpty()) {
                  currentPreset.addElementConfig(elementId, safeDefaults);
                  updated = true;
               }
            }
         }

         if (updated) {
            if (!isEditing) {
               saveConfig();
               ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.reset_preset_config_success", presetId);
            }
         } else {
            ClientMessageLogger.chatInfo("battlefield_ops.client.config.element.reset_preset_config_no_change", presetId);
         }
      }
   }

   public static JsonObject getDefaultElementConfig(String presetId, String elementId) {
      return buildLanguageDefaults(elementId);
   }

   public static JsonObject getDefaultElementConfig(String name) {
      return buildLanguageDefaults(name);
   }

   private static JsonObject buildLanguageDefaults(String elementId) {
      JsonObject defaults = ElementConfigRegistry.buildElementDefaults(elementId);

      for (String key : defaults.keySet()) {
         if (key.startsWith("format_") || key.equals("kill_feed_format") || key.equals("best_text_format")) {
            defaults.addProperty(key, FormatDefaultsManager.getDefault(elementId, key));
         }
      }

      return defaults;
   }

   public static void resetConfig() {
      if (PRESETS_DIR.exists()) {
         File[] files = PRESETS_DIR.listFiles();
         if (files != null) {
            for (File file : files) {
               if (!file.delete()) {
                  ClientMessageLogger.chatError("battlefield_ops.client.config.element.delete_old_fail_occupied");
                  ClientMessageLogger.error("battlefield_ops.client.config.element.delete_old_fail", file.getName());
               }
            }
         }
      }

      createDefaultConfig();
   }

   public static void resetOfficialPreset(String presetId) {
      if (!isOfficialPresetId(presetId)) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.reset_unofficial_fail", presetId);
      } else {
         ElementConfigManager.ElementPreset preset = loadOfficialPresetFromJar(presetId);
         if (preset == null) {
            ClientMessageLogger.chatError("battlefield_ops.client.config.element.reset_preset_fail", presetId);
         } else {
            getActivePresets().put(presetId, preset);
            if (!isEditing) {
               saveConfig();
               ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.reset_official_success", presetId);
            }
         }
      }
   }

   public static void clearPresetElements(String presetId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset == null) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.preset_not_found", presetId);
      } else {
         preset.elementConfigs.clear();
         if (!isEditing) {
            saveConfig();
            ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.preset_cleared", presetId);
         }
      }
   }

   public static void saveConfig() {
      for (Entry<String, ElementConfigManager.ElementPreset> entry : PRESETS.entrySet()) {
         writePresetFile(new File(PRESETS_DIR, entry.getKey() + ".json"), entry.getValue());
      }

      File[] files = PRESETS_DIR.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
         for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 5);
            if (!PRESETS.containsKey(id)) {
               file.delete();
            }
         }
      }
   }

   private static void writePresetFile(File file, ElementConfigManager.ElementPreset preset) {
      JsonObject presetJson = new JsonObject();
      presetJson.addProperty("format_version", 1);
      if (preset.getDisplayName() != null && !preset.getDisplayName().isEmpty()) {
         presetJson.addProperty("display_name", preset.getDisplayName());
      }

      preset.elementConfigs.forEach(presetJson::add);

      try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
         GSON.toJson(presetJson, writer);
      } catch (IOException var8) {
         ClientMessageLogger.error("battlefield_ops.client.config.element.save_fail", var8.getMessage());
      }
   }

   public static JsonObject getElementConfig(String presetId, String elementId) {
      ElementConfigManager.ElementPreset preset = getPreset(presetId);
      return preset == null ? null : preset.getConfig(elementId);
   }

   public static JsonObject getResetDefaultConfig(String presetId, String elementId) {
      JsonObject languageDefaults = getDefaultElementConfig(elementId);
      if (!isOfficialPreset(presetId)) {
         return languageDefaults;
      } else {
         ElementConfigManager.ElementPreset jarPreset = loadOfficialPresetFromJar(presetId);
         if (jarPreset == null) {
            return languageDefaults;
         } else {
            JsonObject jarConfig = jarPreset.getConfig(elementId);
            if (jarConfig == null) {
               return languageDefaults;
            } else {
               JsonObject result = jarConfig.deepCopy();

               for (Entry<String, JsonElement> entry : languageDefaults.entrySet()) {
                  if (!result.has(entry.getKey())) {
                     result.add(entry.getKey(), entry.getValue());
                  }
               }

               return result;
            }
         }
      }
   }

   public static Set<String> getAllElementTypes() {
      return ElementConfigRegistry.getElements();
   }

   public static Set<String> getAvailableElementTypes(String presetId) {
      Set<String> all = new HashSet<>(getAllElementTypes());
      Set<String> existing = getElementIds(presetId);
      all.removeAll(existing);
      return all;
   }

   public static Set<String> getPresetIds() {
      return getActivePresets().keySet();
   }

   public static Set<String> getElementIds(String presetId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      return preset == null ? Collections.emptySet() : preset.elementConfigs.keySet();
   }

   public static Set<String> getConfigKeys(String presetId, String elementId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset == null) {
         return Collections.emptySet();
      } else {
         JsonObject config = preset.getConfig(elementId);
         if (config == null) {
            return Collections.emptySet();
         } else {
            Set<String> keys = new HashSet<>(config.keySet());
            keys.addAll(ElementConfigRegistry.getKeys(elementId));
            return keys;
         }
      }
   }

   public static void addElementToPreset(String presetId, String elementId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset == null) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.preset_not_found", presetId);
      } else if (preset.getConfig(elementId) != null) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.element_exists", elementId);
      } else {
         preset.addElementConfig(elementId, getDefaultElementConfig(elementId));
         if (!isEditing) {
            saveConfig();
            ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.element_added", elementId, presetId);
         }
      }
   }

   public static void removeElementFromPreset(String presetId, String elementId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset == null) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.preset_not_found", presetId);
      } else if (preset.getConfig(elementId) == null) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.element.element_not_found", elementId);
      } else {
         preset.elementConfigs.remove(elementId);
         if (!isEditing) {
            saveConfig();
            ClientMessageLogger.chatSuccess("battlefield_ops.client.config.element.element_removed", elementId, presetId);
         }
      }
   }

   public static boolean isOfficialPresetId(String presetId) {
      return DefaultConfigRegistry.isOfficialPreset(presetId);
   }

   public static String getPresetDisplayName(String presetId) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      return preset != null ? preset.getDisplayName() : "";
   }

   public static void setPresetDisplayName(String presetId, String displayName) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset != null) {
         preset.setDisplayName(displayName);
         if (!isEditing) {
            saveConfig();
         }
      }
   }

   public static void createPreset(String presetId) {
      if (!getActivePresets().containsKey(presetId) && !isOfficialPreset(presetId)) {
         ElementConfigManager.ElementPreset preset = new ElementConfigManager.ElementPreset();
         String defaultNameKey = "battlefield_ops.client.config.preset.new_preset_default_name";
         preset.setDisplayName(I18nCompat.exists(defaultNameKey) ? I18n.get(defaultNameKey, new Object[0]) : "New Custom Preset");
         getActivePresets().put(presetId, preset);
         ensurePresetAssets(presetId);
         if (!isEditing) {
            saveConfig();
         }
      }
   }

   public static void ensurePresetAssets(String presetId) {
      ExternalTextureManager.ensureTextureFilesForPreset(presetId);
      ExternalSoundManager.ensureSoundFilesForPreset(presetId);
   }

   public static void setElementConfig(String presetId, String elementId, JsonObject config) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset != null) {
         preset.addElementConfig(elementId, config);
         if (!isEditing) {
            saveConfig();
         }
      }
   }

   public static void updateConfigValue(String presetId, String elementId, String key, String value) {
      ElementConfigManager.ElementPreset preset = getActivePresets().get(presetId);
      if (preset != null) {
         JsonObject config = preset.getConfig(elementId);
         if (config != null) {
            JsonObject defaultConfig = getDefaultElementConfig(presetId, elementId);
            if (defaultConfig != null && defaultConfig.has(key)) {
               JsonElement defaultVal = defaultConfig.get(key);
               if (defaultVal.isJsonPrimitive()) {
                  if (defaultVal.getAsJsonPrimitive().isBoolean()) {
                     config.addProperty(key, Boolean.parseBoolean(value));
                  } else if (defaultVal.getAsJsonPrimitive().isNumber()) {
                     try {
                        config.addProperty(key, Double.parseDouble(value));
                     } catch (NumberFormatException var9) {
                     }
                  } else {
                     config.addProperty(key, value);
                  }
               } else {
                  config.addProperty(key, value);
               }
            } else {
               config.addProperty(key, value);
            }

            if (!isEditing) {
               saveConfig();
            }
         }
      }
   }

   public static class ElementPreset {
      private final Map<String, JsonObject> elementConfigs = new HashMap<>();
      private String displayName = "";

      public void addElementConfig(String key, JsonObject config) {
         this.elementConfigs.put(key, config);
      }

      public Map<String, JsonObject> getElementConfigs() {
         return this.elementConfigs;
      }

      public JsonObject getConfig(String key) {
         return this.elementConfigs.get(key);
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public void setDisplayName(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            ElementConfigManager.ElementPreset that = (ElementConfigManager.ElementPreset)o;
            return Objects.equals(this.displayName, that.displayName) && Objects.equals(this.elementConfigs, that.elementConfigs);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.elementConfigs, this.displayName);
      }
   }
}
