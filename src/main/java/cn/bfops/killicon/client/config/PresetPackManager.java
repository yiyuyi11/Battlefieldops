package cn.bfops.killicon.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.Random;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.util.ClientMessageLogger;

public class PresetPackManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_DIR = ClientBridge.loader().getConfigDir().resolve("battlefield_ops");
   private static final Path EXPORT_DIR = CONFIG_DIR.resolve("export");
   private static final Path ASSETS_DIR = CONFIG_DIR.resolve("assets");

   public static Path getExportDir() {
      return EXPORT_DIR;
   }

   public static boolean exportPreset(String presetId, String fileName) {
      try {
         if (!Files.exists(EXPORT_DIR)) {
            Files.createDirectories(EXPORT_DIR);
         }

         if (!fileName.toLowerCase().endsWith(".gdpack")) {
            fileName = fileName + ".gdpack";
         }

         Path exportPath = EXPORT_DIR.resolve(fileName);
         ElementConfigManager.ElementPreset preset = ElementConfigManager.getActivePresets().get(presetId);
         if (preset == null) {
            ClientMessageLogger.chatError("battlefield_ops.client.config.export.preset_not_found", presetId);
            return false;
         } else {
            JsonObject presetJson = new JsonObject();
            presetJson.addProperty("original_id", presetId);
            presetJson.addProperty("display_name", preset.getDisplayName());
            JsonObject elements = new JsonObject();

            for (Entry<String, JsonObject> entry : preset.getElementConfigs().entrySet()) {
               elements.add(entry.getKey(), (JsonElement)entry.getValue());
            }

            presetJson.add("elements", elements);

            try (final ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(exportPath))) {
               ZipEntry configEntry = new ZipEntry("preset.json");
               zos.putNextEntry(configEntry);
               zos.write(GSON.toJson(presetJson).getBytes(StandardCharsets.UTF_8));
               zos.closeEntry();
               final Path presetAssetsDir = ASSETS_DIR.resolve(presetId);
               if (Files.exists(presetAssetsDir)) {
                  Files.walkFileTree(presetAssetsDir, new SimpleFileVisitor<Path>() {
                     public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String relativePath = presetAssetsDir.relativize(file).toString().replace("\\", "/");
                        ZipEntry assetEntry = new ZipEntry("assets/" + relativePath);
                        zos.putNextEntry(assetEntry);
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                     }
                  });
               }
            }

            ClientMessageLogger.chatSuccess("battlefield_ops.client.config.export.success", fileName);
            return true;
         }
      } catch (Exception var11) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.export.fail", var11.getMessage());
         return false;
      }
   }

   public static String checkImport(File file) {
      try {
         String var3;
         try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            ZipEntry configEntry = zipFile.getEntry("preset.json");
            if (configEntry == null) {
               return "invalid_format";
            }

            var3 = "valid";
         }

         return var3;
      } catch (Exception var6) {
         return "error";
      }
   }

   public static String getPresetIdFromPack(File file) {
      try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
         ZipEntry configEntry = zipFile.getEntry("preset.json");
         if (configEntry == null) {
            return null;
         } else {
            try (InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(configEntry), StandardCharsets.UTF_8)) {
               JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
               return json.has("original_id") ? json.get("original_id").getAsString() : null;
            }
         }
      } catch (Exception var10) {
         return null;
      }
   }

   public static boolean importPreset(File file, String targetId) {
      try {
         boolean var25;
         try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            ZipEntry configEntry = zipFile.getEntry("preset.json");
            if (configEntry == null) {
               throw new IOException("Missing preset.json");
            }

            InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(configEntry), StandardCharsets.UTF_8);

            JsonObject presetJson;
            try {
               presetJson = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            } catch (Throwable var17) {
               try {
                  reader.close();
               } catch (Throwable var16) {
                  var17.addSuppressed(var16);
               }

               throw var17;
            }

            reader.close();
            ElementConfigManager.ElementPreset var21 = new ElementConfigManager.ElementPreset();
            if (presetJson.has("display_name")) {
               var21.setDisplayName(presetJson.get("display_name").getAsString());
            }

            if (presetJson.has("elements")) {
               JsonObject elements = presetJson.getAsJsonObject("elements");

               for (String key : elements.keySet()) {
                  JsonObject config = elements.getAsJsonObject(key);
                  ElementConfigManager.completeMissingKeys(targetId, key, config);
                  var21.addElementConfig(key, config);
               }
            }

            ElementConfigManager.getActivePresets().put(targetId, var21);
            ElementConfigManager.saveConfig();
            ElementConfigManager.ensurePresetAssets(targetId);
            Path targetAssetsDir = ASSETS_DIR.resolve(targetId);
            if (!Files.exists(targetAssetsDir)) {
               Files.createDirectories(targetAssetsDir);
            }

            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
               ZipEntry entry = entries.nextElement();
               if (entry.getName().startsWith("assets/") && !entry.isDirectory()) {
                  String relativePath = entry.getName().substring("assets/".length());
                  Path targetPath = targetAssetsDir.resolve(relativePath);
                  if (!Files.exists(targetPath.getParent())) {
                     Files.createDirectories(targetPath.getParent());
                  }

                  try (InputStream is = zipFile.getInputStream(entry)) {
                     Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                  }
               }
            }

            ClientMessageLogger.chatSuccess("battlefield_ops.client.config.import.success", targetId);
            var25 = true;
         }

         return var25;
      } catch (Exception var20) {
         ClientMessageLogger.chatError("battlefield_ops.client.config.import.fail", var20.getMessage());
         return false;
      }
   }

   public static String generateRandomId() {
      Random rand = new Random();

      String id;
      do {
         id = String.format("%05d", rand.nextInt(100000));
      } while (ElementConfigManager.getActivePresets().containsKey(id));

      return id;
   }
}
