package cn.bfops.killicon.client.textures;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.config.ValorantStyleCatalog;
import cn.bfops.killicon.client.render.effect.IconTextureFilterEffect;
import cn.bfops.killicon.client.util.ClientMessageLogger;

public class ExternalTextureManager {
   private static final Path CONFIG_ASSETS_DIR = ClientBridge.loader().getConfigDir().resolve("battlefield_ops/assets");
   private static final Path COMMON_TEXTURES_DIR = CONFIG_ASSETS_DIR.resolve("common").resolve("textures");
   private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
   private static final ExecutorService TEXTURE_THREAD_POOL = Executors.newCachedThreadPool();
   private static final Gson GSON = new Gson();
   private static final Pattern CUSTOM_TEXTURE_PATTERN = Pattern.compile("^custom_(\\d+)\\.png$");
   private static final String CUSTOM_LABELS_FILE = "custom_labels.json";
   private static final String CUSTOM_META_FILE = "custom_meta.json";
   private static final String[] DEFAULT_TEXTURES = buildDefaultTextures();
   private static final Set<String> DEFAULT_TEXTURE_SET = new HashSet<>(Arrays.asList(DEFAULT_TEXTURES));
   private static final List<String> DEFAULT_TEXTURE_LIST = Collections.unmodifiableList(Arrays.asList(DEFAULT_TEXTURES));
   private static final Map<String, Map<String, ExternalTextureManager.TextureBackup>> PENDING_TEXTURE_BACKUPS = new HashMap<>();
   private static final Map<String, byte[]> DEFAULT_TEXTURE_BYTES = new HashMap<>();
   private static final Map<String, ExternalTextureManager.TextureState> TEXTURE_STATE_CACHE = new ConcurrentHashMap<>();
   private static final Set<String> TEXTURE_STATE_LOADING = ConcurrentHashMap.newKeySet();
   private static final Map<String, ExternalTextureManager.TextureDimensions> TEXTURE_DIMENSIONS = new HashMap<>();

   private static String[] buildDefaultTextures() {
      LinkedHashSet<String> names = new LinkedHashSet<>();
      addElementTextures(names, "00001", "kill_icon/scrolling");
      addElementTextures(names, "00007", "kill_icon/scrolling");
      addElementTextures(names, "00008", "kill_icon/scrolling");
      addElementTextures(names, "00037", "kill_icon/scrolling");
      addElementTextures(names, "00001", "kill_icon/combo");
      addElementTextures(names, "00001", "kill_icon/card");
      addElementTextures(names, "00001", "kill_icon/card_bar");
      addElementTextures(names, "00001", "kill_icon/battlefield1");

      for (ValorantStyleCatalog.StyleSpec definition : ValorantStyleCatalog.getDefinitions()) {
         for (String textureKey : ElementTextureDefinition.getTextures("kill_icon/valorant")) {
            String fileName = ValorantStyleCatalog.getOfficialTextureFileNameForStyle(definition.styleId(), textureKey);
            if (fileName != null && !fileName.isBlank()) {
               names.add(fileName);
            }
         }
      }

      names.add("killicon_valorant_icon.png");
      names.add("killicon_valorant_bar.png");
      names.add("killicon_valorant_gaia_icon.png");
      names.add("killicon_valorant_gaia_bar.png");
      return names.toArray(new String[0]);
   }

   private static void addElementTextures(Set<String> names, String presetId, String elementId) {
      for (String textureKey : ElementTextureDefinition.getTextures(elementId)) {
         String fileName = ElementTextureDefinition.getTextureFileName(presetId, elementId, textureKey);
         if (fileName != null && !fileName.isBlank()) {
            names.add(fileName);
         }
      }
   }

   public static void init() {
      reload();
   }

   public static void reload() {
      ensureAllPresetsTextureFiles(false);
      clearCache();
      ClientMessageLogger.chatInfo("battlefield_ops.client.texture.reloading");
      String currentPresetId = ConfigManager.getCurrentPresetId();
      // getTexture already resolves individual images on demand. Avoid decoding the whole preset at startup.
      ClientMessageLogger.info("Texture cache cleared for %s; images load when displayed.", currentPresetId);
   }

   public static void reloadAsync() {
      TEXTURE_THREAD_POOL.submit(() -> {
         try {
            ensureAllPresetsTextureFiles(false);
            Minecraft.getInstance().execute(() -> {
               clearCache();
               ClientMessageLogger.info("Texture cache cleared; images load when displayed.");
            });
         } catch (Exception error) {
            ClientMessageLogger.error("Async texture reload failed: %s", error.getMessage());
         }
      });
   }

   public static void resetTextures(String presetId) {
      ensureCommonTextureFiles(false);
      ensureTextureFilesForPreset(presetId, true);
      ClientMessageLogger.chatSuccess("battlefield_ops.client.texture.reset_success", presetId);
      if (presetId.equals(ConfigManager.getCurrentPresetId())) {
         reload();
      }
   }

   public static void resetTexturesAsync(String presetId) {
      TEXTURE_THREAD_POOL.submit(() -> {
         try {
            ClientMessageLogger.info("Async texture reset started for preset %s.", presetId);
            ensureCommonTextureFiles(false);
            ensureTextureFilesForPreset(presetId, true);
            ClientMessageLogger.info("Async texture reset success for preset %s.", presetId);
            if (presetId.equals(ConfigManager.getCurrentPresetId())) {
               reloadAsync();
            }
         } catch (Exception var2) {
            ClientMessageLogger.error("Async texture reset failed for preset %s: %s", presetId, var2.getMessage());
         }
      });
   }

   public static void markPendingTextureReset(String presetId) {
      if (presetId != null) {
         ensureTextureFilesForPreset(presetId, false);
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures");
         Map<String, ExternalTextureManager.TextureBackup> presetBackups = PENDING_TEXTURE_BACKUPS.computeIfAbsent(presetId, k -> new HashMap<>());
         if (Files.exists(presetDir)) {
            try (Stream<Path> paths = Files.list(presetDir)) {
               paths.filter(x$0 -> Files.isRegularFile(x$0)).forEach(path -> {
                  try {
                     backupTextureFileIfNeeded(presetBackups, path);
                     Files.deleteIfExists(path);
                  } catch (IOException var3x) {
                  }
               });
            } catch (IOException var8) {
            }
         }

         if (presetId.equals(ConfigManager.getCurrentPresetId())) {
            reloadAsync();
         }
      }
   }

   public static void resetAllTextures() {
      ensureCommonTextureFiles(true);

      for (String presetId : ConfigManager.getPresetIds()) {
         resetTextures(presetId);
      }
   }

   public static void resetAllTexturesAsync() {
      TEXTURE_THREAD_POOL.submit(() -> {
         try {
            ClientMessageLogger.info("Async texture reset started for all presets.");
            ensureCommonTextureFiles(true);

            for (String presetId : ConfigManager.getPresetIds()) {
               ensureTextureFilesForPreset(presetId, true);
            }

            ClientMessageLogger.info("Async texture reset success for all presets.");
            reloadAsync();
         } catch (Exception var3) {
            ClientMessageLogger.error("Async texture reset failed: %s", var3.getMessage());
         }
      });
   }

   public static List<String> getAllTextureFileNames() {
      return DEFAULT_TEXTURE_LIST;
   }

   public static boolean isValidTextureName(String textureName) {
      if (textureName == null) {
         return false;
      } else {
         return DEFAULT_TEXTURE_SET.contains(textureName) ? true : isCustomTextureName(textureName);
      }
   }

   public static boolean isOfficialTextureName(String textureName) {
      return textureName != null && DEFAULT_TEXTURE_SET.contains(textureName);
   }

   public static boolean isCustomTextureName(String textureName) {
      return textureName == null ? false : CUSTOM_TEXTURE_PATTERN.matcher(textureName).matches();
   }

   public static boolean isVanillaTexturePath(String path) {
      return path != null && path.startsWith("minecraft:");
   }

   public static boolean isVanillaTextureAvailable(String path) {
      if (!isVanillaTexturePath(path)) {
         return false;
      } else {
         ResourceLocation vanilla = getVanillaTextureLocation(path);
         return vanilla == null ? false : Minecraft.getInstance().getResourceManager().getResource(vanilla).isPresent();
      }
   }

   public static ResourceLocation getVanillaTextureLocation(String path) {
      if (!isVanillaTexturePath(path)) {
         return null;
      } else {
         String raw = path.substring("minecraft:".length());
         String normalized = normalizeVanillaTexturePath(raw);
         return ResourceLocation.tryBuild("minecraft", normalized);
      }
   }

   public static ResourceLocation getTexture(String path) {
      ResourceLocation empty=new ResourceLocation("battlefield_ops","textures/gui/empty_preview.png");
      if(path==null||path.isBlank())return empty;
      if (isVanillaTexturePath(path)) {
         ResourceLocation vanilla = getVanillaTextureLocation(path);
         if (vanilla != null && Minecraft.getInstance().getResourceManager().getResource(vanilla).isPresent()) {
            applyTextureFilter(vanilla);
            return vanilla;
         }
         return empty;
      }

      String presetId = ConfigManager.getCurrentPresetId();
      String resolvedPath = IconTextureAnimationManager.resolveTexturePath(path);
      String cacheKey = presetId + ":" + resolvedPath;
      if (TEXTURE_CACHE.containsKey(cacheKey)) {
         ResourceLocation cached = TEXTURE_CACHE.get(cacheKey);
         applyTextureFilter(cached);
         return cached;
      } else if (loadExternalTexture(presetId, resolvedPath)) {
         ResourceLocation loaded = TEXTURE_CACHE.get(cacheKey);
         applyTextureFilter(loaded);
         return loaded;
      } else {
         ResourceLocation fallback = ResourceLocation.tryBuild("battlefield_ops", "icons/" + resolvedPath);
         if(fallback==null||Minecraft.getInstance().getResourceManager().getResource(fallback).isEmpty())
            fallback=new ResourceLocation("battlefield_ops","textures/gui/empty_preview.png");
         applyTextureFilter(fallback);
         return fallback;
      }
   }

   private static void applyTextureFilter(ResourceLocation textureLocation) {
      // No mip chain: these textures are uploaded through DynamicTexture, whose storage allocation gives
      // them mip level 0 only. Asking the sampler for mipmapped filtering here would read levels that were
      // never defined and render the icon black. See IconTextureFilterEffect for the full explanation.
      IconTextureFilterEffect.apply(textureLocation, false);
   }

   public static byte[] readTextureBytes(String presetId, String path) throws IOException {
      if (isVanillaTexturePath(path)) {
         ResourceLocation vanilla = getVanillaTextureLocation(path);
         if (vanilla != null) {
            try {
               byte[] var19;
               try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(vanilla).get()).open()) {
                  var19 = stream.readAllBytes();
               }

               return var19;
            } catch (Exception var16) {
               throw new IOException("Failed to read vanilla texture: " + path, var16);
            }
         }
      }

      Path file = resolveExternalTexturePath(presetId, path);
      if (Files.exists(file)) {
         return Files.readAllBytes(file);
      } else {
         ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "icons/" + path);

         try {
            byte[] var20;
            try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
               var20 = stream.readAllBytes();
            }

            return var20;
         } catch (Exception var14) {
            try (InputStream stream = ExternalTextureManager.class.getResourceAsStream("/assets/battlefield_ops/icons/" + path)) {
               if (stream != null) {
                  return stream.readAllBytes();
               } else {
                  throw new IOException("Failed to read texture: " + path, var14);
               }
            } catch (Exception var12) {
               throw new IOException("Failed to read texture: " + path, var14);
            }
         }
      }
   }

   public static List<String> getDefaultTexturePaths() {
      return Arrays.asList(DEFAULT_TEXTURES);
   }

   public static boolean replaceTextureWithBackup(String presetId, String textureName, Path sourcePath) {
      if (presetId == null || textureName == null || sourcePath == null) {
         return false;
      } else if (!DEFAULT_TEXTURE_SET.contains(textureName)) {
         return false;
      } else if (!Files.exists(sourcePath)) {
         return false;
      } else {
         ensureTextureFilesForPreset(presetId, false);
         Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve(textureName);

         try {
            if (!Files.exists(targetPath.getParent())) {
               Files.createDirectories(targetPath.getParent());
            }

            Map<String, ExternalTextureManager.TextureBackup> presetBackups = PENDING_TEXTURE_BACKUPS.computeIfAbsent(presetId, k -> new HashMap<>());
            if (!presetBackups.containsKey(textureName)) {
               if (Files.exists(targetPath)) {
                  byte[] original = Files.readAllBytes(targetPath);
                  presetBackups.put(textureName, new ExternalTextureManager.TextureBackup(true, original));
               } else {
                  presetBackups.put(textureName, new ExternalTextureManager.TextureBackup(false, null));
               }
            }

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            refreshTextureCache(presetId, textureName);
            invalidateTextureState(presetId, textureName);
            return true;
         } catch (IOException var6) {
            ClientMessageLogger.error("battlefield_ops.client.texture.replace_fail", presetId, textureName, var6.getMessage());
            return false;
         }
      }
   }

   public static boolean resetTextureWithBackup(String presetId, String textureName) {
      return resetTextureWithBackupInternal(presetId, textureName, true);
   }

   public static boolean resetTextureWithBackupSilent(String presetId, String textureName) {
      return resetTextureWithBackupInternal(presetId, textureName, false);
   }

   private static boolean resetTextureWithBackupInternal(String presetId, String textureName, boolean logErrors) {
      if (presetId == null || textureName == null) {
         return false;
      } else if (!DEFAULT_TEXTURE_SET.contains(textureName)) {
         return false;
      } else {
         ensureTextureFilesForPreset(presetId, false);
         Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve(textureName);

         try {
            if (!Files.exists(targetPath.getParent())) {
               Files.createDirectories(targetPath.getParent());
            }

            Map<String, ExternalTextureManager.TextureBackup> presetBackups = PENDING_TEXTURE_BACKUPS.computeIfAbsent(presetId, k -> new HashMap<>());
            if (!presetBackups.containsKey(textureName)) {
               if (Files.exists(targetPath)) {
                  byte[] original = Files.readAllBytes(targetPath);
                  presetBackups.put(textureName, new ExternalTextureManager.TextureBackup(true, original));
               } else {
                  presetBackups.put(textureName, new ExternalTextureManager.TextureBackup(false, null));
               }
            }

            Files.deleteIfExists(targetPath);
            if (logErrors) {
               refreshTextureCache(presetId, textureName);
            } else {
               refreshTextureCacheAsync(presetId, textureName);
            }

            invalidateTextureState(presetId, textureName);
            return true;
         } catch (IOException var6) {
            if (logErrors) {
               ClientMessageLogger.error("battlefield_ops.client.texture.reset_error", presetId, textureName, var6.getMessage());
            }

            return false;
         }
      }
   }

   private static void backupTextureFileIfNeeded(Map<String, ExternalTextureManager.TextureBackup> presetBackups, Path path) throws IOException {
      String fileName = path.getFileName().toString();
      if (!presetBackups.containsKey(fileName)) {
         if (Files.exists(path)) {
            byte[] original = Files.readAllBytes(path);
            presetBackups.put(fileName, new ExternalTextureManager.TextureBackup(true, original));
         } else {
            presetBackups.put(fileName, new ExternalTextureManager.TextureBackup(false, null));
         }
      }
   }

   public static boolean resetTexturesForElement(String presetId, String elementId) {
      if (presetId != null && elementId != null) {
         if (!ElementTextureDefinition.hasTextures(elementId)) {
            return false;
         } else {
            boolean any = false;

            for (String texture : ElementTextureDefinition.getTextures(elementId)) {
               String fileName = ElementTextureDefinition.getTextureFileName(presetId, elementId, texture);
               if (fileName != null) {
                  any = resetTextureWithBackup(presetId, fileName) || any;
               }
            }

            return any;
         }
      } else {
         return false;
      }
   }

   public static void confirmPendingTextureReplacements() {
      PENDING_TEXTURE_BACKUPS.clear();
   }

   public static void revertPendingTextureReplacements() {
      if (!PENDING_TEXTURE_BACKUPS.isEmpty()) {
         for (Entry<String, Map<String, ExternalTextureManager.TextureBackup>> presetEntry : PENDING_TEXTURE_BACKUPS.entrySet()) {
            String presetId = presetEntry.getKey();

            for (Entry<String, ExternalTextureManager.TextureBackup> entry : presetEntry.getValue().entrySet()) {
               String textureName = entry.getKey();
               ExternalTextureManager.TextureBackup backup = entry.getValue();
               Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve(textureName);

               try {
                  if (backup.existed) {
                     Files.write(targetPath, backup.data);
                  } else {
                     Files.deleteIfExists(targetPath);
                  }

                  refreshTextureCache(presetId, textureName);
                  invalidateTextureState(presetId, textureName);
               } catch (IOException var9) {
                  ClientMessageLogger.error("battlefield_ops.client.texture.revert_fail", presetId, textureName, var9.getMessage());
               }
            }
         }

         PENDING_TEXTURE_BACKUPS.clear();
      }
   }

   public static void revertPendingTextureReplacementsForElement(String presetId, String elementId) {
      if (presetId != null && elementId != null) {
         Map<String, ExternalTextureManager.TextureBackup> presetBackups = PENDING_TEXTURE_BACKUPS.get(presetId);
         if (presetBackups != null && !presetBackups.isEmpty()) {
            if (ElementTextureDefinition.hasTextures(elementId)) {
               for (String texture : ElementTextureDefinition.getTextures(elementId)) {
                  String fileName = ElementTextureDefinition.getTextureFileName(presetId, elementId, texture);
                  if (fileName != null) {
                     ExternalTextureManager.TextureBackup backup = presetBackups.remove(fileName);
                     if (backup != null) {
                        Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve(fileName);

                        try {
                           if (backup.existed) {
                              Files.write(targetPath, backup.data);
                           } else {
                              Files.deleteIfExists(targetPath);
                           }

                           refreshTextureCache(presetId, fileName);
                           invalidateTextureState(presetId, fileName);
                        } catch (IOException var9) {
                           ClientMessageLogger.error("battlefield_ops.client.texture.revert_fail", presetId, fileName, var9.getMessage());
                        }
                     }
                  }
               }

               if (presetBackups.isEmpty()) {
                  PENDING_TEXTURE_BACKUPS.remove(presetId);
               }
            }
         }
      }
   }

   public static boolean hasPendingTextureChanges() {
      for (Map<String, ExternalTextureManager.TextureBackup> presetBackups : PENDING_TEXTURE_BACKUPS.values()) {
         if (presetBackups != null && !presetBackups.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static boolean hasPendingTextureChangesForElement(String presetId, String elementId) {
      if (presetId != null && elementId != null) {
         Map<String, ExternalTextureManager.TextureBackup> presetBackups = PENDING_TEXTURE_BACKUPS.get(presetId);
         if (presetBackups != null && !presetBackups.isEmpty()) {
            if (!ElementTextureDefinition.hasTextures(elementId)) {
               return false;
            } else {
               for (String texture : ElementTextureDefinition.getTextures(elementId)) {
                  String fileName = ElementTextureDefinition.getTextureFileName(presetId, elementId, texture);
                  if (fileName != null && presetBackups.containsKey(fileName)) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isTextureModified(String presetId, String textureName) {
      if (presetId == null || textureName == null) {
         return false;
      } else if (!DEFAULT_TEXTURE_SET.contains(textureName)) {
         return false;
      } else {
         String key = presetId + ":" + textureName;
         ExternalTextureManager.TextureState cachedState = TEXTURE_STATE_CACHE.get(key);
         if (cachedState != null) {
            return cachedState.modified;
         } else if (TEXTURE_STATE_LOADING.contains(key)) {
            return false;
         } else {
            TEXTURE_STATE_LOADING.add(key);
            TEXTURE_THREAD_POOL.submit(() -> {
               try {
                  Path targetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve(textureName);
                  if (!Files.exists(targetPath)) {
                     return;
                  }

                  byte[] defaultBytes = getDefaultTextureBytes(textureName);
                  if (defaultBytes != null) {
                     long lastModified = Files.getLastModifiedTime(targetPath).toMillis();
                     long size = Files.size(targetPath);
                     byte[] currentBytes = Files.readAllBytes(targetPath);
                     boolean modified = !Arrays.equals(currentBytes, defaultBytes);
                     TEXTURE_STATE_CACHE.put(key, new ExternalTextureManager.TextureState(lastModified, size, modified));
                     return;
                  }
               } catch (IOException var14) {
                  return;
               } finally {
                  TEXTURE_STATE_LOADING.remove(key);
               }
            });
            return false;
         }
      }
   }

   private static void ensureAllPresetsTextureFiles(boolean forceReset) {
      ensureCommonTextureFiles(forceReset);
      Set<String> presets = ConfigManager.getPresetIds();
      ensureTextureFilesForPreset("00001", forceReset);
      ensureTextureFilesForPreset("00002", forceReset);

      for (String presetId : presets) {
         ensureTextureFilesForPreset(presetId, forceReset);
      }
   }

   public static void ensureTextureFilesForPreset(String presetId) {
      ensureTextureFilesForPreset(presetId, false);
   }

   public static void ensureTextureFilesForPreset(String presetId, boolean forceReset) {
      try {
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures");
         if (!Files.exists(presetDir)) {
            Files.createDirectories(presetDir);
         }

         for (String texturePath : DEFAULT_TEXTURES) {
            Path targetPath = presetDir.resolve(texturePath);
            if (!Files.exists(targetPath.getParent())) {
               Files.createDirectories(targetPath.getParent());
            }

            if (forceReset) {
               Files.deleteIfExists(targetPath);
            }
         }
      } catch (IOException var8) {
         ClientMessageLogger.error("battlefield_ops.client.texture.init_fail", presetId, var8.getMessage());
      }
   }

   private static int loadTexturesForPreset(String presetId) {
      int loadedCount = 0;

      for (String path : DEFAULT_TEXTURES) {
         if (loadExternalTexture(presetId, path)) {
            loadedCount++;
         }
      }

      return loadedCount;
   }

   private static boolean loadExternalTexture(String presetId, String path) {
      Path file = resolveExternalTexturePath(presetId, path);
      if (!Files.exists(file)) {
         return false;
      } else {
         try {
            boolean var8;
            try (InputStream stream = new FileInputStream(file.toFile())) {
               NativeImage image = NativeImage.read(stream);
               DynamicTexture texture = new DynamicTexture(image);
               String dynamicName = "battlefield_ops_external_" + presetId + "_" + path.replace("/", "_").replace(".", "_");
               ResourceLocation dynamicLoc = Minecraft.getInstance().getTextureManager().register(dynamicName, texture);
               TEXTURE_CACHE.put(presetId + ":" + path, dynamicLoc);
               texture.setFilter(
                     cn.bfops.killicon.client.config.ClientConfigManager.isEnableIconAntialiasing(), false);
               var8 = true;
            }

            return var8;
         } catch (IOException var11) {
            ClientMessageLogger.chatError("battlefield_ops.client.texture.load_fail", presetId, path, var11.getMessage());
            return false;
         }
      }
   }

   private static void clearCache() {
      for (ResourceLocation loc : TEXTURE_CACHE.values()) {
         Minecraft.getInstance().getTextureManager().release(loc);
      }

      TEXTURE_CACHE.clear();
   }

   /**
    * Re-applies the current filtering mode to every texture already resolved.
    *
    * <p>Filtering is baked into the texture object the first time a resource location is resolved, so
    * flipping the antialiasing switch otherwise has no visible effect until the preset is reloaded and the
    * cache dropped. Re-applying makes the toggle take effect immediately, which is what a user expects from
    * a switch in the config UI.
    */
   public static void reapplyTextureFiltering() {
      for (ResourceLocation loc : TEXTURE_CACHE.values()) {
         applyTextureFilter(loc);
      }
   }

   private static void refreshTextureCache(String presetId, String textureName) {
      String cacheKey = presetId + ":" + textureName;
      ResourceLocation cached = TEXTURE_CACHE.remove(cacheKey);
      if (cached != null) {
         Minecraft.getInstance().getTextureManager().release(cached);
      }

      loadExternalTexture(presetId, textureName);
   }

   private static void refreshTextureCacheAsync(String presetId, String textureName) {
      String cacheKey = presetId + ":" + textureName;
      Minecraft.getInstance().execute(() -> {
         ResourceLocation cached = TEXTURE_CACHE.remove(cacheKey);
         if (cached != null) {
            Minecraft.getInstance().getTextureManager().release(cached);
         }
      });
      TEXTURE_THREAD_POOL.submit(() -> {
         Path file = resolveExternalTexturePath(presetId, textureName);
         if (Files.exists(file)) {
            try (InputStream stream = new FileInputStream(file.toFile())) {
               NativeImage image = NativeImage.read(stream);
               Minecraft.getInstance().execute(() -> {
                  try {
                     DynamicTexture texture = new DynamicTexture(image);
                     String dynamicName = "battlefield_ops_external_" + presetId + "_" + textureName.replace("/", "_").replace(".", "_");
                     ResourceLocation dynamicLoc = Minecraft.getInstance().getTextureManager().register(dynamicName, texture);
                     TEXTURE_CACHE.put(cacheKey, dynamicLoc);
                  } catch (Exception var7) {
                     image.close();
                  }
               });
            } catch (IOException var9) {
            }
         }
      });
   }

   private static byte[] getDefaultTextureBytes(String textureName) {
      byte[] cached = DEFAULT_TEXTURE_BYTES.get(textureName);
      if (cached != null) {
         return cached;
      } else {
         ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "icons/" + textureName);

         try {
            byte[] var14;
            try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
               byte[] data = stream.readAllBytes();
               DEFAULT_TEXTURE_BYTES.put(textureName, data);
               var14 = data;
            }

            return var14;
         } catch (Exception var12) {
            try (InputStream stream = ExternalTextureManager.class.getResourceAsStream("/assets/battlefield_ops/icons/" + textureName)) {
               if (stream != null) {
                  byte[] data = stream.readAllBytes();
                  DEFAULT_TEXTURE_BYTES.put(textureName, data);
                  return data;
               } else {
                  return null;
               }
            } catch (Exception var10) {
               return null;
            }
         }
      }
   }

   public static ExternalTextureManager.TextureDimensions getTextureDimensions(String presetId, String path) {
      String key = presetId + ":" + path;
      if (TEXTURE_DIMENSIONS.containsKey(key)) {
         return TEXTURE_DIMENSIONS.get(key);
      } else if (isVanillaTexturePath(path)) {
         ResourceLocation vanilla = getVanillaTextureLocation(path);
         if (vanilla != null) {
            try {
               ExternalTextureManager.TextureDimensions var41;
               try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(vanilla).get()).open()) {
                  NativeImage image = NativeImage.read(stream);

                  try {
                     ExternalTextureManager.TextureDimensions dims = new ExternalTextureManager.TextureDimensions(image.getWidth(), image.getHeight());
                     TEXTURE_DIMENSIONS.put(key, dims);
                     var41 = dims;
                  } catch (Throwable var22) {
                     if (image != null) {
                        try {
                           image.close();
                        } catch (Throwable var15) {
                           var22.addSuppressed(var15);
                        }
                     }

                     throw var22;
                  }

                  if (image != null) {
                     image.close();
                  }
               }

               return var41;
            } catch (Exception var24) {
            }
         }

         ExternalTextureManager.TextureDimensions dims = new ExternalTextureManager.TextureDimensions(16, 16);
         TEXTURE_DIMENSIONS.put(key, dims);
         return dims;
      } else {
         Path file = resolveExternalTexturePath(presetId, path);
         if (Files.exists(file)) {
            try {
               ExternalTextureManager.TextureDimensions var40;
               try (InputStream stream = new FileInputStream(file.toFile())) {
                  NativeImage image = NativeImage.read(stream);

                  try {
                     ExternalTextureManager.TextureDimensions dims = new ExternalTextureManager.TextureDimensions(image.getWidth(), image.getHeight());
                     TEXTURE_DIMENSIONS.put(key, dims);
                     var40 = dims;
                  } catch (Throwable var27) {
                     if (image != null) {
                        try {
                           image.close();
                        } catch (Throwable var17) {
                           var27.addSuppressed(var17);
                        }
                     }

                     throw var27;
                  }

                  if (image != null) {
                     image.close();
                  }
               }

               return var40;
            } catch (IOException var29) {
            }
         }

         ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "icons/" + path);

         try {
            ExternalTextureManager.TextureDimensions var42;
            try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
               NativeImage image = NativeImage.read(stream);

               try {
                  ExternalTextureManager.TextureDimensions dims = new ExternalTextureManager.TextureDimensions(image.getWidth(), image.getHeight());
                  TEXTURE_DIMENSIONS.put(key, dims);
                  var42 = dims;
               } catch (Throwable var21) {
                  if (image != null) {
                     try {
                        image.close();
                     } catch (Throwable var13) {
                        var21.addSuppressed(var13);
                     }
                  }

                  throw var21;
               }

               if (image != null) {
                  image.close();
               }
            }

            return var42;
         } catch (Exception var26) {
            try (InputStream stream = ExternalTextureManager.class.getResourceAsStream("/assets/battlefield_ops/icons/" + path)) {
               if (stream != null) {
                  NativeImage image = NativeImage.read(stream);

                  ExternalTextureManager.TextureDimensions var9;
                  try {
                     ExternalTextureManager.TextureDimensions dims = new ExternalTextureManager.TextureDimensions(image.getWidth(), image.getHeight());
                     TEXTURE_DIMENSIONS.put(key, dims);
                     var9 = dims;
                  } catch (Throwable var18) {
                     if (image != null) {
                        try {
                           image.close();
                        } catch (Throwable var11) {
                           var18.addSuppressed(var11);
                        }
                     }

                     throw var18;
                  }

                  if (image != null) {
                     image.close();
                  }

                  return var9;
               } else {
                  return new ExternalTextureManager.TextureDimensions(0, 0);
               }
            } catch (Exception var20) {
               return new ExternalTextureManager.TextureDimensions(0, 0);
            }
         }
      }
   }

   private static String normalizeVanillaTexturePath(String raw) {
      if (raw != null && !raw.isEmpty()) {
         if (raw.startsWith("textures/")) {
            return raw;
         } else {
            String normalized;
            if (!raw.startsWith("item/") && !raw.startsWith("block/") && !raw.startsWith("gui/")) {
               normalized = "textures/item/" + raw;
            } else {
               normalized = "textures/" + raw;
            }

            if (!normalized.endsWith(".png")) {
               normalized = normalized + ".png";
            }

            return normalized;
         }
      } else {
         return "textures/misc/missing_texture.png";
      }
   }

   public static List<String> getCustomTextureFileNames(String presetId) {
      if (presetId == null) {
         return Collections.emptyList();
      } else {
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures");
         if (!Files.exists(presetDir)) {
            return Collections.emptyList();
         } else {
            try {
               return Files.list(presetDir)
                  .filter(path -> Files.isRegularFile(path))
                  .map(path -> path.getFileName().toString())
                  .filter(ExternalTextureManager::isCustomTextureName)
                  .sorted((a, b) -> {
                     Matcher ma = CUSTOM_TEXTURE_PATTERN.matcher(a);
                     Matcher mb = CUSTOM_TEXTURE_PATTERN.matcher(b);
                     if (ma.matches() && mb.matches()) {
                        int ia = Integer.parseInt(ma.group(1));
                        int ib = Integer.parseInt(mb.group(1));
                        return Integer.compare(ia, ib);
                     } else {
                        return a.compareToIgnoreCase(b);
                     }
                  })
                  .toList();
            } catch (IOException var3) {
               return Collections.emptyList();
            }
         }
      }
   }

   public static Map<String, String> getCustomTextureLabels(String presetId) {
      if (presetId == null) {
         return Collections.emptyMap();
      } else {
         Path labelsPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve("custom_labels.json");
         if (!Files.exists(labelsPath)) {
            return Collections.emptyMap();
         } else {
            try {
               String content = Files.readString(labelsPath);
               JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
               Map<String, String> map = new HashMap<>();

               for (String key : obj.keySet()) {
                  map.put(key, obj.get(key).getAsString());
               }

               return map;
            } catch (Exception var7) {
               return Collections.emptyMap();
            }
         }
      }
   }

   public static String createCustomTextureFromFile(
      String presetId, Path sourcePath, String originalName, boolean gifDerived, Integer frameCount, Integer intervalMs, String orientation
   ) {
      if (presetId != null && sourcePath != null && Files.exists(sourcePath)) {
         Path presetDir = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures");

         try {
            if (!Files.exists(presetDir)) {
               Files.createDirectories(presetDir);
            }

            int nextIndex = 1;

            for (String name : getCustomTextureFileNames(presetId)) {
               Matcher matcher = CUSTOM_TEXTURE_PATTERN.matcher(name);
               if (matcher.matches()) {
                  int index = Integer.parseInt(matcher.group(1));
                  if (index >= nextIndex) {
                     nextIndex = index + 1;
                  }
               }
            }

            String fileName = "custom_" + nextIndex + ".png";
            Path targetPath = presetDir.resolve(fileName);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            refreshTextureCache(presetId, fileName);
            invalidateTextureState(presetId, fileName);
            updateCustomLabel(presetId, fileName, originalName);
            ExternalTextureManager.TextureDimensions logicalDimensions = calculateLogicalTextureDimensions(targetPath, gifDerived, frameCount, orientation);
            updateCustomMeta(presetId, fileName, gifDerived, frameCount, intervalMs, orientation, logicalDimensions);
            return fileName;
         } catch (IOException var13) {
            ClientMessageLogger.error("battlefield_ops.client.texture.replace_fail", presetId, sourcePath.toString(), var13.getMessage());
            return null;
         }
      } else {
         return null;
      }
   }

   public static String createCustomTextureFromFile(String presetId, Path sourcePath, String originalName) {
      return createCustomTextureFromFile(presetId, sourcePath, originalName, false, null, null, null);
   }

   private static void updateCustomLabel(String presetId, String fileName, String originalName) {
      if (presetId != null && fileName != null) {
         String label = originalName == null ? fileName : originalName;
         Path labelsPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve("custom_labels.json");
         JsonObject obj = new JsonObject();
         if (Files.exists(labelsPath)) {
            try {
               String content = Files.readString(labelsPath);
               obj = JsonParser.parseString(content).getAsJsonObject();
            } catch (Exception var8) {
            }
         }

         obj.addProperty(fileName, label);

         try {
            Files.writeString(labelsPath, GSON.toJson(obj));
         } catch (IOException var7) {
         }
      }
   }

   private static void updateCustomMeta(
      String presetId,
      String fileName,
      boolean gifDerived,
      Integer frameCount,
      Integer intervalMs,
      String orientation,
      ExternalTextureManager.TextureDimensions logicalDimensions
   ) {
      if (presetId != null && fileName != null) {
         Path metaPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve("custom_meta.json");
         JsonObject obj = new JsonObject();
         if (Files.exists(metaPath)) {
            try {
               String content = Files.readString(metaPath);
               obj = JsonParser.parseString(content).getAsJsonObject();
            } catch (Exception var12) {
            }
         }

         JsonObject meta = new JsonObject();
         meta.addProperty("gif", gifDerived);
         if (frameCount != null) {
            meta.addProperty("frames", frameCount);
         }

         if (intervalMs != null) {
            meta.addProperty("interval", intervalMs);
         }

         if (orientation != null) {
            meta.addProperty("orientation", orientation);
         }

         if (logicalDimensions != null && logicalDimensions.width > 0 && logicalDimensions.height > 0) {
            meta.addProperty("width", logicalDimensions.width);
            meta.addProperty("height", logicalDimensions.height);
         }

         obj.add(fileName, meta);

         try {
            Files.writeString(metaPath, GSON.toJson(obj));
         } catch (IOException var11) {
         }
      }
   }

   public static boolean isGifDerivedCustomTexture(String presetId, String fileName) {
      JsonObject meta = getCustomMeta(presetId, fileName);
      return meta != null && meta.has("gif") && meta.get("gif").getAsBoolean();
   }

   public static JsonObject getCustomMeta(String presetId, String fileName) {
      if (presetId != null && fileName != null) {
         Path metaPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve("custom_meta.json");
         if (!Files.exists(metaPath)) {
            return null;
         } else {
            try {
               String content = Files.readString(metaPath);
               JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
               if (obj.has(fileName) && obj.get(fileName).isJsonObject()) {
                  return obj.getAsJsonObject(fileName);
               }
            } catch (Exception var5) {
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public static ExternalTextureManager.TextureDimensions getLogicalTextureDimensions(String presetId, String path) {
      if (path == null) {
         return new ExternalTextureManager.TextureDimensions(0, 0);
      } else {
         if (isCustomTextureName(path)) {
            JsonObject meta = getCustomMeta(presetId, path);
            if (meta != null && meta.has("width") && meta.has("height")) {
               int width = meta.get("width").getAsInt();
               int height = meta.get("height").getAsInt();
               if (width > 0 && height > 0) {
                  return new ExternalTextureManager.TextureDimensions(width, height);
               }
            }
         }

         return getTextureDimensions(presetId, path);
      }
   }

   private static ExternalTextureManager.TextureDimensions calculateLogicalTextureDimensions(
      Path texturePath, boolean gifDerived, Integer frameCount, String orientation
   ) {
      ExternalTextureManager.TextureDimensions actualDimensions = readTextureDimensions(texturePath);
      if (actualDimensions.width > 0 && actualDimensions.height > 0) {
         if (gifDerived && frameCount != null && frameCount > 1) {
            String normalizedOrientation = orientation == null ? "vertical" : orientation.toLowerCase(Locale.ROOT);
            return "horizontal".equals(normalizedOrientation)
               ? new ExternalTextureManager.TextureDimensions(Math.max(1, actualDimensions.width / frameCount), actualDimensions.height)
               : new ExternalTextureManager.TextureDimensions(actualDimensions.width, Math.max(1, actualDimensions.height / frameCount));
         } else {
            return actualDimensions;
         }
      } else {
         return actualDimensions;
      }
   }

   private static ExternalTextureManager.TextureDimensions readTextureDimensions(Path texturePath) {
      if (texturePath != null && Files.exists(texturePath)) {
         try (InputStream stream = Files.newInputStream(texturePath);
              var input = new javax.imageio.stream.MemoryCacheImageInputStream(stream)) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (readers.hasNext()) {
               var reader = readers.next();
               try {
                  reader.setInput(input, true, true);
                  return new ExternalTextureManager.TextureDimensions(reader.getWidth(0), reader.getHeight(0));
               } finally { reader.dispose(); }
            }
         } catch (IOException ignored) {}
      }
      return new ExternalTextureManager.TextureDimensions(0, 0);
   }

   private static void invalidateTextureState(String presetId, String textureName) {
      TEXTURE_STATE_CACHE.remove(presetId + ":" + textureName);
      TEXTURE_DIMENSIONS.remove(presetId + ":" + textureName);
   }

   private static void ensureCommonTextureFiles(boolean forceReset) {
      try {
         if (!Files.exists(COMMON_TEXTURES_DIR)) {
            Files.createDirectories(COMMON_TEXTURES_DIR);
         }

         for (String texturePath : DEFAULT_TEXTURES) {
            Path targetPath = COMMON_TEXTURES_DIR.resolve(texturePath);
            if (!Files.exists(targetPath.getParent())) {
               Files.createDirectories(targetPath.getParent());
            }

            if (forceReset || !Files.exists(targetPath)) {
               ResourceLocation resourceLocation = new ResourceLocation("battlefield_ops", "icons/" + texturePath);

               try (InputStream stream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).get()).open()) {
                  Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
               } catch (Exception var16) {
                  Exception e = var16;

                  try (InputStream streamx = ExternalTextureManager.class.getResourceAsStream("/assets/battlefield_ops/icons/" + texturePath)) {
                     if (streamx != null) {
                        Files.copy(streamx, targetPath, StandardCopyOption.REPLACE_EXISTING);
                     } else {
                        ClientMessageLogger.error("battlefield_ops.client.texture.extract_fail", texturePath, e.getMessage());
                     }
                  } catch (Exception var14) {
                     ClientMessageLogger.error("battlefield_ops.client.texture.extract_fail", texturePath, var14.getMessage());
                  }
               }
            }
         }
      } catch (IOException var17) {
         ClientMessageLogger.error("battlefield_ops.client.texture.init_fail", "common", var17.getMessage());
      }
   }

   private static Path resolveExternalTexturePath(String presetId, String path) {
      Path presetPath = CONFIG_ASSETS_DIR.resolve(presetId).resolve("textures").resolve(path);
      return Files.exists(presetPath) ? presetPath : COMMON_TEXTURES_DIR.resolve(path);
   }

   private static final class TextureBackup {
      private final boolean existed;
      private final byte[] data;

      private TextureBackup(boolean existed, byte[] data) {
         this.existed = existed;
         this.data = data;
      }
   }

   public static class TextureDimensions {
      public final int width;
      public final int height;

      public TextureDimensions(int width, int height) {
         this.width = width;
         this.height = height;
      }
   }

   private static final class TextureState {
      private final long lastModified;
      private final long size;
      private final boolean modified;

      private TextureState(long lastModified, long size, boolean modified) {
         this.lastModified = lastModified;
         this.size = size;
         this.modified = modified;
      }
   }
}
