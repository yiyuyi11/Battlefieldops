package cn.bfops.killicon.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import cn.bfops.killicon.client.bridge.ClientBridge;

public final class ScoreboardLoadoutConfigManager {
   public static final String TEAM_ALL = "__all__";
   public static final String TEAM_CONQUEST_SOLO = "__conquest_solo__";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String CONQUEST_MOD_ID = "gd656conquest";
   private static ScoreboardLoadoutConfigManager.DisplayMode persistedMode = ScoreboardLoadoutConfigManager.DisplayMode.AUTO;
   private static final String[] persistedPanelTeams = new String[]{"__all__", "__all__", "__all__", "__all__"};
   private static ScoreboardLoadoutConfigManager.DisplayMode tempMode = ScoreboardLoadoutConfigManager.DisplayMode.AUTO;
   private static final String[] tempPanelTeams = new String[]{"__all__", "__all__", "__all__", "__all__"};
   private static boolean isEditing = false;
   private static String loadedContextId = null;
   private static int serverSuggestedColumns = 1;
   private static final String[] serverSuggestedPanelTeams = new String[]{"__all__", "__all__", "__all__", "__all__"};

   private ScoreboardLoadoutConfigManager() {
   }

   public static void startEditing() {
      ensureContextLoaded();
      tempMode = persistedMode;
      copyArray(persistedPanelTeams, tempPanelTeams);
      isEditing = true;
   }

   public static void saveChanges() {
      if (isEditing) {
         persistedMode = tempMode;
         copyArray(tempPanelTeams, persistedPanelTeams);
         isEditing = false;
         saveCurrentContext();
      }
   }

   public static void discardChanges() {
      if (isEditing) {
         isEditing = false;
         tempMode = persistedMode;
         copyArray(persistedPanelTeams, tempPanelTeams);
      }
   }

   public static boolean hasUnsavedChanges() {
      if (!isEditing) {
         return false;
      } else if (tempMode != persistedMode) {
         return true;
      } else {
         for (int i = 0; i < 4; i++) {
            if (!normalizeTeam(tempPanelTeams[i]).equals(normalizeTeam(persistedPanelTeams[i]))) {
               return true;
            }
         }

         return false;
      }
   }

   public static void setDisplayMode(ScoreboardLoadoutConfigManager.DisplayMode mode) {
      ensureContextLoaded();
      if (mode != null) {
         if (!isDisplayModeLockedToAuto() || mode == ScoreboardLoadoutConfigManager.DisplayMode.AUTO) {
            if (isEditing) {
               tempMode = mode;
            } else {
               persistedMode = mode;
               saveCurrentContext();
            }
         }
      }
   }

   public static ScoreboardLoadoutConfigManager.DisplayMode getDisplayMode() {
      ensureContextLoaded();
      if (isDisplayModeLockedToAuto()) {
         return ScoreboardLoadoutConfigManager.DisplayMode.AUTO;
      } else {
         return isEditing ? tempMode : persistedMode;
      }
   }

   public static boolean isDisplayModeLockedToAuto() {
      return ClientBridge.loader().isModLoaded("gd656conquest");
   }

   public static void setPanelTeamBinding(int panelIndex, String teamName) {
      ensureContextLoaded();
      if (panelIndex >= 0 && panelIndex < 4) {
         String normalized = normalizeTeam(teamName);
         if (isEditing) {
            tempPanelTeams[panelIndex] = normalized;
         } else {
            persistedPanelTeams[panelIndex] = normalized;
            saveCurrentContext();
         }
      }
   }

   public static String getPanelTeamBinding(int panelIndex) {
      ensureContextLoaded();
      return panelIndex >= 0 && panelIndex < 4 ? normalizeTeam(isEditing ? tempPanelTeams[panelIndex] : persistedPanelTeams[panelIndex]) : "__all__";
   }

   public static void setServerSuggestedColumns(int columns) {
      if (columns != 2 && columns != 4) {
         serverSuggestedColumns = 1;
      } else {
         serverSuggestedColumns = columns;
      }
   }

   public static void setServerSuggestedPanelTeams(String[] panelTeams) {
      for (int i = 0; i < 4; i++) {
         if (panelTeams != null && i < panelTeams.length) {
            serverSuggestedPanelTeams[i] = normalizeTeam(panelTeams[i]);
         } else {
            serverSuggestedPanelTeams[i] = "__all__";
         }
      }
   }

   public static int getEffectiveColumns(boolean inGame) {
      if(inGame&&cn.bfops.killicon.client.gui.tabs.ScoreboardTab.isCareerScope())return 2;
      if (!inGame) {
         return 1;
      } else if (isServerForcingSoloMode()) {
         return 1;
      } else {
         ScoreboardLoadoutConfigManager.DisplayMode mode = getDisplayMode();

         return switch (mode) {
            case SINGLE -> 1;
            case DOUBLE -> 2;
            case QUAD -> 4;
            case AUTO -> serverSuggestedColumns;
         };
      }
   }

   public static String getEffectivePanelTeamBinding(int panelIndex, boolean inGame) {
      if(inGame&&cn.bfops.killicon.client.gui.tabs.ScoreboardTab.isCareerScope())return panelIndex==0?"在线玩家":panelIndex==1?"离线玩家":"";
      if (panelIndex < 0 || panelIndex >= 4) {
         return "__all__";
      } else if (!inGame) {
         return "__all__";
      } else if (isServerForcingSoloMode()) {
         return panelIndex == 0 ? "__all__" : "";
      } else {
         ScoreboardLoadoutConfigManager.DisplayMode mode = getDisplayMode();
         return mode == ScoreboardLoadoutConfigManager.DisplayMode.AUTO
            ? normalizeTeam(serverSuggestedPanelTeams[panelIndex])
            : getPanelTeamBinding(panelIndex);
      }
   }

   public static boolean isServerForcingSoloMode() {
      return serverSuggestedColumns == 1 && "__conquest_solo__".equals(normalizeTeam(serverSuggestedPanelTeams[0]));
   }

   public static void clearServerSuggestions() {
      serverSuggestedColumns = 1;

      for (int i = 0; i < 4; i++) {
         serverSuggestedPanelTeams[i] = "__all__";
      }
   }

   private static void ensureContextLoaded() {
      String contextId = resolveContextId();
      if (contextId != null) {
         if (!contextId.equals(loadedContextId)) {
            loadedContextId = contextId;
            loadCurrentContext();
            if (!isEditing) {
               tempMode = persistedMode;
               copyArray(persistedPanelTeams, tempPanelTeams);
            }
         }
      } else {
         loadedContextId = null;
         persistedMode = ScoreboardLoadoutConfigManager.DisplayMode.AUTO;

         for (int i = 0; i < 4; i++) {
            persistedPanelTeams[i] = "__all__";
         }

         if (!isEditing) {
            tempMode = ScoreboardLoadoutConfigManager.DisplayMode.AUTO;

            for (int i = 0; i < 4; i++) {
               tempPanelTeams[i] = "__all__";
            }
         }
      }
   }

   private static void loadCurrentContext() {
      persistedMode = ScoreboardLoadoutConfigManager.DisplayMode.AUTO;

      for (int i = 0; i < 4; i++) {
         persistedPanelTeams[i] = "__all__";
      }

      Path file = getContextFilePath();
      if (file != null && Files.exists(file)) {
         try {
            label88: {
               try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                  JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
                  if (json != null) {
                     if (json.has("display_mode")) {
                        try {
                           persistedMode = ScoreboardLoadoutConfigManager.DisplayMode.valueOf(json.get("display_mode").getAsString().toUpperCase(Locale.ROOT));
                        } catch (Exception var6) {
                           persistedMode = ScoreboardLoadoutConfigManager.DisplayMode.AUTO;
                        }
                     }

                     for (int i = 0; i < 4; i++) {
                        String key = "panel_team_" + (i + 1);
                        if (json.has(key)) {
                           persistedPanelTeams[i] = normalizeTeam(json.get(key).getAsString());
                        }
                     }
                     break label88;
                  }
               }

               return;
            }
         } catch (Exception var8) {
         }
      }
   }

   private static void saveCurrentContext() {
      Path file = getContextFilePath();
      if (file != null) {
         try {
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("display_mode", persistedMode.name().toLowerCase(Locale.ROOT));

            for (int i = 0; i < 4; i++) {
               json.addProperty("panel_team_" + (i + 1), normalizeTeam(persistedPanelTeams[i]));
            }

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
               GSON.toJson(json, writer);
            }
         } catch (Exception var7) {
         }
      }
   }

   private static Path getContextFilePath() {
      String contextId = resolveContextId();
      if (contextId == null) {
         return null;
      } else {
         Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
         return gameDir.resolve("data").resolve("battlefield_ops").resolve("scoreboard_layout").resolve(contextId + ".json");
      }
   }

   private static String resolveContextId() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.level != null && mc.player != null) {
         try {
            if (mc.getSingleplayerServer() != null) {
               Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
               String folder = root.getFileName() == null ? root.toString() : root.getFileName().toString();
               return "local_" + sanitize(folder);
            }
         } catch (Exception var4) {
         }

         try {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isEmpty()) {
               return "remote_" + sanitize(mc.getCurrentServer().ip);
            }
         } catch (Exception var3) {
         }

         return "local_unknown";
      } else {
         return null;
      }
   }

   private static String sanitize(String value) {
      return value != null && !value.isEmpty() ? value.replaceAll("[^a-zA-Z0-9._-]", "_") : "unknown";
   }

   private static void copyArray(String[] from, String[] to) {
      for (int i = 0; i < 4; i++) {
         to[i] = normalizeTeam(from[i]);
      }
   }

   private static String normalizeTeam(String team) {
      return team != null && !team.isEmpty() ? team : "__all__";
   }

   public static enum DisplayMode {
      AUTO,
      SINGLE,
      DOUBLE,
      QUAD;
   }
}
