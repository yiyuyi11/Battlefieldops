package cn.bfops.killicon.client.sounds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.FloatControl.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ValorantStyleCatalog;
import cn.bfops.killicon.client.util.ClientMessageLogger;

public class ExternalSoundManager {
   private static final Path CONFIG_ASSETS_DIR = ClientBridge.loader().getConfigDir().resolve("battlefield_ops/assets");
   private static final Path COMMON_SOUNDS_DIR = CONFIG_ASSETS_DIR.resolve("common").resolve("sounds");
   private static final String SOUND_SELECTION_FILE = "sound_selection.json";
   private static final String CUSTOM_SOUND_LABELS_FILE = "custom_sound_labels.json";
   private static final String CUSTOM_SOUND_PREFIX = "custom_";
   private static final Map<String, ExternalSoundManager.SoundData> SOUND_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Long> SOUND_LAST_PLAY_TIMES = new ConcurrentHashMap<>();
   private static final Map<String, Boolean> BLOCKING_STATUS = new ConcurrentHashMap<>();
   private static final ExecutorService SOUND_IO_THREAD_POOL = Executors.newCachedThreadPool(createThreadFactory("gd656-sound-io"));
   private static final ExecutorService SOUND_PLAYBACK_THREAD_POOL = Executors.newCachedThreadPool(createThreadFactory("gd656-sound-play"));
   private static final long SOUND_COOLDOWN = 10L;
   private static final String[] DEFAULT_SOUNDS = new String[]{
      "combokillsound_1_cf.ogg",
      "combokillsound_2_cf.ogg",
      "combokillsound_3_cf.ogg",
      "combokillsound_4_cf.ogg",
      "combokillsound_5_cf.ogg",
      "combokillsound_6_cf.ogg",
      "valorant_primekill_1.ogg",
      "valorant_primekill_2.ogg",
      "valorant_primekill_3.ogg",
      "valorant_primekill_4.ogg",
      "valorant_primekill_5.ogg",
      "valorant_glitchpopkill_1.ogg",
      "valorant_glitchpopkill_2.ogg",
      "valorant_glitchpopkill_3.ogg",
      "valorant_glitchpopkill_4.ogg",
      "valorant_glitchpopkill_5.ogg",
      "valorant_singularitykill_1.ogg",
      "valorant_singularitykill_2.ogg",
      "valorant_singularitykill_3.ogg",
      "valorant_singularitykill_4.ogg",
      "valorant_singularitykill_5.ogg",
      "valorant_gaiavengeancekill_1.ogg",
      "valorant_gaiavengeancekill_2.ogg",
      "valorant_gaiavengeancekill_3.ogg",
      "valorant_gaiavengeancekill_4.ogg",
      "valorant_gaiavengeancekill_5.ogg",
      "valorant_bubblegumdeathwishkill_1.ogg",
      "valorant_bubblegumdeathwishkill_2.ogg",
      "valorant_bubblegumdeathwishkill_3.ogg",
      "valorant_bubblegumdeathwishkill_4.ogg",
      "valorant_bubblegumdeathwishkill_5.ogg",
      "valorant_champions2021kill_1.ogg",
      "valorant_champions2021kill_2.ogg",
      "valorant_champions2021kill_3.ogg",
      "valorant_champions2021kill_4.ogg",
      "valorant_champions2021kill_5.ogg",
      "valorant_preludetochaoskill_1.ogg",
      "valorant_preludetochaoskill_2.ogg",
      "valorant_preludetochaoskill_3.ogg",
      "valorant_preludetochaoskill_4.ogg",
      "valorant_preludetochaoskill_5.ogg",
      "valorant_primordiumkill_1.ogg",
      "valorant_primordiumkill_2.ogg",
      "valorant_primordiumkill_3.ogg",
      "valorant_primordiumkill_4.ogg",
      "valorant_primordiumkill_5.ogg",
      "valorant_radiantcrisis001kill_1.ogg",
      "valorant_radiantcrisis001kill_2.ogg",
      "valorant_radiantcrisis001kill_3.ogg",
      "valorant_radiantcrisis001kill_4.ogg",
      "valorant_radiantcrisis001kill_5.ogg",
      "valorant_rgx11zprokill_1.ogg",
      "valorant_rgx11zprokill_2.ogg",
      "valorant_rgx11zprokill_3.ogg",
      "valorant_rgx11zprokill_4.ogg",
      "valorant_rgx11zprokill_5.ogg",
      "valorant_headshot.ogg",
      "explosionkillsound_df.ogg",
      "headshotkillsound_df.ogg",
      "critkillsound_df.ogg",
      "hitsound_df.ogg",
      "killsound_df.ogg",
      "defaulticonsound_df.ogg",
      "cardkillsound_default_cs.ogg",
      "cardkillsound_headshot_cs.ogg",
      "cardkillsound_explosion_cs.ogg",
      "cardkillsound_crit_cs.ogg",
      "cardkillsound_armorheadshot_cs.ogg",
      "killsound_bf1.ogg",
      "headshotkillsound_bf1.ogg",
      "killsound_bf5.ogg",
      "headshotkillsound_bf5.ogg",
      "vehiclekillsound_bf5.ogg",
      "killsound_bf6.ogg",
      "headshotkillsound_bf6.ogg",
      "hitsound_bf6.ogg",
      "assistsound_bf6.ogg",
      "addscore_df.ogg",
      "honorkillsound_normal.ogg",
      "honorkillsound_high.ogg"
   };
   private static final Set<String> DEFAULT_SOUND_SET = new HashSet<>(Arrays.asList(DEFAULT_SOUNDS));
   private static final Map<String, Map<String, ExternalSoundManager.SoundBackup>> PENDING_SOUND_BACKUPS = new ConcurrentHashMap<>();
   private static final Map<String, byte[]> DEFAULT_SOUND_BYTES = new ConcurrentHashMap<>();
   private static final Map<String, Map<String, String>> SOUND_SELECTIONS = new ConcurrentHashMap<>();
   private static final Map<String, Map<String, String>> SOUND_LABELS = new ConcurrentHashMap<>();
   private static final Map<String, Set<String>> PENDING_CUSTOM_SOUNDS = new ConcurrentHashMap<>();
   private static final Set<String> PENDING_SOUND_RESETS = new HashSet<>();
   private static Map<String, Map<String, String>> TEMP_SOUND_SELECTIONS = null;
   private static Map<String, Map<String, String>> TEMP_SOUND_LABELS = null;
   private static boolean isEditing = false;
   public static final String SLOT_COMMON_SCORE = "common_score";
   public static final String SLOT_COMMON_HIT = "common_hit";
   public static final String SLOT_COMMON_HEADSHOT_HIT = "common_headshot_hit";
   public static final String SLOT_SCROLLING_DEFAULT = "scrolling_default";
   public static final String SLOT_SCROLLING_HEADSHOT = "scrolling_headshot";
   public static final String SLOT_SCROLLING_EXPLOSION = "scrolling_explosion";
   public static final String SLOT_SCROLLING_CRIT = "scrolling_crit";
   public static final String SLOT_SCROLLING_ASSIST = "scrolling_assist";
   public static final String SLOT_SCROLLING_VEHICLE = "scrolling_vehicle";
   public static final String SLOT_BF1_DEFAULT = "bf1_default";
   public static final String SLOT_BF1_HEADSHOT = "bf1_headshot";
   public static final String SLOT_CARD_DEFAULT = "card_default";
   public static final String SLOT_CARD_HEADSHOT = "card_headshot";
   public static final String SLOT_CARD_EXPLOSION = "card_explosion";
   public static final String SLOT_CARD_CRIT = "card_crit";
   public static final String SLOT_CARD_ARMOR_HEADSHOT = "card_armor_headshot";
   public static final String SLOT_COMBO_1 = "combo_1";
   public static final String SLOT_COMBO_2 = "combo_2";
   public static final String SLOT_COMBO_3 = "combo_3";
   public static final String SLOT_COMBO_4 = "combo_4";
   public static final String SLOT_COMBO_5 = "combo_5";
   public static final String SLOT_COMBO_6 = "combo_6";
   public static final String SLOT_VALORANT_1 = "valorant_1";
   public static final String SLOT_VALORANT_2 = "valorant_2";
   public static final String SLOT_VALORANT_3 = "valorant_3";
   public static final String SLOT_VALORANT_4 = "valorant_4";
   public static final String SLOT_VALORANT_5 = "valorant_5";
   public static final String SLOT_HONOR_NORMAL = "honor_normal";
   public static final String SLOT_HONOR_HIGH = "honor_high";
   private static final List<ExternalSoundManager.SoundSlotDefinition> SOUND_SLOT_DEFINITIONS = List.of(
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMMON, "battlefield_ops.client.gui.config.sound.subgroup.common.score", "common_score"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMMON, "battlefield_ops.client.gui.config.sound.subgroup.common.hit", "common_hit"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMMON, "battlefield_ops.client.gui.config.sound.subgroup.common.headshot", "common_headshot_hit"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.SCROLLING, "battlefield_ops.client.gui.config.sound.subgroup.scrolling.default", "scrolling_default"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.SCROLLING, "battlefield_ops.client.gui.config.sound.subgroup.scrolling.headshot", "scrolling_headshot"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.SCROLLING, "battlefield_ops.client.gui.config.sound.subgroup.scrolling.explosion", "scrolling_explosion"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.SCROLLING, "battlefield_ops.client.gui.config.sound.subgroup.scrolling.crit", "scrolling_crit"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.SCROLLING, "battlefield_ops.client.gui.config.sound.subgroup.scrolling.vehicle", "scrolling_vehicle"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.SCROLLING, "battlefield_ops.client.gui.config.sound.subgroup.scrolling.assist", "scrolling_assist"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.BATTLEFIELD1, "battlefield_ops.client.gui.config.sound.subgroup.bf1.default", "bf1_default"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.BATTLEFIELD1, "battlefield_ops.client.gui.config.sound.subgroup.bf1.headshot", "bf1_headshot"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.CARD, "battlefield_ops.client.gui.config.sound.subgroup.card.default", "card_default"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.CARD, "battlefield_ops.client.gui.config.sound.subgroup.card.headshot", "card_headshot"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.CARD, "battlefield_ops.client.gui.config.sound.subgroup.card.explosion", "card_explosion"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.CARD, "battlefield_ops.client.gui.config.sound.subgroup.card.crit", "card_crit"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.CARD, "battlefield_ops.client.gui.config.sound.subgroup.card.armor_headshot", "card_armor_headshot"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMBO, "battlefield_ops.client.gui.config.sound.subgroup.combo.1", "combo_1"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMBO, "battlefield_ops.client.gui.config.sound.subgroup.combo.2", "combo_2"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMBO, "battlefield_ops.client.gui.config.sound.subgroup.combo.3", "combo_3"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMBO, "battlefield_ops.client.gui.config.sound.subgroup.combo.4", "combo_4"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMBO, "battlefield_ops.client.gui.config.sound.subgroup.combo.5", "combo_5"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.COMBO, "battlefield_ops.client.gui.config.sound.subgroup.combo.6", "combo_6"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.VALORANT, "battlefield_ops.client.gui.config.sound.subgroup.valorant.1", "valorant_1"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.VALORANT, "battlefield_ops.client.gui.config.sound.subgroup.valorant.2", "valorant_2"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.VALORANT, "battlefield_ops.client.gui.config.sound.subgroup.valorant.3", "valorant_3"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.VALORANT, "battlefield_ops.client.gui.config.sound.subgroup.valorant.4", "valorant_4"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.VALORANT, "battlefield_ops.client.gui.config.sound.subgroup.valorant.5", "valorant_5"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.HONOR, "battlefield_ops.client.gui.config.sound.subgroup.honor.normal", "honor_normal"
      ),
      new ExternalSoundManager.SoundSlotDefinition(
         ExternalSoundManager.SoundElementGroup.HONOR, "battlefield_ops.client.gui.config.sound.subgroup.honor.high", "honor_high"
      )
   );
   private static final List<String> SOUND_SLOT_IDS = Arrays.asList(
      "common_score",
      "common_hit",
      "common_headshot_hit",
      "scrolling_default",
      "scrolling_headshot",
      "scrolling_explosion",
      "scrolling_crit",
      "scrolling_assist",
      "scrolling_vehicle",
      "bf1_default",
      "bf1_headshot",
      "card_default",
      "card_headshot",
      "card_explosion",
      "card_crit",
      "card_armor_headshot",
      "combo_1",
      "combo_2",
      "combo_3",
      "combo_4",
      "combo_5",
      "combo_6",
      "valorant_1",
      "valorant_2",
      "valorant_3",
      "valorant_4",
      "valorant_5"
   );
   private static final Map<String, Long> SOUND_START_TIMES = new ConcurrentHashMap<>();
   private static final Map<String, Long> SOUND_DURATIONS_MS = new ConcurrentHashMap<>();
   private static final Map<String, AtomicInteger> ACTIVE_PLAY_COUNTS = new ConcurrentHashMap<>();

   public static void init() {
      ensureAllPresetsSoundFiles(false);
      loadSoundSelections();
      loadSoundLabels();
      reload();
   }

   public static List<ExternalSoundManager.SoundSlotDefinition> getSoundSlotDefinitions() {
      return SOUND_SLOT_DEFINITIONS;
   }

   public static void startEditing() {
      if (!isEditing) {
         TEMP_SOUND_SELECTIONS = deepCopyStringMap(SOUND_SELECTIONS);
         TEMP_SOUND_LABELS = deepCopyStringMap(SOUND_LABELS);
         isEditing = true;
      }
   }

   public static void saveChanges() {
      if (isEditing) {
         SOUND_SELECTIONS.clear();
         SOUND_SELECTIONS.putAll(TEMP_SOUND_SELECTIONS);
         SOUND_LABELS.clear();
         SOUND_LABELS.putAll(TEMP_SOUND_LABELS);
         TEMP_SOUND_SELECTIONS = null;
         TEMP_SOUND_LABELS = null;
         isEditing = false;
      }

      applyPendingSoundResets();
      saveSoundSelections();
      saveSoundLabels();
      confirmPendingSoundReplacements();
      clearPendingCustomSounds();
   }

   public static void discardChanges() {
      if (isEditing) {
         TEMP_SOUND_SELECTIONS = null;
         TEMP_SOUND_LABELS = null;
         isEditing = false;
      }

      PENDING_SOUND_RESETS.clear();
      revertPendingSoundReplacements();
      revertPendingCustomSounds();
   }

   public static void reload() {
      ensureAllPresetsSoundFiles(false);
      clearCache();
      ClientMessageLogger.chatInfo("battlefield_ops.client.sound.reloading");
      String currentPresetId = ConfigManager.getCurrentPresetId();
      int loadedCount = loadSoundsForPreset(currentPresetId);
      ClientMessageLogger.chatSuccess("battlefield_ops.client.sound.reload_success", currentPresetId, loadedCount);
   }

   public static void reloadAsync() {
      SOUND_IO_THREAD_POOL.submit(() -> {
         try {
            ClientMessageLogger.info("Async sound reload started.");
            ensureAllPresetsSoundFiles(false);
            clearCache();
            String currentPresetId = ConfigManager.getCurrentPresetId();
            int finalLoadedCount = loadSoundsForPreset(currentPresetId);
            ClientMessageLogger.info("Async sound reload success for preset %s: %d loaded.", currentPresetId, finalLoadedCount);
         } catch (Exception var2) {
            ClientMessageLogger.error("Async sound reload failed: %s", var2.getMessage());
         }
      });
   }

   public static void clearCache() {
      SOUND_CACHE.clear();
   }

   private static void ensureAllPresetsSoundFiles(boolean forceReset) {
      ensureCommonSoundFiles(forceReset);
      Set<String> presets = ConfigManager.getPresetIds();
      ensureSoundFilesForPreset("00001", forceReset);
      ensureSoundFilesForPreset("00002", forceReset);

      for (String presetId : presets) {
         ensureSoundFilesForPreset(presetId, forceReset);
      }
   }

   public static void ensureSoundFilesForPreset(String presetId) {
      ensureSoundFilesForPreset(presetId, false);
   }

   public static void ensureSoundFilesForPreset(String presetId, boolean forceReset) {
      try {
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
         if (!Files.exists(presetDir)) {
            Files.createDirectories(presetDir);
         }

         for (String soundName : DEFAULT_SOUNDS) {
            Path targetPath = presetDir.resolve(soundName);
            String baseName = resolveBaseName(soundName);
            Path wavPath = presetDir.resolve(baseName + ".wav");
            if (forceReset) {
               Files.deleteIfExists(targetPath);
               Files.deleteIfExists(wavPath);
            }
         }
      } catch (IOException var10) {
         ClientMessageLogger.error("battlefield_ops.client.sound.init_fail", presetId, var10.getMessage());
      }
   }

   public static List<String> getDefaultSoundNames() {
      return Arrays.asList(DEFAULT_SOUNDS);
   }

   public static List<String> getOfficialSoundFileNames() {
      return Arrays.asList(DEFAULT_SOUNDS);
   }

   public static List<String> getCustomSoundBaseNames(String presetId) {
      if (presetId == null) {
         return Collections.emptyList();
      } else if (PENDING_SOUND_RESETS.contains(presetId)) {
         return Collections.emptyList();
      } else {
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
         if (!Files.exists(presetDir)) {
            return Collections.emptyList();
         } else {
            List<String> result = new ArrayList<>();

            try {
               Files.list(presetDir).forEach(path -> {
                  String name = path.getFileName().toString();
                  if (name.startsWith("custom_") && (name.endsWith(".ogg") || name.endsWith(".wav"))) {
                     result.add(resolveBaseName(name));
                  }
               });
            } catch (IOException var4) {
            }

            Collections.sort(result);
            return result;
         }
      }
   }

   public static Map<String, String> getCustomSoundLabels(String presetId) {
      ensureSoundLabelsLoaded(presetId);
      return getActiveSoundLabels().getOrDefault(presetId, new HashMap<>());
   }

   public static String getSoundDisplayName(String presetId, String baseName) {
      if (baseName == null) {
         return "";
      } else if (baseName.startsWith("custom_")) {
         Map<String, String> labels = getCustomSoundLabels(presetId);
         return labels.getOrDefault(baseName, baseName);
      } else {
         for (String soundName : DEFAULT_SOUNDS) {
            if (resolveBaseName(soundName).equals(baseName)) {
               return soundName;
            }
         }

         return baseName + ".ogg";
      }
   }

   public static String getSelectedSoundBaseName(String presetId, String slotId) {
      ensureSoundSelectionsLoaded(presetId);
      Map<String, String> selections = getActiveSoundSelections().getOrDefault(presetId, new HashMap<>());
      String selected = selections.get(slotId);
      return selected != null && !selected.isEmpty() ? selected : getDefaultSoundBaseName(presetId, slotId);
   }

   public static boolean isSoundSelectionModified(String presetId, String slotId) {
      String selected = getSelectedSoundBaseName(presetId, slotId);
      String def = getDefaultSoundBaseName(presetId, slotId);
      return def != null && !def.equals(selected);
   }

   public static void setSoundSelection(String presetId, String slotId, String baseName) {
      if (presetId != null && slotId != null && baseName != null) {
         ensureSoundSelectionsLoaded(presetId);
         Map<String, String> selections = getActiveSoundSelections().computeIfAbsent(presetId, k -> new ConcurrentHashMap<>());
         selections.put(slotId, baseName);
         refreshSoundCache(presetId, baseName);
         if (!isEditing) {
            saveSoundSelections();
         }
      }
   }

   public static void resetSoundSelectionToDefault(String presetId, String slotId) {
      String def = getDefaultSoundBaseName(presetId, slotId);
      if (def != null) {
         setSoundSelection(presetId, slotId, def);
      }
   }

   public static void markPendingSoundReset(String presetId) {
      if (presetId != null) {
         ensureSoundSelectionsLoaded(presetId);
         ensureSoundLabelsLoaded(presetId);
         PENDING_SOUND_RESETS.add(presetId);

         for (String soundName : DEFAULT_SOUNDS) {
            resetSoundWithBackup(presetId, soundName);
         }

         Map<String, Map<String, String>> selections = getActiveSoundSelections();
         Map<String, String> defaults = new HashMap<>();

         for (String slotId : SOUND_SLOT_IDS) {
            String def = getDefaultSoundBaseName(presetId, slotId);
            if (def != null) {
               defaults.put(slotId, def);
            }
         }

         selections.put(presetId, defaults);
         getActiveSoundLabels().remove(presetId);
      }
   }

   public static void playConfiguredSound(String presetId, String slotId) {
      playConfiguredSound(presetId, slotId, false);
   }

   public static void playConfiguredSound(String presetId, String slotId, boolean blocking) {
      playConfiguredSound(presetId, slotId, blocking, 1.0F);
   }

   public static void playConfiguredSound(String presetId, String slotId, boolean blocking, float volumeMultiplier) {
      if(!cn.bfops.NativeKillIcons.sfxEnabled())return;
      String baseName = getSelectedSoundBaseName(presetId, slotId);
      if (baseName != null && !baseName.isEmpty()) {
         playSound(baseName, blocking, volumeMultiplier);
      }
   }

   public static String createCustomSoundFromFile(String presetId, Path sourcePath, String originalName) {
      if (presetId != null && sourcePath != null) {
         String lower = sourcePath.toString().toLowerCase();
         String sourceExt = lower.endsWith(".ogg") ? ".ogg" : (lower.endsWith(".wav") ? ".wav" : null);
         if (sourceExt == null) {
            return null;
         } else {
            ensureSoundFilesForPreset(presetId, false);
            Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
            int index = 1;

            String baseName;
            Path targetPath;
            do {
               baseName = "custom_" + index;
               targetPath = presetDir.resolve(baseName + sourceExt);
               index++;
            } while (Files.exists(targetPath));

            try {
               if (!Files.exists(targetPath.getParent())) {
                  Files.createDirectories(targetPath.getParent());
               }

               Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
               if (isEditing) {
                  markPendingCustomSound(presetId, targetPath.getFileName().toString());
               }

               updateCustomSoundLabel(presetId, baseName, originalName);
               refreshSoundCache(presetId, baseName);
               return baseName;
            } catch (IOException var10) {
               ClientMessageLogger.error("battlefield_ops.client.sound.replace_fail", presetId, sourcePath.toString(), var10.getMessage());
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static byte[] readSoundBytes(String presetId, String baseName) throws IOException {
      Path path = getSoundPathForPreset(presetId, baseName);
      if (path != null && Files.exists(path)) {
         return Files.readAllBytes(path);
      } else {
         throw new IOException("Missing sound: " + baseName);
      }
   }

   public static boolean deleteCustomSound(String presetId, String baseName) {
      if (presetId != null && baseName != null) {
         ensureSoundSelectionsLoaded(presetId);
         String fileNameOgg = baseName + ".ogg";
         String fileNameWav = baseName + ".wav";
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
         Path oggPath = presetDir.resolve(fileNameOgg);
         Path wavPath = presetDir.resolve(fileNameWav);
         Map<String, ExternalSoundManager.SoundBackup> presetBackups = PENDING_SOUND_BACKUPS.computeIfAbsent(presetId, k -> new ConcurrentHashMap<>());
         Set<String> pending = PENDING_CUSTOM_SOUNDS.get(presetId);

         try {
            boolean existedBefore = Files.exists(oggPath) || Files.exists(wavPath);
            if (pending == null || !pending.contains(fileNameOgg) && !pending.contains(fileNameWav)) {
               backupFileIfNeeded(presetBackups, oggPath);
               backupFileIfNeeded(presetBackups, wavPath);
            }

            Files.deleteIfExists(oggPath);
            Files.deleteIfExists(wavPath);
            removeCustomSoundLabel(presetId, baseName);
            refreshSoundCache(presetId, baseName);
            if (pending != null) {
               pending.remove(fileNameOgg);
               pending.remove(fileNameWav);
            }

            Map<String, String> selections = getActiveSoundSelections().get(presetId);
            if (selections != null) {
               for (Entry<String, String> entry : new HashMap<>(selections).entrySet()) {
                  if (baseName.equals(entry.getValue())) {
                     String def = getDefaultSoundBaseName(presetId, entry.getKey());
                     if (def != null) {
                        selections.put(entry.getKey(), def);
                     }
                  }
               }
            }

            if (!isEditing) {
               saveSoundSelections();
            }

            boolean missingAfter = !Files.exists(oggPath) && !Files.exists(wavPath);
            return missingAfter || !existedBefore;
         } catch (IOException var14) {
            ClientMessageLogger.error("battlefield_ops.client.sound.revert_fail", presetId, baseName, var14.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   public static String getSoundExtensionForPreset(String presetId, String soundName) {
      if (presetId != null && soundName != null) {
         String baseName = resolveBaseName(soundName);
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
         Path oggPath = presetDir.resolve(baseName + ".ogg");
         if (Files.exists(oggPath)) {
            return "OGG";
         } else {
            Path wavPath = presetDir.resolve(baseName + ".wav");
            if (Files.exists(wavPath)) {
               return "WAV";
            } else {
               Path commonOgg = COMMON_SOUNDS_DIR.resolve(baseName + ".ogg");
               if (Files.exists(commonOgg)) {
                  return "OGG";
               } else {
                  Path commonWav = COMMON_SOUNDS_DIR.resolve(baseName + ".wav");
                  return Files.exists(commonWav) ? "WAV" : "OGG";
               }
            }
         }
      } else {
         return "OGG";
      }
   }

   public static boolean replaceSoundWithBackup(String presetId, String soundName, Path sourcePath) {
      if (presetId == null || soundName == null || sourcePath == null) {
         return false;
      } else if (!DEFAULT_SOUND_SET.contains(soundName)) {
         return false;
      } else if (!Files.exists(sourcePath)) {
         return false;
      } else {
         String lower = sourcePath.toString().toLowerCase();
         String sourceExt = lower.endsWith(".ogg") ? ".ogg" : (lower.endsWith(".wav") ? ".wav" : null);
         if (sourceExt == null) {
            return false;
         } else {
            ensureSoundFilesForPreset(presetId, false);
            String baseName = resolveBaseName(soundName);
            Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
            Path targetPath = presetDir.resolve(baseName + sourceExt);
            Path otherPath = presetDir.resolve(baseName + (".ogg".equals(sourceExt) ? ".wav" : ".ogg"));

            try {
               if (!Files.exists(targetPath.getParent())) {
                  Files.createDirectories(targetPath.getParent());
               }

               Map<String, ExternalSoundManager.SoundBackup> presetBackups = PENDING_SOUND_BACKUPS.computeIfAbsent(presetId, k -> new ConcurrentHashMap<>());
               backupFileIfNeeded(presetBackups, targetPath);
               backupFileIfNeeded(presetBackups, otherPath);
               Files.deleteIfExists(otherPath);
               Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
               refreshSoundCache(presetId, baseName);
               return true;
            } catch (IOException var10) {
               ClientMessageLogger.error("battlefield_ops.client.sound.replace_fail", presetId, soundName, var10.getMessage());
               return false;
            }
         }
      }
   }

   public static boolean resetSoundWithBackup(String presetId, String soundName) {
      if (presetId == null || soundName == null) {
         return false;
      } else if (!DEFAULT_SOUND_SET.contains(soundName)) {
         return false;
      } else {
         ensureSoundFilesForPreset(presetId, false);
         String baseName = resolveBaseName(soundName);
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
         Path oggPath = presetDir.resolve(baseName + ".ogg");
         Path wavPath = presetDir.resolve(baseName + ".wav");

         try {
            if (!Files.exists(presetDir)) {
               Files.createDirectories(presetDir);
            }

            Map<String, ExternalSoundManager.SoundBackup> presetBackups = PENDING_SOUND_BACKUPS.computeIfAbsent(presetId, k -> new ConcurrentHashMap<>());
            backupFileIfNeeded(presetBackups, oggPath);
            backupFileIfNeeded(presetBackups, wavPath);
            Files.deleteIfExists(oggPath);
            Files.deleteIfExists(wavPath);
            refreshSoundCache(presetId, baseName);
            return true;
         } catch (IOException var7) {
            ClientMessageLogger.error("battlefield_ops.client.sound.reset_error", presetId, soundName, var7.getMessage());
            return false;
         }
      }
   }

   public static void confirmPendingSoundReplacements() {
      PENDING_SOUND_BACKUPS.clear();
   }

   public static void clearPendingSoundReplacementsForPreset(String presetId) {
      if (presetId != null) {
         PENDING_SOUND_BACKUPS.remove(presetId);
      }
   }

   public static void revertPendingSoundReplacements() {
      if (!PENDING_SOUND_BACKUPS.isEmpty()) {
         for (Entry<String, Map<String, ExternalSoundManager.SoundBackup>> presetEntry : PENDING_SOUND_BACKUPS.entrySet()) {
            String presetId = presetEntry.getKey();

            for (Entry<String, ExternalSoundManager.SoundBackup> entry : presetEntry.getValue().entrySet()) {
               String fileName = entry.getKey();
               ExternalSoundManager.SoundBackup backup = entry.getValue();
               Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve(fileName);

               try {
                  if (backup.existed) {
                     Files.write(targetPath, backup.data);
                  } else {
                     Files.deleteIfExists(targetPath);
                  }

                  refreshSoundCache(presetId, resolveBaseName(fileName));
               } catch (IOException var9) {
                  ClientMessageLogger.error("battlefield_ops.client.sound.revert_fail", presetId, fileName, var9.getMessage());
               }
            }
         }

         PENDING_SOUND_BACKUPS.clear();
      }
   }

   public static void revertPendingSoundReplacementsForPreset(String presetId) {
      if (presetId != null) {
         Map<String, ExternalSoundManager.SoundBackup> presetBackups = PENDING_SOUND_BACKUPS.get(presetId);
         if (presetBackups != null && !presetBackups.isEmpty()) {
            for (Entry<String, ExternalSoundManager.SoundBackup> entry : presetBackups.entrySet()) {
               String fileName = entry.getKey();
               ExternalSoundManager.SoundBackup backup = entry.getValue();
               Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve(fileName);

               try {
                  if (backup.existed) {
                     Files.write(targetPath, backup.data);
                  } else {
                     Files.deleteIfExists(targetPath);
                  }

                  refreshSoundCache(presetId, resolveBaseName(fileName));
               } catch (IOException var8) {
                  ClientMessageLogger.error("battlefield_ops.client.sound.revert_fail", presetId, fileName, var8.getMessage());
               }
            }

            PENDING_SOUND_BACKUPS.remove(presetId);
         }
      }
   }

   public static boolean hasPendingSoundChanges() {
      if (hasPendingSoundSelectionChanges()) {
         return true;
      } else if (hasPendingSoundLabelChanges()) {
         return true;
      } else if (!PENDING_CUSTOM_SOUNDS.isEmpty()) {
         return true;
      } else if (!PENDING_SOUND_RESETS.isEmpty()) {
         return true;
      } else {
         for (Map<String, ExternalSoundManager.SoundBackup> presetBackups : PENDING_SOUND_BACKUPS.values()) {
            if (presetBackups != null && !presetBackups.isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean isSoundModified(String presetId, String soundName) {
      if (presetId == null || soundName == null) {
         return false;
      } else if (!DEFAULT_SOUND_SET.contains(soundName)) {
         return false;
      } else {
         String baseName = resolveBaseName(soundName);
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
         Path oggPath = presetDir.resolve(baseName + ".ogg");
         Path wavPath = presetDir.resolve(baseName + ".wav");
         Path currentPath = Files.exists(oggPath) ? oggPath : (Files.exists(wavPath) ? wavPath : null);
         if (currentPath == null) {
            return false;
         } else {
            byte[] defaultBytes = getDefaultSoundBytes(soundName);
            if (defaultBytes == null) {
               return false;
            } else {
               try {
                  byte[] currentBytes = Files.readAllBytes(currentPath);
                  return !Arrays.equals(defaultBytes, currentBytes);
               } catch (IOException var9) {
                  return false;
               }
            }
         }
      }
   }

   public static boolean resetSound(String presetId, String soundName) {
      if (presetId != null && soundName != null) {
         Map<String, ExternalSoundManager.SoundBackup> presetBackups = PENDING_SOUND_BACKUPS.get(presetId);
         if (presetBackups != null && presetBackups.containsKey(soundName)) {
            ExternalSoundManager.SoundBackup backup = presetBackups.remove(soundName);
            Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve(soundName);

            try {
               if (backup.existed) {
                  Files.write(targetPath, backup.data);
               } else {
                  Files.deleteIfExists(targetPath);
               }

               if (presetBackups.isEmpty()) {
                  PENDING_SOUND_BACKUPS.remove(presetId);
               }

               refreshSoundCache(presetId, resolveBaseName(soundName));
               return true;
            } catch (IOException var12) {
               ClientMessageLogger.error("battlefield_ops.client.sound.revert_fail", presetId, soundName, var12.getMessage());
               return false;
            }
         } else if (isSoundModified(presetId, soundName)) {
            try {
               Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
               Path targetPath = presetDir.resolve(soundName);
               String baseName = resolveBaseName(soundName);
               Files.deleteIfExists(presetDir.resolve(baseName + ".ogg"));
               Files.deleteIfExists(presetDir.resolve(baseName + ".wav"));
               ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "sounds/kill/" + soundName);

               try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
                  Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
               } catch (Exception var16) {
                  try (InputStream streamx = ExternalSoundManager.class.getResourceAsStream("/assets/battlefield_ops/sounds/kill/" + soundName)) {
                     if (streamx != null) {
                        Files.copy(streamx, targetPath, StandardCopyOption.REPLACE_EXISTING);
                     }
                  }
               }

               refreshSoundCache(presetId, baseName);
               return true;
            } catch (IOException var17) {
               ClientMessageLogger.error("battlefield_ops.client.sound.replace_fail", var17.getMessage());
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void resetSounds(String presetId) {
      ensureCommonSoundFiles(false);
      ensureSoundFilesForPreset(presetId, true);
      resetSoundSelectionDataForPreset(presetId, true);
      saveSoundSelections();
      saveSoundLabels();
      ClientMessageLogger.chatSuccess("battlefield_ops.client.sound.reset_success", presetId);
      if (presetId.equals(ConfigManager.getCurrentPresetId())) {
         reload();
      }
   }

   public static void resetSoundsAsync(String presetId) {
      SOUND_IO_THREAD_POOL.submit(() -> {
         try {
            ClientMessageLogger.info("Async sound reset started for preset %s.", presetId);
            ensureCommonSoundFiles(false);
            ensureSoundFilesForPreset(presetId, true);
            resetSoundSelectionDataForPreset(presetId, true);
            saveSoundSelections();
            saveSoundLabels();
            ClientMessageLogger.info("Async sound reset success for preset %s.", presetId);
            if (presetId.equals(ConfigManager.getCurrentPresetId())) {
               reloadAsync();
            }
         } catch (Exception var2) {
            ClientMessageLogger.error("Async sound reset failed for preset %s: %s", presetId, var2.getMessage());
         }
      });
   }

   public static void resetAllSounds() {
      ensureCommonSoundFiles(true);

      for (String presetId : ConfigManager.getPresetIds()) {
         ensureSoundFilesForPreset(presetId, true);
         resetSoundSelectionDataForPreset(presetId, true);
      }

      saveSoundSelections();
      saveSoundLabels();
      reload();
   }

   public static void resetAllSoundsAsync() {
      SOUND_IO_THREAD_POOL.submit(() -> {
         try {
            ClientMessageLogger.info("Async sound reset started for all presets.");
            ensureCommonSoundFiles(true);

            for (String presetId : ConfigManager.getPresetIds()) {
               ensureSoundFilesForPreset(presetId, true);
               resetSoundSelectionDataForPreset(presetId, true);
            }

            saveSoundSelections();
            saveSoundLabels();
            ClientMessageLogger.info("Async sound reset success for all presets.");
            reloadAsync();
         } catch (Exception var3) {
            ClientMessageLogger.error("Async sound reset failed: %s", var3.getMessage());
         }
      });
   }

   private static void resetSoundSelectionDataForPreset(String presetId, boolean deleteCustomFiles) {
      if (presetId != null && !presetId.isEmpty()) {
         Map<String, String> defaults = new HashMap<>();

         for (String slotId : SOUND_SLOT_IDS) {
            String def = getDefaultSoundBaseName(presetId, slotId);
            if (def != null) {
               defaults.put(slotId, def);
            }
         }

         SOUND_SELECTIONS.put(presetId, defaults);
         SOUND_LABELS.remove(presetId);
         if (deleteCustomFiles) {
            Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");

            try {
               if (Files.exists(presetDir)) {
                  try (Stream<Path> paths = Files.list(presetDir)) {
                     paths.forEach(path -> {
                        String name = path.getFileName().toString();
                        if (name.startsWith("custom_") && (name.endsWith(".ogg") || name.endsWith(".wav"))) {
                           try {
                              Files.deleteIfExists(path);
                           } catch (IOException var3) {
                           }
                        }
                     });
                  }

                  Files.deleteIfExists(presetDir.resolve("custom_sound_labels.json"));
               }
            } catch (IOException var9) {
            }

            clearPendingSoundReplacementsForPreset(presetId);
            PENDING_CUSTOM_SOUNDS.remove(presetId);
         }
      }
   }

   private static int loadSoundsForPreset(String presetId) {
      return loadSoundsForPreset(presetId, null);
   }

   private static int loadSoundsForPreset(String presetId, Consumer<Integer> progressCallback) {
      int count = 0;
      count += loadSoundsFromDirectory(COMMON_SOUNDS_DIR, false, progressCallback, count);
      Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
      return count + loadSoundsFromDirectory(presetDir, true, progressCallback, count);
   }

   private static ExternalSoundManager.SoundData loadSoundFile(Path path) throws IOException {
      String fileName = path.getFileName().toString().toLowerCase();
      if (fileName.endsWith(".wav")) {
         return loadWav(path);
      } else {
         return fileName.endsWith(".ogg") ? loadOgg(path) : null;
      }
   }

   private static ExternalSoundManager.SoundData loadWav(Path path) {
      try {
         AudioInputStream ais = AudioSystem.getAudioInputStream(path.toFile());
         AudioFormat format = ais.getFormat();
         ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         byte[] data = new byte[4096];

         int nRead;
         while ((nRead = ais.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
         }

         return new ExternalSoundManager.SoundData(buffer.toByteArray(), format);
      } catch (Exception var6) {
         ClientMessageLogger.error("battlefield_ops.client.sound.load_fail", var6.getMessage());
         return null;
      }
   }

   private static ExternalSoundManager.SoundData loadOgg(Path path) {
      ByteBuffer vorbisBuffer = null;

      Object var22;
      try {
         MemoryStack stack;
         label141: {
            byte[] bytes = Files.readAllBytes(path);
            vorbisBuffer = BufferUtils.createByteBuffer(bytes.length);
            vorbisBuffer.put(bytes);
            vorbisBuffer.flip();
            stack = MemoryStack.stackPush();

            ExternalSoundManager.SoundData var24;
            try {
               IntBuffer channelsBuffer = stack.mallocInt(1);
               IntBuffer sampleRateBuffer = stack.mallocInt(1);
               ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(vorbisBuffer, channelsBuffer, sampleRateBuffer);
               if (pcm == null) {
                  var22 = null;
                  break label141;
               }

               int channels = channelsBuffer.get(0);
               int sampleRate = sampleRateBuffer.get(0);
               byte[] pcmBytes = new byte[pcm.capacity() * 2];

               for (int i = 0; i < pcm.capacity(); i++) {
                  short sample = pcm.get(i);
                  pcmBytes[i * 2] = (byte)(sample & 255);
                  pcmBytes[i * 2 + 1] = (byte)(sample >> 8 & 0xFF);
               }

               AudioFormat format = new AudioFormat((float)sampleRate, 16, channels, true, false);
               var24 = new ExternalSoundManager.SoundData(pcmBytes, format);
            } catch (Throwable var18) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var17) {
                     var18.addSuppressed(var17);
                  }
               }

               throw var18;
            }

            if (stack != null) {
               stack.close();
            }

            return var24;
         }

         if (stack != null) {
            stack.close();
         }
      } catch (Exception var19) {
         ClientMessageLogger.error("battlefield_ops.client.sound.load_fail", var19.getMessage());
         return null;
      } finally {
         if (vorbisBuffer != null) {
         }
      }

      return (ExternalSoundManager.SoundData)var22;
   }

   public static void playSound(String name) {
      playSound(name, false);
   }

   public static void playSound(String name, boolean blocking) {
      playSound(name, blocking, 1.0F);
   }

   public static void playSound(String name, boolean blocking, float volumeMultiplier) {
      if (ClientConfigManager.isEnableSound()) {
         int soundVolume = ClientConfigManager.getSoundVolume();
         if (soundVolume > 0) {
            if (!blocking || !BLOCKING_STATUS.getOrDefault(name, false)) {
               long now = System.currentTimeMillis();
               long lastPlayTime = SOUND_LAST_PLAY_TIMES.getOrDefault(name, 0L);
               if (now - lastPlayTime >= 10L) {
                  SOUND_LAST_PLAY_TIMES.put(name, now);
                  ExternalSoundManager.SoundData data = SOUND_CACHE.get(name);
                  if (data != null) {
                     long frameLength = (long)(data.pcmData.length / data.format.getFrameSize());
                     double durationInSeconds = (double)((float)frameLength / data.format.getFrameRate());
                     long durationMs = (long)(durationInSeconds * 1000.0);
                     SOUND_DURATIONS_MS.put(name, durationMs);
                     if (blocking) {
                        BLOCKING_STATUS.put(name, true);
                     }

                     ACTIVE_PLAY_COUNTS.compute(name, (key, count) -> {
                        if (count == null) {
                           count = new AtomicInteger(0);
                        }

                        count.incrementAndGet();
                        return (AtomicInteger)count;
                     });
                     SOUND_START_TIMES.put(name, System.currentTimeMillis());
                     SOUND_PLAYBACK_THREAD_POOL.submit(() -> {
                        try {
                           Info info = new Info(SourceDataLine.class, data.format);
                           if (AudioSystem.isLineSupported(info)) {
                              SourceDataLine line = (SourceDataLine)AudioSystem.getLine(info);
                              line.open(data.format);
                              if (line.isControlSupported(Type.MASTER_GAIN)) {
                                 FloatControl gainControl = (FloatControl)line.getControl(Type.MASTER_GAIN);
                                 float masterVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
                                 masterVolume *= Math.max(0.0F, (float)soundVolume / 100.0F) * Math.max(0.0F, volumeMultiplier);
                                 if (masterVolume <= 1.0E-4F) {
                                    masterVolume = 1.0E-4F;
                                 }

                                 float dB = (float)(20.0 * Math.log10((double)masterVolume));
                                 dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                                 gainControl.setValue(dB);
                              }

                              line.start();
                              line.write(data.pcmData, 0, data.pcmData.length);
                              line.drain();
                              line.close();
                              return;
                           }
                        } catch (Exception var13) {
                           ClientMessageLogger.error("sound playback failed: %s", var13.getMessage());
                           return;
                        } finally {
                           if (blocking) {
                              BLOCKING_STATUS.put(name, false);
                           }

                           ACTIVE_PLAY_COUNTS.computeIfPresent(name, (key, count) -> (AtomicInteger)(count.decrementAndGet() <= 0 ? null : count));
                        }
                     });
                  }
               }
            }
         }
      }
   }

   public static boolean isSoundPlaying(String name) {
      AtomicInteger count = ACTIVE_PLAY_COUNTS.get(name);
      return count != null && count.get() > 0;
   }

   public static float getSoundProgress(String name) {
      if (!isSoundPlaying(name)) {
         return 0.0F;
      } else {
         Long start = SOUND_START_TIMES.get(name);
         Long duration = SOUND_DURATIONS_MS.get(name);
         if (start != null && duration != null && duration != 0L) {
            long elapsed = System.currentTimeMillis() - start;
            float progress = (float)elapsed / (float)duration.longValue();
            return Math.min(1.0F, Math.max(0.0F, progress));
         } else {
            return 0.0F;
         }
      }
   }

   private static void backupFileIfNeeded(Map<String, ExternalSoundManager.SoundBackup> presetBackups, Path path) throws IOException {
      String fileName = path.getFileName().toString();
      if (!presetBackups.containsKey(fileName)) {
         if (Files.exists(path)) {
            byte[] original = Files.readAllBytes(path);
            presetBackups.put(fileName, new ExternalSoundManager.SoundBackup(true, original));
         } else {
            presetBackups.put(fileName, new ExternalSoundManager.SoundBackup(false, null));
         }
      }
   }

   private static Path getSoundPathForPreset(String presetId, String baseName) {
      Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
      Path oggPath = presetDir.resolve(baseName + ".ogg");
      if (Files.exists(oggPath)) {
         return oggPath;
      } else {
         Path wavPath = presetDir.resolve(baseName + ".wav");
         if (Files.exists(wavPath)) {
            return wavPath;
         } else {
            Path commonOgg = COMMON_SOUNDS_DIR.resolve(baseName + ".ogg");
            if (Files.exists(commonOgg)) {
               return commonOgg;
            } else {
               Path commonWav = COMMON_SOUNDS_DIR.resolve(baseName + ".wav");
               return Files.exists(commonWav) ? commonWav : oggPath;
            }
         }
      }
   }

   private static String resolveBaseName(String soundName) {
      return soundName.replaceFirst("[.][^.]+$", "");
   }

   private static byte[] getDefaultSoundBytes(String soundName) {
      if (DEFAULT_SOUND_BYTES.containsKey(soundName)) {
         return DEFAULT_SOUND_BYTES.get(soundName);
      } else {
         byte[] bytes = null;

         try {
            ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "sounds/kill/" + soundName);

            try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
               bytes = stream.readAllBytes();
            }
         } catch (Exception var11) {
            try (InputStream stream = ExternalSoundManager.class.getResourceAsStream("/assets/battlefield_ops/sounds/kill/" + soundName)) {
               if (stream != null) {
                  bytes = stream.readAllBytes();
               }
            } catch (IOException var9) {
               bytes = null;
            }
         }

         if (bytes != null) {
            DEFAULT_SOUND_BYTES.put(soundName, bytes);
         }

         return bytes;
      }
   }

   private static void ensureSoundSelectionsLoaded(String presetId) {
      if (presetId != null) {
         if (!getActiveSoundSelections().containsKey(presetId)) {
            Map<String, String> selections = new HashMap<>();
            Path path = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve("sound_selection.json");
            if (Files.exists(path)) {
               try {
                  String content = Files.readString(path);
                  JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

                  for (String key : obj.keySet()) {
                     selections.put(key, obj.get(key).getAsString());
                  }
               } catch (Exception var7) {
               }
            }

            getActiveSoundSelections().put(presetId, selections);
         }
      }
   }

   private static void ensureSoundLabelsLoaded(String presetId) {
      if (presetId != null) {
         if (!getActiveSoundLabels().containsKey(presetId)) {
            Map<String, String> labels = new HashMap<>();
            Path path = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve("custom_sound_labels.json");
            if (Files.exists(path)) {
               try {
                  String content = Files.readString(path);
                  JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

                  for (String key : obj.keySet()) {
                     labels.put(key, obj.get(key).getAsString());
                  }
               } catch (Exception var7) {
               }
            }

            getActiveSoundLabels().put(presetId, labels);
         }
      }
   }

   private static Map<String, Map<String, String>> getActiveSoundSelections() {
      return isEditing && TEMP_SOUND_SELECTIONS != null ? TEMP_SOUND_SELECTIONS : SOUND_SELECTIONS;
   }

   private static Map<String, Map<String, String>> getActiveSoundLabels() {
      return isEditing && TEMP_SOUND_LABELS != null ? TEMP_SOUND_LABELS : SOUND_LABELS;
   }

   private static void saveSoundSelections() {
      for (Entry<String, Map<String, String>> entry : SOUND_SELECTIONS.entrySet()) {
         String presetId = entry.getKey();
         Map<String, String> selections = entry.getValue();
         Path path = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve("sound_selection.json");
         JsonObject obj = new JsonObject();

         for (Entry<String, String> sel : selections.entrySet()) {
            obj.addProperty(sel.getKey(), sel.getValue());
         }

         try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, obj.toString());
         } catch (IOException var8) {
         }
      }
   }

   private static void saveSoundLabels() {
      for (Entry<String, Map<String, String>> entry : SOUND_LABELS.entrySet()) {
         String presetId = entry.getKey();
         Map<String, String> labels = entry.getValue();
         Path path = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve("custom_sound_labels.json");
         JsonObject obj = new JsonObject();

         for (Entry<String, String> label : labels.entrySet()) {
            obj.addProperty(label.getKey(), label.getValue());
         }

         try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, obj.toString());
         } catch (IOException var8) {
         }
      }
   }

   private static void applyPendingSoundResets() {
      if (!PENDING_SOUND_RESETS.isEmpty()) {
         Set<String> pending = new HashSet<>(PENDING_SOUND_RESETS);
         PENDING_SOUND_RESETS.clear();

         for (String presetId : pending) {
            Map<String, String> defaults = new HashMap<>();

            for (String slotId : SOUND_SLOT_IDS) {
               String def = getDefaultSoundBaseName(presetId, slotId);
               if (def != null) {
                  defaults.put(slotId, def);
               }
            }

            SOUND_SELECTIONS.put(presetId, defaults);
            SOUND_LABELS.remove(presetId);

            try {
               Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds");
               if (Files.exists(presetDir)) {
                  try (Stream<Path> paths = Files.list(presetDir)) {
                     paths.forEach(path -> {
                        String name = path.getFileName().toString();
                        if (name.startsWith("custom_") && (name.endsWith(".ogg") || name.endsWith(".wav"))) {
                           try {
                              Files.deleteIfExists(path);
                           } catch (IOException var3x) {
                           }
                        }
                     });
                  }
               }

               Files.deleteIfExists(presetDir.resolve("custom_sound_labels.json"));
               ensureSoundFilesForPreset(presetId, true);
            } catch (IOException var10) {
            }

            clearPendingSoundReplacementsForPreset(presetId);
            PENDING_CUSTOM_SOUNDS.remove(presetId);
            if (presetId.equals(ConfigManager.getCurrentPresetId())) {
               reloadAsync();
            }
         }
      }
   }

   private static void loadSoundSelections() {
      SOUND_SELECTIONS.clear();
   }

   private static void loadSoundLabels() {
      SOUND_LABELS.clear();
   }

   private static Map<String, Map<String, String>> deepCopyStringMap(Map<String, Map<String, String>> source) {
      Map<String, Map<String, String>> result = new HashMap<>();

      for (Entry<String, Map<String, String>> entry : source.entrySet()) {
         result.put(entry.getKey(), new HashMap<>(entry.getValue()));
      }

      return result;
   }

   private static boolean hasPendingSoundSelectionChanges() {
      if (isEditing && TEMP_SOUND_SELECTIONS != null) {
         if (TEMP_SOUND_SELECTIONS.size() != SOUND_SELECTIONS.size()) {
            return true;
         } else {
            for (Entry<String, Map<String, String>> entry : SOUND_SELECTIONS.entrySet()) {
               Map<String, String> temp = TEMP_SOUND_SELECTIONS.get(entry.getKey());
               if (temp == null || !temp.equals(entry.getValue())) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean hasPendingSoundLabelChanges() {
      if (isEditing && TEMP_SOUND_LABELS != null) {
         if (TEMP_SOUND_LABELS.size() != SOUND_LABELS.size()) {
            return true;
         } else {
            for (Entry<String, Map<String, String>> entry : SOUND_LABELS.entrySet()) {
               Map<String, String> temp = TEMP_SOUND_LABELS.get(entry.getKey());
               if (temp == null || !temp.equals(entry.getValue())) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static void updateCustomSoundLabel(String presetId, String baseName, String originalName) {
      if (presetId != null && baseName != null) {
         ensureSoundLabelsLoaded(presetId);
         String label = originalName == null ? baseName : originalName;
         getActiveSoundLabels().computeIfAbsent(presetId, k -> new ConcurrentHashMap<>()).put(baseName, label);
         if (!isEditing) {
            saveSoundLabels();
         }
      }
   }

   private static void removeCustomSoundLabel(String presetId, String baseName) {
      if (presetId != null && baseName != null) {
         ensureSoundLabelsLoaded(presetId);
         Map<String, String> labels = getActiveSoundLabels().get(presetId);
         if (labels != null) {
            labels.remove(baseName);
            if (!isEditing) {
               saveSoundLabels();
            }
         }
      }
   }

   private static void markPendingCustomSound(String presetId, String fileName) {
      if (presetId != null && fileName != null) {
         PENDING_CUSTOM_SOUNDS.computeIfAbsent(presetId, k -> ConcurrentHashMap.newKeySet()).add(fileName);
      }
   }

   private static void clearPendingCustomSounds() {
      PENDING_CUSTOM_SOUNDS.clear();
   }

   private static void revertPendingCustomSounds() {
      for (Entry<String, Set<String>> entry : PENDING_CUSTOM_SOUNDS.entrySet()) {
         String presetId = entry.getKey();

         for (String fileName : entry.getValue()) {
            Path path = CONFIG_ASSETS_DIR.resolve(presetId).resolve("sounds").resolve(fileName);

            try {
               Files.deleteIfExists(path);
            } catch (IOException var7) {
            }
         }
      }

      PENDING_CUSTOM_SOUNDS.clear();
   }

   public static String getDefaultSoundBaseName(String presetId, String slotId) {
      if (slotId == null) {
         return null;
      } else {
         boolean isBf5 = "00007".equals(presetId);
         boolean isBf6 = "00037".equals(presetId);

         return switch (slotId) {
            case "common_score" -> "addscore_df";
            case "common_hit" -> isBf6 ? "hitsound_bf6" : "hitsound_df";
            case "common_headshot_hit" -> isBf6 ? "hitsound_bf6" : (ValorantStyleCatalog.getPresetSpec(presetId) != null ? "valorant_headshot" : "hitsound_df");
            case "scrolling_default" -> isBf6 ? "killsound_bf6" : (isBf5 ? "killsound_bf5" : "killsound_df");
            case "scrolling_headshot" -> isBf6 ? "headshotkillsound_bf6" : (isBf5 ? "headshotkillsound_bf5" : "headshotkillsound_df");
            case "scrolling_explosion" -> isBf6 ? "killsound_bf6" : (isBf5 ? "headshotkillsound_bf5" : "explosionkillsound_df");
            case "scrolling_crit" -> isBf6 ? "killsound_bf6" : (isBf5 ? "killsound_bf5" : "critkillsound_df");
            case "scrolling_assist" -> isBf6 ? "assistsound_bf6" : "defaulticonsound_df";
            case "scrolling_vehicle" -> isBf6 ? "assistsound_bf6" : (isBf5 ? "vehiclekillsound_bf5" : "explosionkillsound_df");
            case "bf1_default" -> "killsound_bf1";
            case "bf1_headshot" -> "headshotkillsound_bf1";
            case "card_default" -> "cardkillsound_default_cs";
            case "card_headshot" -> "cardkillsound_headshot_cs";
            case "card_explosion" -> "cardkillsound_explosion_cs";
            case "card_crit" -> "cardkillsound_crit_cs";
            case "card_armor_headshot" -> "cardkillsound_armorheadshot_cs";
            case "combo_1" -> "combokillsound_1_cf";
            case "combo_2" -> "combokillsound_2_cf";
            case "combo_3" -> "combokillsound_3_cf";
            case "combo_4" -> "combokillsound_4_cf";
            case "combo_5" -> "combokillsound_5_cf";
            case "combo_6" -> "combokillsound_6_cf";
            case "valorant_1" -> resolveValorantComboSoundBaseName(presetId, 1);
            case "valorant_2" -> resolveValorantComboSoundBaseName(presetId, 2);
            case "valorant_3" -> resolveValorantComboSoundBaseName(presetId, 3);
            case "valorant_4" -> resolveValorantComboSoundBaseName(presetId, 4);
            case "valorant_5" -> resolveValorantComboSoundBaseName(presetId, 5);
            case "honor_normal" -> "honorkillsound_normal";
            case "honor_high" -> "honorkillsound_high";
            default -> null;
         };
      }
   }

   private static String resolveValorantComboSoundBaseName(String presetId, int comboTier) {
      int resolvedTier = Mth.clamp(comboTier, 1, 5);
      return ValorantStyleCatalog.getComboSoundBaseName(presetId, null, resolvedTier);
   }

   private static void refreshSoundCache(String presetId, String baseName) {
      if (ConfigManager.getCurrentPresetId().equals(presetId)) {
         SOUND_CACHE.remove(baseName);
         Path path = getSoundPathForPreset(presetId, baseName);
         if (path != null && Files.exists(path)) {
            try {
               ExternalSoundManager.SoundData data = loadSoundFile(path);
               if (data != null) {
                  SOUND_CACHE.put(baseName, data);
               }
            } catch (Exception var4) {
               SOUND_CACHE.remove(baseName);
            }
         }
      }
   }

   private static void ensureCommonSoundFiles(boolean forceReset) {
      try {
         if (!Files.exists(COMMON_SOUNDS_DIR)) {
            Files.createDirectories(COMMON_SOUNDS_DIR);
         }

         for (String soundName : DEFAULT_SOUNDS) {
            String baseName = resolveBaseName(soundName);
            Path targetPath = COMMON_SOUNDS_DIR.resolve(baseName + ".ogg");
            if (forceReset || !Files.exists(targetPath)) {
               ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "sounds/kill/" + soundName);

               try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
                  Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
               } catch (Exception var17) {
                  Exception e = var17;

                  try (InputStream streamx = ExternalSoundManager.class.getResourceAsStream("/assets/battlefield_ops/sounds/kill/" + soundName)) {
                     if (streamx != null) {
                        Files.copy(streamx, targetPath, StandardCopyOption.REPLACE_EXISTING);
                     } else {
                        ClientMessageLogger.error("battlefield_ops.client.sound.extract_fail", soundName, e.getMessage());
                     }
                  } catch (Exception var15) {
                     ClientMessageLogger.error("battlefield_ops.client.sound.extract_fail", soundName, var15.getMessage());
                  }
               }
            }
         }
      } catch (IOException var18) {
         ClientMessageLogger.error("battlefield_ops.client.sound.init_fail", "common", var18.getMessage());
      }
   }

   private static int loadSoundsFromDirectory(Path directory, boolean override, Consumer<Integer> progressCallback, int currentCount) {
      if (!Files.exists(directory)) {
         return 0;
      } else {
         int count = 0;

         try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : (Iterable<Path>)stream::iterator) {
               if (Files.isRegularFile(path)) {
                  String fileName = path.getFileName().toString();
                  if (fileName.endsWith(".ogg") || fileName.endsWith(".wav")) {
                     try {
                        String key = fileName.replaceFirst("[.][^.]+$", "");
                        if (override || !SOUND_CACHE.containsKey(key)) {
                           ExternalSoundManager.SoundData data = loadSoundFile(path);
                           if (data != null) {
                              SOUND_CACHE.put(key, data);
                              count++;
                              if (progressCallback != null) {
                                 progressCallback.accept(currentCount + count);
                              }
                           }
                        }
                     } catch (Exception var12) {
                        ClientMessageLogger.error("Failed to load sound: " + fileName + " - " + var12.getMessage());
                     }
                  }
               }
            }
         } catch (IOException var14) {
            ClientMessageLogger.error("battlefield_ops.client.sound.extract_fail", var14.getMessage());
         }

         return count;
      }
   }

   public static ExternalSoundManager.SoundData getSoundData(String name) {
      return SOUND_CACHE.get(name);
   }

   private static ThreadFactory createThreadFactory(String namePrefix) {
      return runnable -> {
         Thread thread = new Thread(runnable);
         thread.setName(namePrefix + "-" + thread.getId());
         thread.setDaemon(true);
         return thread;
      };
   }

   private static class SoundBackup {
      private final boolean existed;
      private final byte[] data;

      private SoundBackup(boolean existed, byte[] data) {
         this.existed = existed;
         this.data = data;
      }
   }

   public static class SoundData {
      public final byte[] pcmData;
      public final AudioFormat format;

      public SoundData(byte[] pcmData, AudioFormat format) {
         this.pcmData = pcmData;
         this.format = format;
      }
   }

   public static enum SoundElementGroup {
      COMMON("battlefield_ops.client.gui.config.sound.group.common"),
      SCROLLING("battlefield_ops.client.gui.config.sound.group.scrolling"),
      BATTLEFIELD1("battlefield_ops.client.gui.config.sound.group.battlefield1"),
      CARD("battlefield_ops.client.gui.config.sound.group.card"),
      COMBO("battlefield_ops.client.gui.config.sound.group.combo"),
      VALORANT("battlefield_ops.client.gui.config.sound.group.valorant"),
      HONOR("battlefield_ops.client.gui.config.sound.group.honor");

      private final String titleKey;

      private SoundElementGroup(String titleKey) {
         this.titleKey = titleKey;
      }

      public String titleKey() {
         return this.titleKey;
      }
   }

   public static record SoundSlotDefinition(ExternalSoundManager.SoundElementGroup group, String titleKey, String slotId) {
   }
}
