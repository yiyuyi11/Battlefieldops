package cn.bfops.killicon.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.client.util.ConfigFileUtil;

public class ClientConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File CONFIG_DIR = ClientBridge.loader().getConfigDir().resolve("battlefield_ops").toFile();
   private static final File GLOBAL_CONFIG_FILE = new File(CONFIG_DIR, "client_config.json");
   private static final String DEFAULT_CURRENT_PRESET = "00036";
   private static final boolean DEFAULT_ENABLE_SOUND = true;
   private static final boolean DEFAULT_SHOW_BONUS_MESSAGE = false;
   private static final int DEFAULT_SOUND_VOLUME = 100;
   private static final boolean DEFAULT_ENABLE_ACE_LAG = false;
   private static final int DEFAULT_ACE_LAG_INTENSITY = 5;
   private static final boolean DEFAULT_DISABLE_TACZ_KILL_SOUND = false;
   private static final boolean DEFAULT_ENABLE_ICON_ANTIALIASING = true;
   private static final boolean DEFAULT_SHOW_CONFIG_INTRO = true;
   private static final boolean DEFAULT_SHOW_PRESET_INTRO = true;
   private static final boolean DEFAULT_SHOW_ELEMENT_INTRO = true;
   private static final boolean DEFAULT_SHOW_SOUND_INTRO = true;
   private static final boolean DEFAULT_SHOW_SCOREBOARD_INTRO = true;
   private static final boolean DEFAULT_ENABLE_ELEMENT_PREVIEW_SOUND = false;
   private static final int DEFAULT_PRESET_TUTORIAL_STEP = 1;
   private static final String DEFAULT_LAST_LANGUAGE = "";
   private static final String DEFAULT_SINGLE_LINE_SUBTITLE_COMPRESSION_MODE = "scroll";
   private static final String DEFAULT_GUI_THEME_COLOR_PRIMARY = "#FFB840";
   private static final String DEFAULT_GUI_BACKGROUND_MATERIAL = "minecraft:cut_copper";
   private static final int DEFAULT_BONUS_FORMAT_MIGRATED = 0;
   private static String currentPresetId = "00036";
   private static boolean enableSound = true;
   private static boolean showBonusMessage = false;
   private static int soundVolume = 100;
   private static boolean enableAceLag = false;
   private static int aceLagIntensity = 5;
   private static boolean disableTaczKillSound = false;
   private static boolean enableIconAntialiasing = true;
   private static boolean showConfigIntro = true;
   private static boolean showPresetIntro = true;
   private static boolean showElementIntro = true;
   private static boolean showSoundIntro = true;
   private static boolean showSoundSelectIntro = true;
   private static boolean showScoreboardIntro = true;
   private static boolean enableElementPreviewSound = false;
   private static int presetTutorialStep = 1;
   private static String lastLanguageCode = "";
   private static String lastModVersion = "";
   private static String singleLineSubtitleCompressionMode = "scroll";
   private static String guiThemeColorPrimary = "#FFB840";
   private static String guiBackgroundMaterial = "minecraft:cut_copper";
   private static int bonusFormatMigrated = 0;
   private static String tempCurrentPresetId = null;
   private static Boolean tempEnableSound = null;
   private static Boolean tempShowBonusMessage = null;
   private static Integer tempSoundVolume = null;
   private static Boolean tempEnableAceLag = null;
   private static Integer tempAceLagIntensity = null;
   private static Boolean tempDisableTaczKillSound = null;
   private static Boolean tempEnableIconAntialiasing = null;
   private static String tempSingleLineSubtitleCompressionMode = null;
   private static String tempGuiThemeColorPrimary = null;
   private static String tempGuiBackgroundMaterial = null;
   private static boolean isEditing = false;

   public static String getInitialPresetByLanguage() {
      try {
         String lang = Minecraft.getInstance().options.languageCode;
         if ("zh_cn".equals(lang) || "zh_tw".equals(lang) || "lzh".equals(lang)) {
            return "00007";
         }
      } catch (Exception var1) {
      }

      return "00036";
   }

   public static void startEditing() {
      tempCurrentPresetId = currentPresetId;
      tempEnableSound = enableSound;
      tempShowBonusMessage = showBonusMessage;
      tempSoundVolume = soundVolume;
      tempEnableAceLag = enableAceLag;
      tempAceLagIntensity = aceLagIntensity;
      tempDisableTaczKillSound = disableTaczKillSound;
      tempEnableIconAntialiasing = enableIconAntialiasing;
      tempSingleLineSubtitleCompressionMode = singleLineSubtitleCompressionMode;
      tempGuiThemeColorPrimary = guiThemeColorPrimary;
      tempGuiBackgroundMaterial = guiBackgroundMaterial;
      isEditing = true;
   }

   public static void saveChanges() {
      if (isEditing) {
         currentPresetId = tempCurrentPresetId;
         enableSound = tempEnableSound;
         showBonusMessage = tempShowBonusMessage;
         soundVolume = tempSoundVolume == null ? soundVolume : clampSoundVolume(tempSoundVolume);
         enableAceLag = tempEnableAceLag != null ? tempEnableAceLag : enableAceLag;
         aceLagIntensity = tempAceLagIntensity == null ? aceLagIntensity : clampAceLagIntensity(tempAceLagIntensity);
         disableTaczKillSound = tempDisableTaczKillSound != null ? tempDisableTaczKillSound : disableTaczKillSound;
         enableIconAntialiasing = tempEnableIconAntialiasing != null ? tempEnableIconAntialiasing : enableIconAntialiasing;
         singleLineSubtitleCompressionMode = tempSingleLineSubtitleCompressionMode == null
            ? singleLineSubtitleCompressionMode
            : normalizeSingleLineSubtitleCompressionMode(tempSingleLineSubtitleCompressionMode);
         guiThemeColorPrimary = tempGuiThemeColorPrimary == null ? guiThemeColorPrimary : normalizeHexColor(tempGuiThemeColorPrimary, "#FFB840");
         guiBackgroundMaterial = tempGuiBackgroundMaterial == null ? guiBackgroundMaterial : normalizeGuiBackgroundMaterial(tempGuiBackgroundMaterial);
         isEditing = false;
         applyGuiThemeColors();
         // The editing path defers the switch, so the textures only learn about it on commit.
         cn.bfops.killicon.client.textures.ExternalTextureManager.reapplyTextureFiltering();
         saveGlobalConfig();
         tempCurrentPresetId = null;
         tempEnableSound = null;
         tempShowBonusMessage = null;
         tempSoundVolume = null;
         tempEnableAceLag = null;
         tempAceLagIntensity = null;
         tempDisableTaczKillSound = null;
         tempEnableIconAntialiasing = null;
         tempSingleLineSubtitleCompressionMode = null;
         tempGuiThemeColorPrimary = null;
         tempGuiBackgroundMaterial = null;
      }
   }

   public static void discardChanges() {
      if (isEditing) {
         isEditing = false;
         tempCurrentPresetId = null;
         tempEnableSound = null;
         tempShowBonusMessage = null;
         tempSoundVolume = null;
         tempEnableAceLag = null;
         tempAceLagIntensity = null;
         tempDisableTaczKillSound = null;
         tempEnableIconAntialiasing = null;
         tempSingleLineSubtitleCompressionMode = null;
         tempGuiThemeColorPrimary = null;
         tempGuiBackgroundMaterial = null;
         applyGuiThemeColors();
      }
   }

   public static boolean hasUnsavedChanges() {
      if (!isEditing) {
         return false;
      } else if (tempCurrentPresetId != null && !tempCurrentPresetId.equals(currentPresetId)) {
         return true;
      } else if (tempEnableSound != null && !tempEnableSound.equals(enableSound)) {
         return true;
      } else if (tempShowBonusMessage != null && !tempShowBonusMessage.equals(showBonusMessage)) {
         return true;
      } else if (tempSoundVolume != null && tempSoundVolume != soundVolume) {
         return true;
      } else if (tempEnableAceLag != null && !tempEnableAceLag.equals(enableAceLag)) {
         return true;
      } else if (tempAceLagIntensity != null && tempAceLagIntensity != aceLagIntensity) {
         return true;
      } else if (tempDisableTaczKillSound != null && !tempDisableTaczKillSound.equals(disableTaczKillSound)) {
         return true;
      } else if (tempEnableIconAntialiasing != null && !tempEnableIconAntialiasing.equals(enableIconAntialiasing)) {
         return true;
      } else if (tempSingleLineSubtitleCompressionMode != null && !tempSingleLineSubtitleCompressionMode.equals(singleLineSubtitleCompressionMode)) {
         return true;
      } else {
         return tempGuiThemeColorPrimary != null && !tempGuiThemeColorPrimary.equals(guiThemeColorPrimary)
            ? true
            : tempGuiBackgroundMaterial != null && !tempGuiBackgroundMaterial.equals(guiBackgroundMaterial);
      }
   }

   public static boolean isAceLagConfigChangedInEdit() {
      if (!isEditing) {
         return false;
      } else {
         return tempEnableAceLag != null && !tempEnableAceLag.equals(enableAceLag)
            ? true
            : tempAceLagIntensity != null && tempAceLagIntensity != aceLagIntensity;
      }
   }

   public static void init() {
      if (!CONFIG_DIR.exists()) {
         CONFIG_DIR.mkdirs();
      }

      loadGlobalConfig();
   }

   public static void loadGlobalConfig() {
      if (!GLOBAL_CONFIG_FILE.exists()) {
         createDefaultConfig();
      } else {
         try {
            JsonObject json = (JsonObject)GSON.fromJson(ConfigFileUtil.readText(GLOBAL_CONFIG_FILE), JsonObject.class);
            currentPresetId = json.has("current_preset") ? json.get("current_preset").getAsString() : getInitialPresetByLanguage();
            enableSound = json.has("enable_sound") ? json.get("enable_sound").getAsBoolean() : true;
            showBonusMessage = json.has("show_bonus_message") ? json.get("show_bonus_message").getAsBoolean() : false;
            soundVolume = json.has("sound_volume") ? clampSoundVolume(json.get("sound_volume").getAsInt()) : 100;
            enableAceLag = json.has("enable_ace_lag") ? json.get("enable_ace_lag").getAsBoolean() : false;
            aceLagIntensity = json.has("ace_lag_intensity") ? clampAceLagIntensity(json.get("ace_lag_intensity").getAsInt()) : 5;
            disableTaczKillSound = json.has("disable_tacz_kill_sound") ? json.get("disable_tacz_kill_sound").getAsBoolean() : false;
            enableIconAntialiasing = json.has("enable_icon_antialiasing") ? json.get("enable_icon_antialiasing").getAsBoolean() : true;
            showConfigIntro = json.has("show_config_intro") ? json.get("show_config_intro").getAsBoolean() : true;
            showPresetIntro = json.has("show_preset_intro") ? json.get("show_preset_intro").getAsBoolean() : true;
            showElementIntro = json.has("show_element_intro") ? json.get("show_element_intro").getAsBoolean() : true;
            showSoundIntro = json.has("show_sound_intro") ? json.get("show_sound_intro").getAsBoolean() : true;
            showSoundSelectIntro = json.has("show_sound_select_intro") ? json.get("show_sound_select_intro").getAsBoolean() : true;
            showScoreboardIntro = json.has("show_scoreboard_intro") ? json.get("show_scoreboard_intro").getAsBoolean() : true;
            enableElementPreviewSound = json.has("enable_element_preview_sound") ? json.get("enable_element_preview_sound").getAsBoolean() : false;
            presetTutorialStep = json.has("preset_tutorial_step") ? json.get("preset_tutorial_step").getAsInt() : 1;
            if (presetTutorialStep < 1) {
               presetTutorialStep = 1;
            }

            lastLanguageCode = json.has("last_language") ? json.get("last_language").getAsString() : "";
            lastModVersion = json.has("last_mod_version") ? json.get("last_mod_version").getAsString() : "";
            singleLineSubtitleCompressionMode = json.has("single_line_subtitle_compression_mode")
               ? normalizeSingleLineSubtitleCompressionMode(json.get("single_line_subtitle_compression_mode").getAsString())
               : "scroll";
            guiThemeColorPrimary = json.has("gui_theme_color_primary")
               ? normalizeHexColor(json.get("gui_theme_color_primary").getAsString(), "#FFB840")
               : "#FFB840";
            guiBackgroundMaterial = json.has("gui_background_material")
               ? normalizeGuiBackgroundMaterial(json.get("gui_background_material").getAsString())
               : "minecraft:cut_copper";
            bonusFormatMigrated = json.has("bonus_format_version") ? json.get("bonus_format_version").getAsInt() : 0;
            applyGuiThemeColors();
         } catch (Exception var1) {
            ClientMessageLogger.error("battlefield_ops.client.config.load_fail", var1.getMessage());
            currentPresetId = getInitialPresetByLanguage();
            enableSound = true;
            showBonusMessage = false;
            soundVolume = 100;
            enableAceLag = false;
            aceLagIntensity = 5;
            disableTaczKillSound = false;
            enableIconAntialiasing = true;
            showConfigIntro = true;
            showPresetIntro = true;
            showElementIntro = true;
            showSoundIntro = true;
            showSoundSelectIntro = true;
            showScoreboardIntro = true;
            enableElementPreviewSound = false;
            presetTutorialStep = 1;
            lastLanguageCode = "";
            lastModVersion = "";
            singleLineSubtitleCompressionMode = "scroll";
            guiThemeColorPrimary = "#FFB840";
            guiBackgroundMaterial = "minecraft:cut_copper";
            applyGuiThemeColors();
         }
      }
   }

   public static void createDefaultConfig() {
      JsonObject json = new JsonObject();
      json.addProperty("current_preset", getInitialPresetByLanguage());
      json.addProperty("enable_sound", true);
      json.addProperty("show_bonus_message", false);
      json.addProperty("sound_volume", 100);
      json.addProperty("enable_ace_lag", false);
      json.addProperty("ace_lag_intensity", 5);
      json.addProperty("disable_tacz_kill_sound", false);
      json.addProperty("enable_icon_antialiasing", true);
      json.addProperty("show_config_intro", true);
      json.addProperty("show_preset_intro", true);
      json.addProperty("show_element_intro", true);
      json.addProperty("show_sound_intro", true);
      json.addProperty("show_sound_select_intro", true);
      json.addProperty("show_scoreboard_intro", true);
      json.addProperty("enable_element_preview_sound", false);
      json.addProperty("preset_tutorial_step", 1);
      json.addProperty("last_language", "");
      json.addProperty("last_mod_version", "Forge-1.20.1-1.3.0Alpha");
      json.addProperty("single_line_subtitle_compression_mode", "scroll");
      json.addProperty("gui_theme_color_primary", "#FFB840");
      json.addProperty("gui_background_material", "minecraft:cut_copper");
      json.addProperty("bonus_format_version", 0);
      currentPresetId = getInitialPresetByLanguage();
      enableSound = true;
      showBonusMessage = false;
      soundVolume = 100;
      enableAceLag = false;
      aceLagIntensity = 5;
      disableTaczKillSound = false;
      enableIconAntialiasing = true;
      showConfigIntro = true;
      showPresetIntro = true;
      showElementIntro = true;
      showSoundIntro = true;
      showSoundSelectIntro = true;
      showScoreboardIntro = true;
      enableElementPreviewSound = false;
      presetTutorialStep = 1;
      lastLanguageCode = "";
      lastModVersion = "Forge-1.20.1-1.3.0Alpha";
      singleLineSubtitleCompressionMode = "scroll";
      guiThemeColorPrimary = "#FFB840";
      guiBackgroundMaterial = "minecraft:cut_copper";
      applyGuiThemeColors();

      try (FileWriter writer = new FileWriter(GLOBAL_CONFIG_FILE, StandardCharsets.UTF_8)) {
         GSON.toJson(json, writer);
      } catch (IOException var6) {
         ClientMessageLogger.error("battlefield_ops.client.config.save_fail", var6.getMessage());
      }
   }

   public static void saveGlobalConfig() {
      JsonObject root = new JsonObject();
      root.addProperty("current_preset", currentPresetId);
      root.addProperty("enable_sound", enableSound);
      root.addProperty("show_bonus_message", showBonusMessage);
      root.addProperty("sound_volume", soundVolume);
      root.addProperty("enable_ace_lag", enableAceLag);
      root.addProperty("ace_lag_intensity", aceLagIntensity);
      root.addProperty("disable_tacz_kill_sound", disableTaczKillSound);
      root.addProperty("enable_icon_antialiasing", enableIconAntialiasing);
      root.addProperty("show_config_intro", showConfigIntro);
      root.addProperty("show_preset_intro", showPresetIntro);
      root.addProperty("show_element_intro", showElementIntro);
      root.addProperty("show_sound_intro", showSoundIntro);
      root.addProperty("show_sound_select_intro", showSoundSelectIntro);
      root.addProperty("show_scoreboard_intro", showScoreboardIntro);
      root.addProperty("enable_element_preview_sound", enableElementPreviewSound);
      root.addProperty("preset_tutorial_step", presetTutorialStep);
      root.addProperty("last_language", lastLanguageCode);
      root.addProperty("last_mod_version", lastModVersion);
      root.addProperty("single_line_subtitle_compression_mode", singleLineSubtitleCompressionMode);
      root.addProperty("gui_theme_color_primary", guiThemeColorPrimary);
      root.addProperty("gui_background_material", guiBackgroundMaterial);
      root.addProperty("bonus_format_version", bonusFormatMigrated);

      try (FileWriter writer = new FileWriter(GLOBAL_CONFIG_FILE, StandardCharsets.UTF_8)) {
         GSON.toJson(root, writer);
      } catch (IOException var6) {
         ClientMessageLogger.error("battlefield_ops.client.config.save_fail", var6.getMessage());
      }
   }

   public static void resetToDefaults() {
      createDefaultConfig();
   }

   public static boolean checkLanguageChangedAndUpdate(String currentLanguage) {
      if (currentLanguage == null || currentLanguage.isEmpty()) {
         return false;
      } else if (lastLanguageCode == null || lastLanguageCode.isEmpty()) {
         lastLanguageCode = currentLanguage;
         saveGlobalConfig();
         return false;
      } else if (!currentLanguage.equals(lastLanguageCode)) {
         lastLanguageCode = currentLanguage;
         saveGlobalConfig();
         return true;
      } else {
         return false;
      }
   }

   public static String getCurrentPresetId() {
      return isEditing && tempCurrentPresetId != null ? tempCurrentPresetId : currentPresetId;
   }

   public static void setCurrentPresetId(String id) {
      if (isEditing) {
         tempCurrentPresetId = id;
      } else {
         currentPresetId = id;
         saveGlobalConfig();
      }
   }

   public static boolean isEnableSound() {
      return isEditing && tempEnableSound != null ? tempEnableSound : enableSound;
   }

   public static void setEnableSound(boolean enable) {
      if (isEditing) {
         tempEnableSound = enable;
      } else {
         enableSound = enable;
         saveGlobalConfig();
      }
   }

   public static boolean isShowBonusMessage() {
      return isEditing && tempShowBonusMessage != null ? tempShowBonusMessage : showBonusMessage;
   }

   public static void setShowBonusMessage(boolean show) {
      if (isEditing) {
         tempShowBonusMessage = show;
      } else {
         showBonusMessage = show;
         saveGlobalConfig();
      }
   }

   public static int getSoundVolume() {
      return isEditing && tempSoundVolume != null ? tempSoundVolume : soundVolume;
   }

   public static void setSoundVolume(int volume) {
      int clamped = clampSoundVolume(volume);
      if (isEditing) {
         tempSoundVolume = clamped;
      } else {
         soundVolume = clamped;
         saveGlobalConfig();
      }
   }

   public static boolean isElementPreviewSoundEnabled() {
      return enableElementPreviewSound;
   }

   public static void setElementPreviewSoundEnabled(boolean enabled) {
      enableElementPreviewSound = enabled;
      saveGlobalConfig();
   }

   public static boolean isEnableAceLag() {
      return isEditing && tempEnableAceLag != null ? tempEnableAceLag : enableAceLag;
   }

   public static void setEnableAceLag(boolean enable) {
      if (isEditing) {
         tempEnableAceLag = enable;
      } else {
         enableAceLag = enable;
         saveGlobalConfig();
      }
   }

   public static int getAceLagIntensity() {
      return isEditing && tempAceLagIntensity != null ? tempAceLagIntensity : aceLagIntensity;
   }

   public static void setAceLagIntensity(int intensity) {
      int clamped = clampAceLagIntensity(intensity);
      if (isEditing) {
         tempAceLagIntensity = clamped;
      } else {
         aceLagIntensity = clamped;
         saveGlobalConfig();
      }
   }

   public static boolean isDisableTaczKillSound() {
      return isEditing && tempDisableTaczKillSound != null ? tempDisableTaczKillSound : disableTaczKillSound;
   }

   public static void setDisableTaczKillSound(boolean disable) {
      if (isEditing) {
         tempDisableTaczKillSound = disable;
      } else {
         disableTaczKillSound = disable;
         saveGlobalConfig();
      }
   }

   public static boolean isEnableIconAntialiasing() {
      return isEditing && tempEnableIconAntialiasing != null ? tempEnableIconAntialiasing : enableIconAntialiasing;
   }

   public static void setEnableIconAntialiasing(boolean enable) {
      if (isEditing) {
         tempEnableIconAntialiasing = enable;
      } else {
         enableIconAntialiasing = enable;
         // Filtering is baked into each texture the first time it is resolved, so existing textures must be
         // told about the new mode or the switch does nothing until the preset is reloaded.
         cn.bfops.killicon.client.textures.ExternalTextureManager.reapplyTextureFiltering();
         saveGlobalConfig();
      }
   }

   public static String getSingleLineSubtitleCompressionMode() {
      return isEditing && tempSingleLineSubtitleCompressionMode != null ? tempSingleLineSubtitleCompressionMode : singleLineSubtitleCompressionMode;
   }

   public static void setSingleLineSubtitleCompressionMode(String mode) {
      String normalized = normalizeSingleLineSubtitleCompressionMode(mode);
      if (isEditing) {
         tempSingleLineSubtitleCompressionMode = normalized;
      } else {
         singleLineSubtitleCompressionMode = normalized;
         saveGlobalConfig();
      }
   }

   public static String getGuiThemeColorPrimary() {
      return isEditing && tempGuiThemeColorPrimary != null ? tempGuiThemeColorPrimary : guiThemeColorPrimary;
   }

   public static void setGuiThemeColorPrimary(String color) {
      String normalized = normalizeHexColor(color, "#FFB840");
      if (isEditing) {
         tempGuiThemeColorPrimary = normalized;
      } else {
         guiThemeColorPrimary = normalized;
         saveGlobalConfig();
      }

      applyGuiThemeColors();
   }

   public static String getGuiThemeColorSecondary() {
      return deriveSecondaryThemeColor(getGuiThemeColorPrimary());
   }

   public static String getGuiThemeColorTertiary() {
      return deriveTertiaryThemeColor(getGuiThemeColorPrimary());
   }

   public static String getGuiBackgroundMaterial() {
      return isEditing && tempGuiBackgroundMaterial != null ? tempGuiBackgroundMaterial : guiBackgroundMaterial;
   }

   public static void setGuiBackgroundMaterial(String materialId) {
      String normalized = normalizeGuiBackgroundMaterial(materialId);
      if (isEditing) {
         tempGuiBackgroundMaterial = normalized;
      } else {
         guiBackgroundMaterial = normalized;
         saveGlobalConfig();
      }
   }

   public static int getBonusFormatMigrated() {
      return bonusFormatMigrated;
   }

   public static void setBonusFormatMigrated(int version) {
      bonusFormatMigrated = version;
      saveGlobalConfig();
   }

   public static boolean isGuiConfigChangedInEdit() {
      if (!isEditing) {
         return false;
      } else {
         return tempGuiThemeColorPrimary != null && !tempGuiThemeColorPrimary.equals(guiThemeColorPrimary)
            ? true
            : tempGuiBackgroundMaterial != null && !tempGuiBackgroundMaterial.equals(guiBackgroundMaterial);
      }
   }

   public static boolean shouldShowConfigIntro() {
      return showConfigIntro;
   }

   public static void markConfigIntroShown() {
      if (showConfigIntro) {
         showConfigIntro = false;
         saveGlobalConfig();
      }
   }

   public static boolean shouldShowPresetIntro() {
      return showPresetIntro;
   }

   public static void markPresetIntroShown() {
      if (showPresetIntro) {
         showPresetIntro = false;
         saveGlobalConfig();
      }
   }

   public static boolean shouldRunPresetTutorial() {
      return showPresetIntro && presetTutorialStep <= 8;
   }

   public static int getPresetTutorialStep() {
      return presetTutorialStep;
   }

   public static void setPresetTutorialStep(int step) {
      int next = Math.max(1, step);
      if (next > 8) {
         presetTutorialStep = 9;
         showPresetIntro = false;
      } else {
         presetTutorialStep = next;
      }

      saveGlobalConfig();
   }

   public static boolean shouldShowElementIntro() {
      return showElementIntro;
   }

   public static void markElementIntroShown() {
      if (showElementIntro) {
         showElementIntro = false;
         saveGlobalConfig();
      }
   }

   public static boolean shouldShowSoundIntro() {
      return showSoundIntro;
   }

   public static void markSoundIntroShown() {
      if (showSoundIntro) {
         showSoundIntro = false;
         saveGlobalConfig();
      }
   }

   public static boolean shouldShowSoundSelectIntro() {
      return showSoundSelectIntro;
   }

   public static void markSoundSelectIntroShown() {
      if (showSoundSelectIntro) {
         showSoundSelectIntro = false;
         saveGlobalConfig();
      }
   }

   public static boolean shouldShowScoreboardIntro() {
      return showScoreboardIntro;
   }

   public static void markScoreboardIntroShown() {
      if (showScoreboardIntro) {
         showScoreboardIntro = false;
         saveGlobalConfig();
      }
   }

   public static void resetIntroPrompts() {
      showConfigIntro = true;
      showPresetIntro = true;
      showElementIntro = true;
      showSoundIntro = true;
      showSoundSelectIntro = true;
      showScoreboardIntro = true;
      presetTutorialStep = 1;
      saveGlobalConfig();
   }

   public static boolean checkModVersionChangedAndUpdate(String currentVersion) {
      if (currentVersion == null || currentVersion.isEmpty()) {
         return false;
      } else if (lastModVersion == null || lastModVersion.isEmpty()) {
         lastModVersion = currentVersion;
         saveGlobalConfig();
         return true;
      } else if (!currentVersion.equals(lastModVersion)) {
         lastModVersion = currentVersion;
         saveGlobalConfig();
         return true;
      } else {
         return false;
      }
   }

   public static void setRecordedModVersion(String version) {
      lastModVersion = version == null ? "" : version;
      saveGlobalConfig();
   }

   private static int clampSoundVolume(int volume) {
      return Math.max(0, Math.min(200, volume));
   }

   private static int clampAceLagIntensity(int intensity) {
      return Math.max(1, Math.min(100, intensity));
   }

   private static String normalizeSingleLineSubtitleCompressionMode(String mode) {
      return "ellipsis".equalsIgnoreCase(mode) ? "ellipsis" : "scroll";
   }

   private static String normalizeHexColor(String color, String fallback) {
      if (color == null) {
         return fallback;
      } else {
         String trimmed = color.trim();
         return !trimmed.matches("^#[0-9A-Fa-f]{6}$") ? fallback : "#" + trimmed.substring(1).toUpperCase();
      }
   }

   private static String normalizeGuiBackgroundMaterial(String materialId) {
      if (materialId == null) {
         return "minecraft:cut_copper";
      } else {
         String trimmed = materialId.trim();
         return !trimmed.isEmpty() && trimmed.contains(":") ? trimmed.toLowerCase() : "minecraft:cut_copper";
      }
   }

   private static int parseRgbHexToArgb(String color, int fallback) {
      String normalized = normalizeHexColor(color, null);
      if (normalized == null) {
         return fallback;
      } else {
         try {
            int rgb = Integer.parseInt(normalized.substring(1), 16) & 16777215;
            return 0xFF000000 | rgb;
         } catch (Exception var4) {
            return fallback;
         }
      }
   }

   private static String deriveSecondaryThemeColor(String primary) {
      return scaleThemeColor(primary, 0.9490196F, 0.8423913F, 0.953125F, "#F29B3D");
   }

   private static String deriveTertiaryThemeColor(String primary) {
      return scaleThemeColor(primary, 0.8941176F, 0.8369565F, 0.4375F, "#E49A1C");
   }

   private static String scaleThemeColor(String primary, float rScale, float gScale, float bScale, String fallback) {
      String normalized = normalizeHexColor(primary, null);
      if (normalized == null) {
         return fallback;
      } else {
         int r = Integer.parseInt(normalized.substring(1, 3), 16);
         int g = Integer.parseInt(normalized.substring(3, 5), 16);
         int b = Integer.parseInt(normalized.substring(5, 7), 16);
         int rr = Math.max(0, Math.min(255, Math.round((float)r * rScale)));
         int gg = Math.max(0, Math.min(255, Math.round((float)g * gScale)));
         int bb = Math.max(0, Math.min(255, Math.round((float)b * bScale)));
         return String.format("#%02X%02X%02X", rr, gg, bb);
      }
   }

   private static void applyGuiThemeColors() {
      GuiConstants.COLOR_GOLD = parseRgbHexToArgb(getGuiThemeColorPrimary(), -10496);
      GuiConstants.COLOR_GOLD_ORANGE = parseRgbHexToArgb(getGuiThemeColorSecondary(), -23296);
      GuiConstants.COLOR_DARK_GOLD_ORANGE = parseRgbHexToArgb(getGuiThemeColorTertiary(), -3374080);
      GuiConstants.COLOR_BLACK = -12303292;
   }
}
