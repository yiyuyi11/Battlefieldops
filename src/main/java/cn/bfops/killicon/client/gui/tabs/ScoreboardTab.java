package cn.bfops.killicon.client.gui.tabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.fml.ModList;
import cn.bfops.killicon.client.cache.LocalLeaderboardCache;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ScoreboardLoadoutConfigManager;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.elements.entries.FixedChoiceConfigEntry;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.network.NetworkHandler;
import cn.bfops.killicon.network.packet.ScoreboardRequestPacket;
import cn.bfops.killicon.network.packet.ScoreboardSyncPacket;

public class ScoreboardTab extends ConfigTabContent {
   /**
    * Drops the in-memory roster when the room ends, after writing what it held to this client's own
    * record of the server.
    *
    * <p>The write is the point. This used to just clear the list, which is why the leaderboard had
    * nothing to show once a round finished and had to refuse to open — the numbers existed a tick
    * earlier and were thrown away. What is flushed here is the real roster the server last published,
    * so reopening the tab later shows those numbers, labelled as a record rather than as live.
    */
   public static void clearRoomData() {
      flushToDisk();
      leaderboardData.clear();
      serverTotalCount = 0;
      lastKnownGlobalScore = null;
      source = cn.bfops.LeaderboardScope.Source.NONE;
      fetchedAt = 0L;
   }

   /**
    * Writes the rows currently held to {@code data/battlefield_ops/leaderboard_cache/<server>.json}.
    *
    * <p>Only rows the server actually published are written: this is a record of what a room reported,
    * and inventing entries (or re-writing stale ones as if fresh) would make the cached view a
    * plausible-looking fiction.
    */
   /**
    * Records a room's final roster, so the leaderboard has something true to show afterwards.
    *
    * <p>Separate from {@link #clearRoomData()} because the two happen at different moments and on
    * different triggers: this is called while the leaving state is still current, {@code clearRoomData}
    * after the render buffers are dropped. Writing here also covers the case the disk flush could not —
    * a player who never opened the tab during the round.
    */
   public static void archive(List<ScoreboardSyncPacket.Entry> rows) {
      if (rows == null || rows.isEmpty()) return;
      flush(rows);
   }

   /** Writes rows to disk and adopts them as the current table. */
   private static void flush(List<ScoreboardSyncPacket.Entry> rows) {
      try {
         LocalLeaderboardCache.mergeAndSave(new ArrayList<>(rows));
      } catch (RuntimeException | LinkageError error) {
         cn.bfops.BattlefieldOps.LOG.warn("Cannot write the local leaderboard cache", error);
      }
      if (source != cn.bfops.LeaderboardScope.Source.LIVE_ROOM) {
         leaderboardData = new ArrayList<>(rows);
         serverTotalCount = leaderboardData.size();
         source = cn.bfops.LeaderboardScope.Source.LOCAL_CACHE;
         fetchedAt = System.currentTimeMillis();
         lastRefreshTime = fetchedAt;
      }
   }

   private static void flushToDisk() {
      if (source != cn.bfops.LeaderboardScope.Source.LIVE_ROOM || leaderboardData.isEmpty()) return;
      flush(new ArrayList<>(leaderboardData));
   }
   private GDRowRenderer headerRenderer;
   private final List<GDRowRenderer> rowRenderers = new ArrayList<>();
   private ItemStack headerIcon = Items.GOLDEN_CARROT.getDefaultInstance();
   private final List<GDRowRenderer> panelHeaderRenderers = new ArrayList<>();
   private GDButton refreshButton;
   private GDButton columnModeButton;
   private GDButton toggleOfflineButton;
   private static boolean hideOffline = false;
   private static List<ScoreboardSyncPacket.Entry> leaderboardData = new ArrayList<>();
   /** Y of the provenance badge, just under the tab strip (which ends at y=30). */
   private static final int BADGE_TOP = 33;
   /** Badge height, including its accent bar. Tall enough to carry a 12px rank glyph beside the text. */
   private static final int BADGE_HEIGHT = 14;
   /**
    * Where {@link #leaderboardData} came from, so the tab can be honest about it.
    *
    * <p>Outside a live room the rows are this client's own record of the server, frozen when the last
    * room it saw closed. Drawing them without a label would claim current standings that nobody is
    * maintaining, which is the one thing this data must never do.
    */
   private static cn.bfops.LeaderboardScope.Source source = cn.bfops.LeaderboardScope.Source.NONE;
   /** Wall-clock time the shown roster was last published by the server, 0 when never. */
   private static long fetchedAt = 0L;
   private static boolean cumulative;
   public static boolean isCareerScope(){return cumulative;}
   public static void careerScope(boolean value){if(cumulative!=value){leaderboardData=new ArrayList<>();serverTotalCount=0;}cumulative=value;}
   private static long lastRefreshTime = 0L;
   private static int serverTotalCount = 0;
   private static int lastPacketOffset = -1;
   private static long lastPacketRequestId = -1L;
   private static Integer lastKnownGlobalScore = null;
   private static final int PAGE_SIZE = 20;
   private static final String GLOBAL_SCORE_OBJECTIVE = "battlefield_ops.score";
   private static long requestIdSeed = 0L;
   private boolean pageRequestInFlight = false;
   private int pendingPageOffset = -1;
   private boolean refreshChecking = false;
   private long refreshCheckStartAt = 0L;
   private long refreshRequestId = -1L;
   private boolean refreshReplyReceived = false;
   private long refreshStatusUntil = 0L;
   private boolean refreshStatusLocked = false;
   private final ScoreboardTab.SortType[] panelSortType = new ScoreboardTab.SortType[]{
      ScoreboardTab.SortType.SCORE, ScoreboardTab.SortType.SCORE, ScoreboardTab.SortType.SCORE, ScoreboardTab.SortType.SCORE
   };
   private final boolean[] panelSortAscending = new boolean[]{false, false, false, false};
   private final double[] panelScrollY = new double[]{0.0, 0.0, 0.0, 0.0};
   private final double[] panelTargetScrollY = new double[]{0.0, 0.0, 0.0, 0.0};
   private final int[] panelX1 = new int[]{0, 0, 0, 0};
   private final int[] panelY1 = new int[]{0, 0, 0, 0};
   private final int[] panelX2 = new int[]{0, 0, 0, 0};
   private final int[] panelY2 = new int[]{0, 0, 0, 0};
   private final int[] panelContentHeight = new int[]{0, 0, 0, 0};
   private final int[] panelViewHeight = new int[]{0, 0, 0, 0};
   private int draggingPanelIndex = -1;
   private boolean isDraggingArea3 = false;
   private double lastMouseY = 0.0;
   private long lastFrameTime = 0L;
   private int area2X1;
   private int area2Y1;
   private int area2X2;
   private int area2Y2;
   private int area3X1;
   private int area3Y1;
   private int area3X2;
   private int area3Y2;
   // The out-of-game "please open this in-game" hint renderer used to live here. Both it and the string
   // it drew are gone: that line was the refusal this release removes.
   private final List<GDRowRenderer> area3Renderers = new ArrayList<>();
   private final List<GDRowRenderer> weaponDetailRenderers = new ArrayList<>();
   private final List<GDRowRenderer> mobDetailRenderers = new ArrayList<>();
   private final List<GDRowRenderer> playerDetailRenderers = new ArrayList<>();
   private final List<GDRowRenderer> nemesisDetailRenderers = new ArrayList<>();
   private boolean isWeaponExpanded = false;
   private boolean isMobExpanded = false;
   private boolean isPlayerExpanded = false;
   private boolean isNemesisExpanded = false;
   private double scrollY3 = 0.0;
   private double targetScrollY3 = 0.0;

   public ScoreboardTab(Minecraft minecraft) {
      super(minecraft, "battlefield_ops.client.gui.config.tab.scoreboard");
   }

   public static void updateData(
      List<ScoreboardSyncPacket.Entry> entries, int offset, int totalCount, long requestId, int serverLayoutColumns, String[] serverPanelTeams
   ) {
      ScoreboardLoadoutConfigManager.clearServerSuggestions();
      if (offset <= 0) {
         leaderboardData = new ArrayList<>(entries);
      } else {
         Map<UUID, Integer> indexByUuid = new HashMap<>();

         for (int i = 0; i < leaderboardData.size(); i++) {
            indexByUuid.put(leaderboardData.get(i).uuid, i);
         }

         for (ScoreboardSyncPacket.Entry entry : entries) {
            Integer index = indexByUuid.get(entry.uuid);
            if (index != null) {
               leaderboardData.set(index, entry);
            } else {
               leaderboardData.add(entry);
            }
         }
      }

      serverTotalCount = Math.max(totalCount, leaderboardData.size());
      // A reply is the server's own roster, which is the strongest claim any row on this screen can
      // have. Whether it is non-empty is a separate question: an empty reply outside a room is
      // legitimate and must not downgrade a cached table to "live, zero players".
      if (!entries.isEmpty()) {
         source = cn.bfops.LeaderboardScope.Source.LIVE_ROOM;
         fetchedAt = System.currentTimeMillis();
      } else {
         refreshSource();
      }
      ScoreboardLoadoutConfigManager.setServerSuggestedColumns(serverLayoutColumns);
      ScoreboardLoadoutConfigManager.setServerSuggestedPanelTeams(serverPanelTeams);
      lastPacketOffset = offset;
      lastPacketRequestId = requestId;
      lastRefreshTime = System.currentTimeMillis();
   }

   private void handleHeaderClick(int panelIndex, ScoreboardTab.SortType type, int button) {
      if (panelIndex >= 0 && panelIndex < 4) {
         this.panelSortType[panelIndex] = type;
         this.panelSortAscending[panelIndex] = button != 0;
      }
   }

   @Override
   public void onTabOpen() {
      this.targetScrollY = 0.0;
      this.scrollY = 0.0;

      for (int i = 0; i < 4; i++) {
         this.panelSortType[i] = ScoreboardTab.SortType.SCORE;
         this.panelSortAscending[i] = false;
         this.panelScrollY[i] = 0.0;
         this.panelTargetScrollY[i] = 0.0;
         this.panelContentHeight[i] = 0;
         this.panelViewHeight[i] = 0;
      }

      this.draggingPanelIndex = -1;
      if (ClientConfigManager.shouldShowScoreboardIntro()) {
         ClientConfigManager.markScoreboardIntroShown();
         this.promptDialog.show(I18n.get("battlefield_ops.client.gui.prompt.scoreboard_intro", new Object[0]), PromptDialog.PromptType.INFO, null);
      }

      this.refreshButton = null;
      this.columnModeButton = null;
      this.toggleOfflineButton = null;
      this.pageRequestInFlight = false;
      this.pendingPageOffset = -1;
      this.refreshChecking = false;
      this.refreshStatusLocked = false;
      ScoreboardLoadoutConfigManager.clearServerSuggestions();
      // Two sources, in order of trust. The room this client is in is authoritative; this client's own
      // record of the server is the fallback that makes the tab useful after a round ends. Before this,
      // the local list was hardcoded empty with the comment "Parent mod exposes only the current room",
      // which was true and also the reason the page had nothing to draw outside a match.
      refreshSource();
      String serverKey = LocalLeaderboardCache.resolveServerKey();
      int liveRows = source == cn.bfops.LeaderboardScope.Source.LIVE_ROOM ? leaderboardData.size() : 0;
      List<ScoreboardSyncPacket.Entry> recorded = List.of();
      if (liveRows == 0) {
         try {
            recorded = LocalLeaderboardCache.load(serverKey);
         } catch (RuntimeException | LinkageError error) {
            cn.bfops.BattlefieldOps.LOG.warn("Cannot read the local leaderboard cache", error);
         }
      }
      source = cn.bfops.LeaderboardScope.select(liveRows, recorded == null ? 0 : recorded.size());
      if (source == cn.bfops.LeaderboardScope.Source.LOCAL_CACHE) {
         leaderboardData = new ArrayList<>(recorded);
         serverTotalCount = leaderboardData.size();
         if (lastRefreshTime <= 0L) lastRefreshTime = System.currentTimeMillis();
      } else if (source == cn.bfops.LeaderboardScope.Source.NONE) {
         leaderboardData = new ArrayList<>();
         serverTotalCount = 0;
      }

      if(cumulative){leaderboardData=new ArrayList<>();serverTotalCount=0;source=cn.bfops.LeaderboardScope.Source.NONE;}
      this.requestPage(0, nextRequestId());
   }

   /**
    * Recomputes {@link #source} from the live snapshot, without touching the rows.
    *
    * <p>Called on open and again after every page reply, so a room that starts publishing standings
    * while the tab is already open promotes the view from "record" to "live" instead of keeping a
    * stale badge over fresh numbers.
    */
   private static void refreshSource() {
      if(cumulative)return;
      if (cn.bfops.NativeKillIcons.liveRosterSize() > 0) {
         if (source != cn.bfops.LeaderboardScope.Source.LIVE_ROOM) {
            fetchedAt = System.currentTimeMillis();
         }
         source = cn.bfops.LeaderboardScope.Source.LIVE_ROOM;
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.choiceListDialog.isVisible()) {
         return this.choiceListDialog.mouseClicked(mouseX, mouseY, button);
      } else {
         for (GDRowRenderer panelHeaderRenderer : this.panelHeaderRenderers) {
            if (panelHeaderRenderer != null && panelHeaderRenderer.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }
         }

         if (this.refreshButton != null && this.refreshButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else if (this.columnModeButton != null && this.columnModeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else if (this.toggleOfflineButton != null && this.toggleOfflineButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else {
            if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
               double adjustedMouseY = mouseY + this.scrollY3;
               int currentY = this.area3Y1;
               int rowHeight = 17;

               for (int i = 0; i < 10; i++) {
                  if (mouseX >= (double)this.area3X1
                     && mouseX <= (double)this.area3X2
                     && adjustedMouseY >= (double)currentY
                     && adjustedMouseY <= (double)(currentY + rowHeight)) {
                     GDRowRenderer renderer = this.area3Renderers.get(i);
                     if (renderer.mouseClicked(mouseX, adjustedMouseY, button)) {
                        return true;
                     }
                  }

                  currentY += rowHeight + 1;
                  if (i == 6 && this.isNemesisExpanded) {
                     List<ClientStatsManager.PlayerStat> topNemesis = ClientStatsManager.getTopNemesisPlayers(3);

                     for (int j = 0; j < topNemesis.size(); j++) {
                        if (mouseX >= (double)(this.area3X1 + rowHeight)
                           && mouseX <= (double)this.area3X2
                           && adjustedMouseY >= (double)currentY
                           && adjustedMouseY <= (double)(currentY + rowHeight)
                           && j < this.nemesisDetailRenderers.size()
                           && this.nemesisDetailRenderers.get(j).mouseClicked(mouseX, adjustedMouseY, button)) {
                           return true;
                        }

                        currentY += rowHeight + 1;
                     }
                  }

                  if (i == 7 && this.isMobExpanded) {
                     List<ClientStatsManager.MobStat> topMobs = ClientStatsManager.getTopKilledMobs(3);

                     for (int j = 0; j < topMobs.size(); j++) {
                        if (mouseX >= (double)(this.area3X1 + rowHeight)
                           && mouseX <= (double)this.area3X2
                           && adjustedMouseY >= (double)currentY
                           && adjustedMouseY <= (double)(currentY + rowHeight)
                           && j < this.mobDetailRenderers.size()
                           && this.mobDetailRenderers.get(j).mouseClicked(mouseX, adjustedMouseY, button)) {
                           return true;
                        }

                        currentY += rowHeight + 1;
                     }
                  }

                  if (i == 8 && this.isPlayerExpanded) {
                     List<ClientStatsManager.PlayerStat> topPlayers = ClientStatsManager.getTopKilledPlayers(3);

                     for (int j = 0; j < topPlayers.size(); j++) {
                        if (mouseX >= (double)(this.area3X1 + rowHeight)
                           && mouseX <= (double)this.area3X2
                           && adjustedMouseY >= (double)currentY
                           && adjustedMouseY <= (double)(currentY + rowHeight)
                           && j < this.playerDetailRenderers.size()
                           && this.playerDetailRenderers.get(j).mouseClicked(mouseX, adjustedMouseY, button)) {
                           return true;
                        }

                        currentY += rowHeight + 1;
                     }
                  }

                  if (i == 9 && this.isWeaponExpanded) {
                     List<ClientStatsManager.WeaponStat> topWeapons = ClientStatsManager.getTopUsedWeapons(3);

                     for (int j = 0; j < topWeapons.size(); j++) {
                        if (mouseX >= (double)(this.area3X1 + rowHeight)
                           && mouseX <= (double)this.area3X2
                           && adjustedMouseY >= (double)currentY
                           && adjustedMouseY <= (double)(currentY + rowHeight)
                           && j < this.weaponDetailRenderers.size()
                           && this.weaponDetailRenderers.get(j).mouseClicked(mouseX, adjustedMouseY, button)) {
                           return true;
                        }

                        currentY += rowHeight + 1;
                     }
                  }
               }
            }

            int panelIndex = this.findPanelAt(mouseX, mouseY);
            if (panelIndex >= 0) {
               this.draggingPanelIndex = panelIndex;
               this.lastMouseY = mouseY;
               return true;
            } else if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
               this.isDraggingArea3 = true;
               this.lastMouseY = mouseY;
               return true;
            } else {
               return super.mouseClicked(mouseX, mouseY, button);
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.draggingPanelIndex = -1;
      this.isDraggingArea3 = false;
      if (this.promptDialog.isVisible()) {
         return true;
      } else {
         return this.choiceListDialog.isVisible() ? this.choiceListDialog.mouseReleased(mouseX, mouseY, button) : super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.choiceListDialog.isVisible()) {
         return this.choiceListDialog.mouseScrolled(mouseX, mouseY, delta);
      } else {
         int panelIndex = this.findPanelAt(mouseX, mouseY);
         if (panelIndex >= 0) {
            this.panelTargetScrollY[panelIndex] = this.panelTargetScrollY[panelIndex] - delta * 40.0;
            return true;
         } else if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
            this.targetScrollY3 -= delta * 40.0;
            return true;
         } else {
            return false;
         }
      }
   }

   @Override
   protected void updateScroll(float dt, int screenHeight) {
      for (int i = 0; i < 4; i++) {
         double maxScroll = (double)Math.max(0, this.panelContentHeight[i] - this.panelViewHeight[i]);
         this.panelTargetScrollY[i] = Math.max(0.0, Math.min(maxScroll, this.panelTargetScrollY[i]));
         double diff = this.panelTargetScrollY[i] - this.panelScrollY[i];
         if (Math.abs(diff) < 0.01) {
            this.panelScrollY[i] = this.panelTargetScrollY[i];
         } else {
            this.panelScrollY[i] = this.panelScrollY[i] + diff * 15.0 * (double)dt;
         }
      }

      int area3ContentHeight = 180;
      if (this.isNemesisExpanded) {
         List<ClientStatsManager.PlayerStat> topNemesis = ClientStatsManager.getTopNemesisPlayers(3);
         area3ContentHeight += topNemesis.size() * 18;
      }

      if (this.isMobExpanded) {
         List<ClientStatsManager.MobStat> topMobs = ClientStatsManager.getTopKilledMobs(3);
         area3ContentHeight += topMobs.size() * 18;
      }

      if (this.isPlayerExpanded) {
         List<ClientStatsManager.PlayerStat> topPlayers = ClientStatsManager.getTopKilledPlayers(3);
         area3ContentHeight += topPlayers.size() * 18;
      }

      if (this.isWeaponExpanded) {
         List<ClientStatsManager.WeaponStat> topWeapons = ClientStatsManager.getTopUsedWeapons(3);
         area3ContentHeight += topWeapons.size() * 18;
      }

      int area3ViewHeight = this.area3Y2 - this.area3Y1;
      double maxScroll3 = (double)Math.max(0, area3ContentHeight - area3ViewHeight);
      this.targetScrollY3 = Math.max(0.0, Math.min(maxScroll3, this.targetScrollY3));
      double diff3 = this.targetScrollY3 - this.scrollY3;
      if (Math.abs(diff3) < 0.01) {
         this.scrollY3 = this.targetScrollY3;
      } else {
         this.scrollY3 += diff3 * 15.0 * (double)dt;
      }
   }

   /**
    * Draws the leaderboard.
    *
    * <p>No match-state guard. The first statement used to be
    * {@code if (!inMatch()) { clearRoomData(); draw "只有在当前对局内才可以查看计分板"; return; }} — which refused to
    * open the page outside a round <em>and</em> destroyed the roster on the way out, so the numbers from
    * the round that had just finished were unrecoverable. Both halves are gone: the boards draw from
    * whatever {@link LeaderboardScope} could establish, and the provenance of those rows is stated in
    * the header rather than being hidden behind a refusal.
    */
   @Override
   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      this.updateAreaCoordinates(screenWidth, screenHeight);
      long currentTime = System.nanoTime();
      if (this.lastFrameTime == 0L) {
         this.lastFrameTime = currentTime;
      }

      float dt = (float)(currentTime - this.lastFrameTime) / 1.0E9F;
      this.lastFrameTime = currentTime;
      if (dt > 0.1F) {
         dt = 0.1F;
      }

      if (this.draggingPanelIndex >= 0) {
         double diff = (double)mouseY - this.lastMouseY;
         this.panelTargetScrollY[this.draggingPanelIndex] = this.panelTargetScrollY[this.draggingPanelIndex] - diff;
         this.lastMouseY = (double)mouseY;
      } else if (this.isDraggingArea3) {
         double diff = (double)mouseY - this.lastMouseY;
         this.targetScrollY3 -= diff;
         this.lastMouseY = (double)mouseY;
      }

      this.updateScroll(dt, screenHeight);
      this.updateRefreshState();
      this.tryLoadNextPage();
      // The boards are always drawn, with or without a world. This branch used to be an either/or on
      // `minecraft.level != null`, so out of a world the right-hand pane showed a bare hint line and a
      // single empty panel header — a screen with nothing on it, which is why the page read as
      // unavailable rather than as empty.
      this.renderSourceBadge(guiGraphics, this.area2X1, BADGE_TOP, this.area2X2);
      this.renderBoards(guiGraphics, mouseX, mouseY, partialTick, this.area2X1, this.area2Y1, this.area2X2, screenHeight - 8);

      this.renderArea3Stats(guiGraphics, mouseX, mouseY, partialTick, screenWidth, screenHeight);
      int area1Right = (screenWidth - 16) / 3 + 8;
      int buttonY = screenHeight - 8 - 17 - 1 - 17;
      int buttonWidth = (area1Right - 8 - 2) / 3;
      if (this.refreshButton == null) {
         this.refreshButton = new GDButton(
            this.area3X1, buttonY, buttonWidth, 17, Component.translatable("battlefield_ops.client.gui.button.refresh"), btn -> this.startRefreshCheck()
         );
      }

      this.refreshButton.setX(this.area3X1);
      this.refreshButton.setY(buttonY);
      this.refreshButton.setWidth(buttonWidth);
      this.refreshButton.setHeight(17);
      this.refreshButton.active = !this.refreshChecking && !this.refreshStatusLocked;
      this.refreshButton.render(guiGraphics, mouseX, mouseY, partialTick);
      if (this.columnModeButton == null) {
         this.columnModeButton = new GDButton(
            this.area3X1 + buttonWidth + 1,
            buttonY,
            buttonWidth,
            17,
            Component.translatable("battlefield_ops.client.gui.button.scoreboard_column_mode"),
            btn -> this.openColumnModeSelector()
         );
      }

      this.columnModeButton.setX(this.area3X1 + buttonWidth + 1);
      this.columnModeButton.setY(buttonY);
      this.columnModeButton.setWidth(buttonWidth);
      this.columnModeButton.setHeight(17);
      boolean canSelectColumnMode = this.minecraft.player != null
         && this.minecraft.level != null
         && !this.refreshChecking
         && !this.refreshStatusLocked
         && !ScoreboardLoadoutConfigManager.isDisplayModeLockedToAuto();
      this.columnModeButton.active = canSelectColumnMode;
      this.columnModeButton.setTextColor(canSelectColumnMode ? -1 : -5592406);
      this.columnModeButton.render(guiGraphics, mouseX, mouseY, partialTick);
      if (this.toggleOfflineButton == null) {
         this.toggleOfflineButton = new GDButton(
            this.area3X1 + 2 * (buttonWidth + 1), buttonY, buttonWidth, 17, Component.translatable("battlefield_ops.client.gui.button.hide_offline"), btn -> {
               hideOffline = !hideOffline;
               btn.setMessage(
                  Component.translatable(hideOffline ? "battlefield_ops.client.gui.button.show_offline" : "battlefield_ops.client.gui.button.hide_offline")
               );

               for (int i = 0; i < 4; i++) {
                  this.panelTargetScrollY[i] = 0.0;
               }
            }
         );
      }

      this.toggleOfflineButton.setX(this.area3X1 + 2 * (buttonWidth + 1));
      this.toggleOfflineButton.setY(buttonY);
      this.toggleOfflineButton.setWidth(buttonWidth);
      this.toggleOfflineButton.setHeight(17);
      this.toggleOfflineButton.active = !this.refreshChecking && !this.refreshStatusLocked;
      this.toggleOfflineButton
         .setMessage(Component.translatable(hideOffline ? "battlefield_ops.client.gui.button.show_offline" : "battlefield_ops.client.gui.button.hide_offline"));
      this.toggleOfflineButton.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void startRefreshCheck() {
      this.refreshChecking = true;
      this.refreshReplyReceived = false;
      this.refreshCheckStartAt = System.currentTimeMillis();
      this.refreshRequestId = nextRequestId();
      this.refreshStatusLocked = false;
      this.setRefreshButtonStatus(Component.translatable("battlefield_ops.client.gui.scoreboard.refresh.checking"), -5592406);
      this.requestPage(0, this.refreshRequestId);
   }

   private void updateRefreshState() {
      if (this.refreshChecking && lastPacketRequestId == this.refreshRequestId && lastPacketOffset == 0) {
         this.refreshReplyReceived = true;
      }

      long now = System.currentTimeMillis();
      if (this.refreshChecking && this.refreshReplyReceived) {
         this.refreshChecking = false;
         int selfPing = this.getSelfPing();
         this.showRefreshResult(Component.translatable("battlefield_ops.client.gui.scoreboard.refresh.success", new Object[]{selfPing}), -5592406);
      }

      if (this.refreshChecking && now - this.refreshCheckStartAt >= 2000L) {
         this.refreshChecking = false;
         if (!this.isConnectedToVanillaServer()) {
            this.showRefreshResult(Component.translatable("battlefield_ops.client.gui.scoreboard.refresh.disconnected"), -65536);
         } else if (!this.refreshReplyReceived) {
            this.showRefreshResult(Component.translatable("battlefield_ops.client.gui.scoreboard.refresh.handshake_failed"), -65536);
         }
      }

      if (this.refreshStatusLocked && now >= this.refreshStatusUntil) {
         this.refreshStatusLocked = false;
         this.refreshButton.active = true;
         this.refreshButton.setMessage(Component.translatable("battlefield_ops.client.gui.button.refresh"));
         this.refreshButton.setTextColor(-1);
      }
   }

   private void showRefreshResult(Component message, int color) {
      this.refreshStatusLocked = true;
      this.refreshStatusUntil = System.currentTimeMillis() + 3000L;
      this.setRefreshButtonStatus(message, color);
   }

   private void setRefreshButtonStatus(Component text, int color) {
      if (this.refreshButton != null) {
         this.refreshButton.active = false;
         this.refreshButton.setMessage(text);
         this.refreshButton.setTextColor(color);
      }
   }

   private boolean isConnectedToVanillaServer() {
      return this.minecraft.getConnection() != null && this.minecraft.getConnection().getConnection() != null && this.minecraft.getConnection().getConnection().isConnected();
   }

   private int getSelfPing() {
      if (this.minecraft.player != null && this.minecraft.getConnection() != null) {
         PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(this.minecraft.player.getUUID());
         return info != null ? info.getLatency() : -1;
      } else {
         return -1;
      }
   }

   private static long nextRequestId() {
      requestIdSeed++;
      return requestIdSeed;
   }

   private void requestPage(int offset, long requestId) {
      if (this.minecraft.player != null && this.minecraft.getConnection() != null && this.isConnectedToVanillaServer()) {
         int safeOffset = Math.max(0, offset);
         int limit = this.resolvePreferredRequestLimit();
         this.pendingPageOffset = safeOffset;
         this.pageRequestInFlight = true;

         try {
            NetworkHandler.sendToServer(new ScoreboardRequestPacket(safeOffset, limit, requestId));
         } catch (Exception var7) {
            this.pageRequestInFlight = false;
            this.pendingPageOffset = -1;
         }
      }
   }

   private void tryLoadNextPage() {
      if (this.pageRequestInFlight && lastPacketOffset == this.pendingPageOffset) {
         this.pageRequestInFlight = false;
         this.pendingPageOffset = -1;
      }

      if (!this.refreshChecking && !this.refreshStatusLocked && !this.pageRequestInFlight) {
         if (leaderboardData.size() < serverTotalCount && serverTotalCount > 0) {
            boolean nearBottom = false;

            for (int i = 0; i < 4; i++) {
               double maxScroll = (double)Math.max(0, this.panelContentHeight[i] - this.panelViewHeight[i]);
               if (maxScroll > 0.0 && this.panelTargetScrollY[i] >= maxScroll - 1.0) {
                  nearBottom = true;
                  break;
               }
            }

            boolean hasVisibleGap = this.hasAnyPanelVisibleGap();
            if (nearBottom || hasVisibleGap) {
               this.requestPage(leaderboardData.size(), nextRequestId());
            }
         }
      }
   }

   private int resolvePreferredRequestLimit() {
      int visibleRows = 0;
      int rowHeight = 18;

      for (int i = 0; i < 4; i++) {
         int view = this.panelViewHeight[i];
         if (view > 0) {
            visibleRows += Math.max(1, (int)Math.ceil((double)view / (double)rowHeight));
         }
      }

      if (visibleRows <= 0) {
         return 20;
      } else {
         int desired = Math.max(20, visibleRows + 10);
         return Math.min(100, desired);
      }
   }

   private boolean hasAnyPanelVisibleGap() {
      int rowHeight = 18;

      for (int i = 0; i < 4; i++) {
         int view = this.panelViewHeight[i];
         if (view > 0) {
            int visibleRows = Math.max(1, (int)Math.ceil((double)view / (double)rowHeight));
            int loadedRows = this.panelContentHeight[i] <= 0 ? 0 : (int)Math.ceil((double)this.panelContentHeight[i] / (double)rowHeight);
            if (loadedRows < visibleRows) {
               return true;
            }
         }
      }

      return false;
   }

   private void updateAreaCoordinates(int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      this.area2X1 = area1Right + 8;
      // 45, not 34: the provenance badge sits above the boards and needs its own row. It used to be
      // drawn at area2Y1-12, which put it under the tab strip that ends at y=30 — the badge and the tabs
      // would have overlapped, and the text would have been unreadable against the tab bodies.
      this.area2Y1 = BADGE_TOP + BADGE_HEIGHT + 3;
      this.area2X2 = screenWidth - 8;
      this.area2Y2 = screenHeight - 8;
      this.area3X1 = 8;
      this.area3Y1 = this.area1Bottom + 8;
      this.area3X2 = area1Right;
      int area4Top = screenHeight - 8 - 35;
      this.area3Y2 = area4Top - 8;
   }

   private void renderArea3Stats(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
      int x1 = this.area3X1;
      int yStart = this.area3Y1;
      int x2 = this.area3X2;
      int rowHeight = 17;
      List<String[]> stats = new ArrayList<>();
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.total_kills", new Object[0]),
            String.valueOf(ClientStatsManager.getTotalKills())
         }
      );
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.total_deaths", new Object[0]),
            String.valueOf(ClientStatsManager.getTotalDeaths())
         }
      );
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.total_assists", new Object[0]),
            String.valueOf(ClientStatsManager.getTotalAssists())
         }
      );
      if (ModList.get().isLoaded("gd656conquest")) {
         stats.add(
            new String[]{
               " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.total_revives", new Object[0]),
               String.valueOf(ClientStatsManager.getTotalRevives())
            }
         );
      }

      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.max_streak", new Object[0]),
            String.valueOf(ClientStatsManager.getMaxKillStreak())
         }
      );
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.max_distance", new Object[0]),
            String.format("%.1fm", ClientStatsManager.getMaxKillDistance())
         }
      );
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.total_damage", new Object[0]),
            String.format("%.0f", ClientStatsManager.getTotalDamageDealt())
         }
      );
      stats.add(new String[]{" " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.nemesis", new Object[0]), ClientStatsManager.getNemesis()});
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.most_killed_mob", new Object[0]), ClientStatsManager.getMostKilledMob()
         }
      );
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.most_killed_player", new Object[0]), ClientStatsManager.getMostKilledPlayer()
         }
      );
      stats.add(
         new String[]{
            " " + I18n.get("battlefield_ops.client.gui.config.scoreboard.stat.most_used_weapon", new Object[0]), ClientStatsManager.getMostUsedWeapon()
         }
      );
      guiGraphics.enableScissor(this.area3X1, this.area3Y1, this.area3X2, this.area3Y2);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0, -this.scrollY3, 0.0);
      int currentY = yStart;
      int visualRowIndex = 0;

      for (int i = 0; i < stats.size(); i++) {
         while (this.area3Renderers.size() <= i) {
            this.area3Renderers.add(new GDRowRenderer(x1, currentY, x2, currentY + rowHeight, GuiConstants.COLOR_BLACK, 0.3F, false));
         }

         GDRowRenderer renderer = this.area3Renderers.get(i);
         renderer.setBounds(x1, currentY, x2, currentY + rowHeight);
         float alpha = visualRowIndex % 2 == 1 ? 0.15F : 0.3F;
         renderer.setBackgroundAlpha(alpha);
         renderer.resetColumnConfig();
         Consumer<Integer> callback = null;
         int nemesisIndex = ModList.get().isLoaded("gd656conquest") ? 7 : 6;
         int mobIndex = ModList.get().isLoaded("gd656conquest") ? 8 : 7;
         int playerIndex = ModList.get().isLoaded("gd656conquest") ? 9 : 8;
         int weaponIndex = ModList.get().isLoaded("gd656conquest") ? 10 : 9;
         if (i == nemesisIndex) {
            callback = btn -> this.isNemesisExpanded = !this.isNemesisExpanded;
         } else if (i == mobIndex) {
            callback = btn -> this.isMobExpanded = !this.isMobExpanded;
         } else if (i == playerIndex) {
            callback = btn -> this.isPlayerExpanded = !this.isPlayerExpanded;
         } else if (i == weaponIndex) {
            callback = btn -> this.isWeaponExpanded = !this.isWeaponExpanded;
         }

         String[] statRow = stats.get(i);
         if (callback != null) {
            renderer.addColumn(statRow[0], 70, GuiConstants.COLOR_GOLD, false, false, callback);
            renderer.addColumn(statRow[1], -1, -1, true, true, callback);
         } else {
            renderer.addColumn(statRow[0], 70, GuiConstants.COLOR_GOLD, false, false);
            renderer.addColumn(statRow[1], -1, -1, true, true);
         }

         renderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
         currentY += rowHeight + 1;
         visualRowIndex++;
         if (i == nemesisIndex && this.isNemesisExpanded) {
            List<ClientStatsManager.PlayerStat> topNemesis = ClientStatsManager.getTopNemesisPlayers(3);

            for (int j = 0; j < topNemesis.size(); j++) {
               ClientStatsManager.PlayerStat stat = topNemesis.get(j);

               while (this.nemesisDetailRenderers.size() <= j) {
                  this.nemesisDetailRenderers
                     .add(new GDRowRenderer(x1 + rowHeight, currentY, x2, currentY + rowHeight, GuiConstants.COLOR_BLACK, 0.15F, false));
               }

               GDRowRenderer detailRenderer = this.nemesisDetailRenderers.get(j);
               detailRenderer.setBounds(x1 + rowHeight, currentY, x2, currentY + rowHeight);
               float detailAlpha = visualRowIndex % 2 == 1 ? 0.15F : 0.3F;
               detailRenderer.setBackgroundAlpha(detailAlpha);
               detailRenderer.resetColumnConfig();
               detailRenderer.addColumn(" " + stat.name, -1, -1, false, false);
               detailRenderer.addColumn(String.valueOf(stat.count), 40, GuiConstants.COLOR_GOLD, true, true);
               detailRenderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
               currentY += rowHeight + 1;
               visualRowIndex++;
            }
         }

         if (i == mobIndex && this.isMobExpanded) {
            List<ClientStatsManager.MobStat> topMobs = ClientStatsManager.getTopKilledMobs(3);

            for (int j = 0; j < topMobs.size(); j++) {
               ClientStatsManager.MobStat stat = topMobs.get(j);

               while (this.mobDetailRenderers.size() <= j) {
                  this.mobDetailRenderers.add(new GDRowRenderer(x1 + rowHeight, currentY, x2, currentY + rowHeight, GuiConstants.COLOR_BLACK, 0.15F, false));
               }

               GDRowRenderer detailRenderer = this.mobDetailRenderers.get(j);
               detailRenderer.setBounds(x1 + rowHeight, currentY, x2, currentY + rowHeight);
               float detailAlpha = visualRowIndex % 2 == 1 ? 0.15F : 0.3F;
               detailRenderer.setBackgroundAlpha(detailAlpha);
               detailRenderer.resetColumnConfig();
               detailRenderer.addColumn(" " + stat.name, -1, -1, false, false);
               detailRenderer.addColumn(String.valueOf(stat.count), 40, GuiConstants.COLOR_GOLD, true, true);
               detailRenderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
               currentY += rowHeight + 1;
               visualRowIndex++;
            }
         }

         if (i == playerIndex && this.isPlayerExpanded) {
            List<ClientStatsManager.PlayerStat> topPlayers = ClientStatsManager.getTopKilledPlayers(3);

            for (int j = 0; j < topPlayers.size(); j++) {
               ClientStatsManager.PlayerStat stat = topPlayers.get(j);

               while (this.playerDetailRenderers.size() <= j) {
                  this.playerDetailRenderers.add(new GDRowRenderer(x1 + rowHeight, currentY, x2, currentY + rowHeight, GuiConstants.COLOR_BLACK, 0.15F, false));
               }

               GDRowRenderer detailRenderer = this.playerDetailRenderers.get(j);
               detailRenderer.setBounds(x1 + rowHeight, currentY, x2, currentY + rowHeight);
               float detailAlpha = visualRowIndex % 2 == 1 ? 0.15F : 0.3F;
               detailRenderer.setBackgroundAlpha(detailAlpha);
               detailRenderer.resetColumnConfig();
               detailRenderer.addColumn(" " + stat.name, -1, -1, false, false);
               detailRenderer.addColumn(String.valueOf(stat.count), 40, GuiConstants.COLOR_GOLD, true, true);
               detailRenderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
               currentY += rowHeight + 1;
               visualRowIndex++;
            }
         }

         if (i == weaponIndex && this.isWeaponExpanded) {
            List<ClientStatsManager.WeaponStat> topWeapons = ClientStatsManager.getTopUsedWeapons(3);

            for (int j = 0; j < topWeapons.size(); j++) {
               ClientStatsManager.WeaponStat stat = topWeapons.get(j);

               while (this.weaponDetailRenderers.size() <= j) {
                  this.weaponDetailRenderers.add(new GDRowRenderer(x1 + rowHeight, currentY, x2, currentY + rowHeight, GuiConstants.COLOR_BLACK, 0.15F, false));
               }

               GDRowRenderer detailRenderer = this.weaponDetailRenderers.get(j);
               detailRenderer.setBounds(x1 + rowHeight, currentY, x2, currentY + rowHeight);
               float detailAlpha = visualRowIndex % 2 == 1 ? 0.15F : 0.3F;
               detailRenderer.setBackgroundAlpha(detailAlpha);
               detailRenderer.resetColumnConfig();
               detailRenderer.addColumn(" " + stat.name, -1, -1, false, false);
               detailRenderer.addColumn(String.valueOf(stat.count), 40, GuiConstants.COLOR_GOLD, true, true);
               detailRenderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
               currentY += rowHeight + 1;
               visualRowIndex++;
            }
         }
      }

      guiGraphics.pose().popPose();
      guiGraphics.disableScissor();
   }

   /**
    * A one-line statement of where the rows on screen came from.
    *
    * <p>This is the honest half of "the leaderboard opens at any time". A live roster needs no
    * disclaimer and gets a short accent-tinted caption; the disk record gets an amber badge with the
    * time it was captured, because a player comparing themselves against last week's numbers without
    * being told is the failure mode that matters. An empty table says so plainly and names the two
    * things that would fill it.
    */
   private void renderSourceBadge(GuiGraphics guiGraphics, int x1, int y, int x2) {
      if (y < 0 || x2 <= x1) return;
      var font = this.minecraft.font;
      int color;
      String text;
      switch (source) {
         case LIVE_ROOM -> {
            color = -3377289;
            text = I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.source.live", new Object[0]);
         }
         case LOCAL_CACHE -> {
            color = -7667712;
            String when = fetchedAt <= 0L ? "" : new java.text.SimpleDateFormat("MM-dd HH:mm")
                    .format(new java.util.Date(fetchedAt));
            text = I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.source.cache",
                    new Object[]{serverTotalCount, when});
         }
         default -> {
            color = -5592406;
            text = I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.source.empty", new Object[0]);
         }
      }
      if(cumulative)text="服务器累计战绩 · 在线 / 离线玩家";
      String clipped = UiClip(font, text, x2 - x1 - 26);
      int plateY = Math.max(0, y);
      guiGraphics.fill(x1, plateY, x2, plateY + BADGE_HEIGHT, 0xC0101820);
      guiGraphics.fill(x1, plateY, x1 + 2, plateY + BADGE_HEIGHT, color);
      // The rank glyph identifies the strip at a glance even before the words are read. 12px is a crop
      // of the 16px cell, not a resample, so it stays sharp — the medal is designed with a one-pixel
      // margin precisely so the trimmed middle still reads.
      cn.bfops.UiIcons.marker(guiGraphics, cn.bfops.UiIcons.RANK, x1 + 11, plateY + BADGE_HEIGHT / 2, 12);
      guiGraphics.drawString(font, clipped, x1 + 20, plateY + 3, color, false);
      // A one-pixel rule under the badge separates the provenance strip from the boards, so a cached
      // table never looks like the top row of a live one.
      guiGraphics.fill(x1, plateY + BADGE_HEIGHT, x2, plateY + BADGE_HEIGHT + 1, 0xff243642);
   }

   private static String UiClip(net.minecraft.client.gui.Font font, String text, int width) {
      if (text == null) return "";
      if (width <= 0) return "";
      return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - 6)) + "…";
   }

   /**
    * Whether team binding and column mode may be resolved from the live world.
    *
    * <p>The tab used to pass a hardcoded {@code true} to
    * {@link ScoreboardLoadoutConfigManager#getEffectiveColumns(boolean)} and
    * {@code getEffectivePanelTeamBinding}, because it only ever ran inside a match. Now that it opens in
    * the lobby and after a round, that {@code true} would bind panels to a team name read out of a
    * world that is not there — and, worse, the "AUTO" column mode would ask a server that is not
    * publishing anything. The manager already knows how to degrade (it returns one column and an "all
    * players" binding when told it is out of game); it just has to be told the truth.
    */
   private boolean inGame() {
      return this.minecraft.player != null && this.minecraft.level != null;
   }

   /**
    * Draws the "no rows for this panel" state inside a board.
    *
    * <p>An empty framed box with a header and nothing under it reads as a broken screen. Saying which
    * of the two situations applies — nothing recorded on this server yet, or a record that does not
    * contain this teammate grouping — is the difference between "the mod is broken" and "this is
    * genuinely empty".
    */
   private void renderPanelEmpty(GuiGraphics guiGraphics, int panelIndex, int x1, int y1, int x2, int y2) {
      var font = this.minecraft.font;
      String text = I18n.get(cn.bfops.LeaderboardScope.emptyHintKey(source), new Object[0]);
      int room = Math.max(1, x2 - x1 - 12);
      var lines = font.split(Component.literal(text), room);
      int lineY = y1 + Math.max(2, (y2 - y1 - lines.size() * 10) / 2);
      for (var line : lines) {
         if (lineY + 9 > y2) break;
         guiGraphics.drawString(font, line, x1 + 6, lineY, -10066330, false);
         lineY += 10;
      }
   }

   private void renderBoards(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int y1, int x2, int y2) {
      int columns = ScoreboardLoadoutConfigManager.getEffectiveColumns(this.inGame());
      if (columns == 2) {
         int width = x2 - x1;
         int panelWidth = (width - 8) / 2;
         int p1x2 = x1 + panelWidth;
         int p2x1 = p1x2 + 8;
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 0, x1, y1, p1x2, y2);
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 1, p2x1, y1, x2, y2);
      } else if (columns == 4) {
         int width = x2 - x1;
         int height = y2 - y1;
         int panelWidth = (width - 8) / 2;
         int panelHeight = (height - 8) / 2;
         int p1x2 = x1 + panelWidth;
         int p2x1 = p1x2 + 8;
         int p1y2 = y1 + panelHeight;
         int p2y1 = p1y2 + 8;
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 0, x1, y1, p1x2, p1y2);
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 1, p2x1, y1, x2, p1y2);
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 2, x1, p2y1, p1x2, y2);
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 3, p2x1, p2y1, x2, y2);
      } else {
         this.renderSingleBoardPanel(guiGraphics, mouseX, mouseY, partialTick, 0, x1, y1, x2, y2);
      }
   }

   private void renderSingleBoardPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int panelIndex, int x1, int y1, int x2, int y2) {
      this.panelX1[panelIndex] = x1;
      this.panelY1[panelIndex] = y1;
      this.panelX2[panelIndex] = x2;
      this.panelY2[panelIndex] = y2;
      int headerY2 = y1 + 17;
      this.renderPanelHeader(guiGraphics, mouseX, mouseY, partialTick, panelIndex, x1, y1, x2, headerY2);
      int contentY1 = headerY2 + 1;
      int contentY2 = y2;
      if (y2 > contentY1) {
         List<ScoreboardSyncPacket.Entry> filtered = this.getPanelEntries(panelIndex);
         if (filtered.isEmpty()) this.renderPanelEmpty(guiGraphics, panelIndex, x1, contentY1, x2, y2);
         int contentHeight = filtered.size() * 18;
         int viewHeight = y2 - contentY1;
         this.panelContentHeight[panelIndex] = contentHeight;
         this.panelViewHeight[panelIndex] = viewHeight;
         ScoreboardTab.ColumnLayout layout = this.computeColumnLayout(x2 - x1, this.shouldShowDedicatedReviveColumn());
         guiGraphics.enableScissor(x1, contentY1, x2, y2);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0, -this.panelScrollY[panelIndex], 0.0);
         int visualIndex = 0;

         for (int i = 0; i < filtered.size(); i++) {
            ScoreboardSyncPacket.Entry entry = filtered.get(i);
            int rowTop = contentY1 + visualIndex * 18;
            int rowBottom = rowTop + 17;
            float actualScreenTop = (float)rowTop - (float)this.panelScrollY[panelIndex];
            float actualScreenBottom = (float)rowBottom - (float)this.panelScrollY[panelIndex];
            if (actualScreenBottom > (float)contentY1 && actualScreenTop < (float)contentY2) {
               this.renderRow(
                  guiGraphics,
                  mouseX,
                  (int)((double)mouseY + this.panelScrollY[panelIndex]),
                  partialTick,
                  panelIndex,
                  entry,
                  visualIndex,
                  x1,
                  rowTop,
                  x2,
                  rowBottom,
                  layout
               );
            }

            visualIndex++;
         }

         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
      }
   }

   private void renderPanelHeader(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int panelIndex, int x1, int y1, int x2, int y2) {
      while (this.panelHeaderRenderers.size() <= panelIndex) {
         this.panelHeaderRenderers.add(new GDRowRenderer(x1, y1, x2, y2, GuiConstants.COLOR_GOLD, 0.75F, true));
      }

      GDRowRenderer renderer = this.panelHeaderRenderers.get(panelIndex);
      renderer.setBounds(x1, y1, x2, y2);
      renderer.resetColumnConfig();
      String boundTeam = ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(panelIndex, this.inGame());
      int teamColor = this.getBoundTeamColor(boundTeam);
      renderer.setBackgroundColor(teamColor == GuiConstants.COLOR_GOLD ? GuiConstants.COLOR_GOLD : teamColor & 16777215);
      renderer.setBackgroundAlpha(0.75F);
      boolean useReviveColumn = this.shouldUseReviveColumn();
      boolean showDedicatedReviveColumn = this.shouldShowDedicatedReviveColumn();
      ScoreboardTab.ColumnLayout layout = this.computeColumnLayout(x2 - x1, showDedicatedReviveColumn);
      ItemStack icon = this.getPanelIconByTeam(boundTeam);
      renderer.addIconColumn(icon, layout.rankWidth, false, false, null);
      boolean allowTeamFilterClick = !ScoreboardLoadoutConfigManager.isDisplayModeLockedToAuto()
         && ScoreboardLoadoutConfigManager.getDisplayMode() != ScoreboardLoadoutConfigManager.DisplayMode.AUTO;
      renderer.addColoredColumn(
         this.getPanelTeamInfo(panelIndex), layout.nameWidth, false, false, allowTeamFilterClick ? btn -> this.openPanelTeamSelector(panelIndex) : null
      );
      renderer.addColumn(
         I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.header.score", new Object[0]),
         layout.scoreWidth,
         -1,
         true,
         true,
         btn -> this.handleHeaderClick(panelIndex, ScoreboardTab.SortType.SCORE, btn)
      );
      renderer.addColumn(
         I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.header.kill", new Object[0]),
         layout.killWidth,
         -1,
         false,
         true,
         btn -> this.handleHeaderClick(panelIndex, ScoreboardTab.SortType.KILL, btn)
      );
      renderer.addColumn(
         I18n.get(
            useReviveColumn ? "battlefield_ops.client.gui.config.tab.scoreboard.header.revive" : "battlefield_ops.client.gui.config.tab.scoreboard.header.death",
            new Object[0]
         ),
         layout.deathWidth,
         -1,
         false,
         true,
         btn -> this.handleHeaderClick(panelIndex, useReviveColumn ? ScoreboardTab.SortType.REVIVE : ScoreboardTab.SortType.DEATH, btn)
      );
      renderer.addColumn(
         I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.header.assist", new Object[0]),
         layout.assistWidth,
         -1,
         false,
         true,
         btn -> this.handleHeaderClick(panelIndex, ScoreboardTab.SortType.ASSIST, btn)
      );
      if (showDedicatedReviveColumn) {
         renderer.addColumn(
            I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.header.revive", new Object[0]),
            layout.reviveWidth,
            -1,
            false,
            true,
            btn -> this.handleHeaderClick(panelIndex, ScoreboardTab.SortType.REVIVE, btn)
         );
      }

      renderer.addColoredColumn(this.getPingInfo(), layout.pingWidth, true, true, btn -> this.handleHeaderClick(panelIndex, ScoreboardTab.SortType.PING, btn));
      renderer.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderRow(
      GuiGraphics guiGraphics,
      int mouseX,
      int mouseY,
      float partialTick,
      int panelIndex,
      ScoreboardSyncPacket.Entry entry,
      int visualIndex,
      int x1,
      int y1,
      int x2,
      int y2,
      ScoreboardTab.ColumnLayout layout
   ) {
      int ping = this.resolvePing(entry);
      boolean oddRow = visualIndex % 2 == 1;
      int rowBgColor = 0;
      float alpha = oddRow ? 0.12F : 0.22F;
      boolean isTeammate = false;
      boolean isSelf = this.minecraft.player != null && entry.uuid.equals(this.minecraft.player.getUUID());
      boolean isConquestMatchRowColor = this.isConquestMatchScoreboardJoined();
      boolean isSoloMatch = this.isConquestSoloMatchScoreboardJoined();
      String selfTeamName = this.getSelfTeamName();
      String selfSquadLabel = this.getSelfSquadLabel();
      boolean isSameTeamAsSelf = !selfTeamName.isBlank() && selfTeamName.equals(entry.teamName);
      boolean isSameSquadAsSelf = isSameTeamAsSelf && !selfSquadLabel.isBlank() && selfSquadLabel.equals(entry.squadLabel);
      int themeColor = this.getBoundTeamColor(ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(panelIndex, this.inGame()));
      if (this.minecraft.player != null && this.minecraft.level != null) {
         Team team = this.minecraft.player.getTeam();
         if (team != null && team.getName().equals(entry.teamName)) {
            isTeammate = true;
         } else if (isSameTeamAsSelf) {
            isTeammate = true;
         }

         if (isSoloMatch) {
            isTeammate = false;
            isSameSquadAsSelf = false;
         }

         if (isSelf) {
            if (isSoloMatch) {
               rowBgColor = 13395558;
               alpha = oddRow ? 0.22F : 0.28F;
            } else if (isConquestMatchRowColor) {
               rowBgColor = 10145074;
               alpha = oddRow ? 0.17F : 0.23F;
            } else {
               rowBgColor = this.tint(themeColor, 0.2F) & 16777215;
               alpha = 0.3F;
            }
         } else if (isConquestMatchRowColor) {
            if (isSameSquadAsSelf) {
               rowBgColor = 7048739;
               alpha = oddRow ? 0.17F : 0.23F;
            } else if (isTeammate) {
               rowBgColor = 1981066;
               alpha = oddRow ? 0.17F : 0.23F;
            } else {
               rowBgColor = 8330525;
               alpha = oddRow ? 0.17F : 0.23F;
            }
         } else if (isTeammate) {
            rowBgColor = this.tint(themeColor, -0.15F) & 16777215;
         }
      }

      if (!isConquestMatchRowColor && isTeammate) {
         alpha = oddRow ? 0.17F : 0.23F;
      }

      while (this.rowRenderers.size() <= visualIndex) {
         this.rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2, rowBgColor, alpha, false));
      }

      GDRowRenderer renderer = this.rowRenderers.get(visualIndex);
      renderer.setBounds(x1, y1, x2, y2);
      renderer.setBackgroundColor(rowBgColor);
      renderer.setBackgroundAlpha(alpha);
      renderer.resetColumnConfig();
      boolean isOffline = !entry.online;
      boolean isSpectator = entry.online && entry.spectator;
      boolean useReviveColumn = this.shouldUseReviveColumn();
      boolean showDedicatedReviveColumn = this.shouldShowDedicatedReviveColumn();
      int rowTextColor = -1;
      boolean italic = false;
      if (isSelf) {
         rowTextColor = GuiConstants.COLOR_GOLD;
         if (isSpectator) {
            italic = true;
         }
      } else if (isOffline) {
         rowTextColor = -10066330;
      } else if (isSpectator) {
         rowTextColor = -5592406;
         italic = true;
      }

      renderer.addColumn(this.toStyledText(String.valueOf(visualIndex + 1), italic), layout.rankWidth, rowTextColor, true, true);
      renderer.addColumn(
         this.toStyledText(entry.lastLoginName != null ? entry.lastLoginName : entry.name, italic), layout.nameWidth, rowTextColor, false, false
      );
      renderer.addColumn(this.toStyledText(String.valueOf(entry.score), italic), layout.scoreWidth, rowTextColor, true, true);
      renderer.addColumn(this.toStyledText(String.valueOf(entry.kill), italic), layout.killWidth, rowTextColor, false, true);
      renderer.addColumn(this.toStyledText(String.valueOf(useReviveColumn ? entry.revive : entry.death), italic), layout.deathWidth, rowTextColor, false, true);
      renderer.addColumn(this.toStyledText(String.valueOf(entry.assist), italic), layout.assistWidth, rowTextColor, false, true);
      if (showDedicatedReviveColumn) {
         renderer.addColumn(this.toStyledText(String.valueOf(entry.revive), italic), layout.reviveWidth, rowTextColor, false, true);
      }

      renderer.addColoredColumn(this.formatPing(ping), layout.pingWidth, true, true);
      renderer.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private boolean isConquestMatchScoreboardJoined() {
      if (this.isConquestSoloMatchScoreboardJoined()) {
         return true;
      } else if (this.minecraft.player != null && ScoreboardLoadoutConfigManager.getEffectiveColumns(this.inGame()) == 2) {
         String leftTeam = this.resolveBoundTeamName(ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(0, this.inGame()));
         String rightTeam = this.resolveBoundTeamName(ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(1, this.inGame()));
         if (this.isConquestRuntimeTeamName(leftTeam) && this.isConquestRuntimeTeamName(rightTeam)) {
            String selfTeamName = this.getSelfTeamName();
            if (selfTeamName.isBlank()) {
               Team selfTeam = this.minecraft.player.getTeam();
               selfTeamName = selfTeam != null && selfTeam.getName() != null ? selfTeam.getName() : "";
            }

            return selfTeamName.isBlank() ? false : selfTeamName.equals(leftTeam) || selfTeamName.equals(rightTeam);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isTeamAll(String bind) {
      return "__all__".equals(bind) || "__conquest_solo__".equals(bind);
   }

   private boolean isConquestSoloMatchScoreboardJoined() {
      if (ScoreboardLoadoutConfigManager.isServerForcingSoloMode()) {
         return true;
      } else if (this.minecraft.player != null && ScoreboardLoadoutConfigManager.getEffectiveColumns(this.inGame()) == 1) {
         String teamBind = ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(0, this.inGame());
         if (!this.isTeamAll(teamBind)) {
            return false;
         } else {
            for (ScoreboardSyncPacket.Entry item : leaderboardData) {
               if (item.online) {
                  String normalized = item.teamName == null ? "" : item.teamName.toLowerCase(Locale.ROOT);
                  if (normalized.startsWith("room_") && normalized.contains("_camp_")) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isConquestRuntimeTeamName(String teamName) {
      String normalized = teamName == null ? "" : teamName.toLowerCase(Locale.ROOT);
      return normalized.startsWith("room_") && normalized.contains("_camp_");
   }

   private String getSelfTeamName() {
      if (this.minecraft.player == null) {
         return "";
      } else {
         UUID selfUUID = this.minecraft.player.getUUID();

         for (ScoreboardSyncPacket.Entry item : leaderboardData) {
            if (selfUUID.equals(item.uuid)) {
               return item.teamName == null ? "" : item.teamName;
            }
         }

         return "";
      }
   }

   private String getSelfSquadLabel() {
      if (this.minecraft.player == null) {
         return "";
      } else {
         UUID selfUUID = this.minecraft.player.getUUID();

         for (ScoreboardSyncPacket.Entry item : leaderboardData) {
            if (selfUUID.equals(item.uuid)) {
               return item.squadLabel == null ? "" : item.squadLabel;
            }
         }

         return "";
      }
   }

   private boolean shouldUseReviveColumn() {
      return this.isConquestMatchScoreboardJoined();
   }

   private boolean shouldShowDedicatedReviveColumn() {
      return ModList.get().isLoaded("gd656conquest") && !this.shouldUseReviveColumn();
   }

   private int findPanelAt(double mouseX, double mouseY) {
      for (int i = 0; i < 4; i++) {
         if (mouseX >= (double)this.panelX1[i] && mouseX <= (double)this.panelX2[i] && mouseY >= (double)this.panelY1[i] && mouseY <= (double)this.panelY2[i]) {
            return i;
         }
      }

      return -1;
   }

   private ScoreboardTab.ColumnLayout computeColumnLayout(int panelWidth, boolean showDedicatedReviveColumn) {
      int rankWidth = 17;
      int nameMin = 25;
      int scoreMin = 15;
      int otherMin = 10;
      int statColumnCount = showDedicatedReviveColumn ? 5 : 4;
      int minTotal = rankWidth + nameMin + scoreMin + otherMin * statColumnCount;
      int extra = Math.max(0, panelWidth - minTotal);
      int nameWidth = nameMin + extra / 2;
      int scoreWidth = scoreMin + extra / 4;
      int remaining = extra - (nameWidth - nameMin) - (scoreWidth - scoreMin);
      int statExtraBase = remaining / statColumnCount;
      int statRemainder = remaining - statExtraBase * statColumnCount;
      int killWidth = otherMin + statExtraBase;
      int deathWidth = otherMin + statExtraBase;
      int assistWidth = otherMin + statExtraBase;
      int reviveWidth = showDedicatedReviveColumn ? otherMin + statExtraBase : 0;
      int pingWidth = otherMin + statExtraBase + statRemainder;
      return new ScoreboardTab.ColumnLayout(rankWidth, nameWidth, scoreWidth, killWidth, deathWidth, assistWidth, reviveWidth, pingWidth);
   }

   private int tint(int argbColor, float delta) {
      int a = argbColor >>> 24 & 0xFF;
      int r = argbColor >>> 16 & 0xFF;
      int g = argbColor >>> 8 & 0xFF;
      int b = argbColor & 0xFF;
      r = this.clampColor((int)((float)r + 255.0F * delta));
      g = this.clampColor((int)((float)g + 255.0F * delta));
      b = this.clampColor((int)((float)b + 255.0F * delta));
      return a << 24 | r << 16 | g << 8 | b;
   }

   private int clampColor(int value) {
      return Math.max(0, Math.min(255, value));
   }

   private List<GDTextRenderer.ColoredText> formatPing(int ping) {
      List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
      if (ping < 0) {
         texts.add(new GDTextRenderer.ColoredText("--", -10066330));
         return texts;
      } else {
         float factor = Math.min(1.0F, (float)ping / 200.0F);
         int r;
         int g;
         int b;
         if (factor <= 0.5F) {
            r = (int)(factor * 2.0F * 255.0F);
            g = 200;
            b = 0;
         } else {
            r = 255;
            g = (int)((1.0F - factor) * 2.0F * 200.0F);
            b = 0;
         }

         int pingColor = 0xFF000000 | r << 16 | g << 8 | b;
         texts.add(new GDTextRenderer.ColoredText(ping + "ms", pingColor));
         return texts;
      }
   }

   private int resolvePing(ScoreboardSyncPacket.Entry entry) {
      if (this.minecraft.getConnection() != null) {
         PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(entry.uuid);
         if (info != null) {
            return info.getLatency();
         }
      }

      return entry.online ? entry.ping : -1;
   }

   private String toStyledText(String text, boolean italic) {
      return italic && text != null && !text.isEmpty() ? "§o" + text + "§r" : text;
   }

   /**
    * A panel caption derived from the rows themselves, for when there is no world to ask.
    *
    * <p>Counts distinct team names present. One team reads as that team's name (this server publishes
    * {@code room_*_camp_*} for its two sides, and {@link #resolveRuntimeTeamDisplayName} already maps
    * those to something legible); anything else reads as the number of players, which is true regardless
    * of how the server groups them.
    */
   private String labelFromRows() {
      if (leaderboardData.isEmpty()) return "";
      java.util.LinkedHashSet<String> teams = new java.util.LinkedHashSet<>();
      for (ScoreboardSyncPacket.Entry entry : leaderboardData) {
         if (entry.teamName != null && !entry.teamName.isBlank()) teams.add(entry.teamName);
      }
      if (teams.size() == 1) {
         String only = teams.iterator().next();
         return this.resolveRuntimeTeamDisplayName(only);
      }
      return leaderboardData.size() + " " + I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.players", new Object[0]);
   }

   /** Every row of the record, for the panels that no longer have a world to filter against. */
   private List<ScoreboardSyncPacket.Entry> getPanelEntries(int panelIndex) {
      List<ScoreboardSyncPacket.Entry> list = new ArrayList<>();
      String bind = ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(panelIndex, this.inGame());
      String boundTeamName = this.resolveBoundTeamName(bind);
      // Presence is a live property, so the offline filter only means something for a live table. Applied
      // to a restored record it hides every row — saved entries are offline by definition — and the table
      // comes back empty with no indication why. This is the exact "I turned that toggle on last week and
      // now the standings are blank" failure.
      boolean filterOffline = hideOffline && source == cn.bfops.LeaderboardScope.Source.LIVE_ROOM;

      for (ScoreboardSyncPacket.Entry entry : leaderboardData) {
         if ((!filterOffline || entry.online) && (this.isTeamAll(bind) || boundTeamName.equals(entry.teamName))) {
            list.add(entry);
         }
      }

      ScoreboardTab.SortType sortType = this.panelSortType[panelIndex];
      boolean ascending = this.panelSortAscending[panelIndex];
      list.sort((a, b) -> {
         int result = switch (sortType) {
            case NAME -> {
               String nameA = a.lastLoginName != null ? a.lastLoginName : a.name;
               String nameB = b.lastLoginName != null ? b.lastLoginName : b.name;
               yield nameA.compareToIgnoreCase(nameB);
            }
            case SCORE -> Integer.compare(a.score, b.score);
            case KILL -> Integer.compare(a.kill, b.kill);
            case DEATH -> Integer.compare(a.death, b.death);
            case ASSIST -> Integer.compare(a.assist, b.assist);
            case REVIVE -> Integer.compare(a.revive, b.revive);
            case PING -> Integer.compare(this.resolvePing(a), this.resolvePing(b));
         };
         if (result == 0) {
            result = a.uuid.compareTo(b.uuid);
         }

         return ascending ? result : -result;
      });
      return list;
   }

   private List<GDTextRenderer.ColoredText> getPanelTeamInfo(int panelIndex) {
      List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
      String bind = ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(panelIndex, this.inGame());
      String boundTeamName = this.resolveBoundTeamName(bind);
      if (this.minecraft.level == null) {
         // Outside a world there is no scoreboard to resolve a team against, but "not in any team yet"
         // is the wrong thing to print above a table full of people: it describes the player's standing
         // rather than the panel's source. The panel caption is derived from the rows instead.
         String label = this.isTeamAll(bind) ? this.labelFromRows() : boundTeamName;
         texts.add(new GDTextRenderer.ColoredText(
                 " " + (label == null || label.isBlank()
                         ? I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.source.empty", new Object[0])
                         : label) + " ",
                 -5592406));
         return texts;
      } else if (this.isConquestSoloMatchScoreboardJoined()) {
         String title = I18n.exists("gd656conquest.scoreboard.solo_all") ? I18n.get("gd656conquest.scoreboard.solo_all", new Object[0]) : "单人枪神竞技";
         texts.add(new GDTextRenderer.ColoredText(" " + title + " ", -3377289));
         int onlineMembers = 0;

         for (ScoreboardSyncPacket.Entry entry : leaderboardData) {
            if (entry.online && (!hideOffline || entry.online)) {
               onlineMembers++;
            }
         }

         texts.add(new GDTextRenderer.ColoredText(String.valueOf(onlineMembers), GuiConstants.COLOR_GOLD));
         return texts;
      } else {
         PlayerTeam team;
         if (this.isTeamAll(bind)) {
            team = this.minecraft.player == null ? null : this.minecraft.level.getScoreboard().getPlayersTeam(this.minecraft.player.getScoreboardName());
         } else {
            team = this.minecraft.level.getScoreboard().getPlayerTeam(boundTeamName);
         }

         if (team == null) {
            if (this.isConquestRuntimeTeamName(boundTeamName)) {
               int totalMembers = 0;
               int onlineMembers = 0;

               for (ScoreboardSyncPacket.Entry entryx : leaderboardData) {
                  if (boundTeamName.equals(entryx.teamName)) {
                     totalMembers++;
                     if (entryx.online) {
                        onlineMembers++;
                     }
                  }
               }

               if (totalMembers > 0) {
                  int configuredMaxPlayers = this.resolveBoundTeamConfiguredMax(bind);
                  int color = this.resolveRuntimeTeamFallbackColor(boundTeamName);
                  texts.add(new GDTextRenderer.ColoredText(" " + this.resolveRuntimeTeamDisplayName(boundTeamName) + " ", color));
                  texts.add(new GDTextRenderer.ColoredText(String.valueOf(onlineMembers), GuiConstants.COLOR_GOLD));
                  texts.add(new GDTextRenderer.ColoredText("/" + (configuredMaxPlayers > 0 ? configuredMaxPlayers : totalMembers), -5592406));
                  return texts;
               }
            }

            texts.add(new GDTextRenderer.ColoredText(" " + I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.no_team", new Object[0]), -5592406));
            return texts;
         } else {
            int color = this.convertChatFormattingToColor(team.getColor());
            texts.add(new GDTextRenderer.ColoredText(" " + team.getDisplayName().getString() + " ", color));
            int totalMembers = team.getPlayers().size();
            int onlineMembers = 0;
            if (this.minecraft.getConnection() != null) {
               for (String playerName : team.getPlayers()) {
                  if (this.minecraft.getConnection().getPlayerInfo(playerName) != null) {
                     onlineMembers++;
                  }
               }
            }

            int configuredMaxPlayers = this.resolveBoundTeamConfiguredMax(bind);
            texts.add(new GDTextRenderer.ColoredText(String.valueOf(onlineMembers), GuiConstants.COLOR_GOLD));
            texts.add(new GDTextRenderer.ColoredText("/" + (configuredMaxPlayers > 0 ? configuredMaxPlayers : totalMembers), -5592406));
            return texts;
         }
      }
   }

   private int getBoundTeamColor(String bind) {
      if (this.minecraft.level != null && bind != null && !this.isTeamAll(bind)) {
         String boundTeamName = this.resolveBoundTeamName(bind);
         PlayerTeam team = this.minecraft.level.getScoreboard().getPlayerTeam(boundTeamName);
         if (team != null) {
            return this.convertChatFormattingToColor(team.getColor());
         } else {
            return this.isConquestRuntimeTeamName(boundTeamName) ? this.resolveRuntimeTeamFallbackColor(boundTeamName) : GuiConstants.COLOR_GOLD;
         }
      } else {
         return this.isConquestSoloMatchScoreboardJoined() ? -3377289 : GuiConstants.COLOR_GOLD;
      }
   }

   private String resolveRuntimeTeamDisplayName(String teamName) {
      if (teamName != null && !teamName.isBlank()) {
         String normalized = teamName.toLowerCase(Locale.ROOT);
         if (normalized.endsWith("_camp_a")) {
            return "Camp A";
         } else {
            return normalized.endsWith("_camp_b") ? "Camp B" : teamName;
         }
      } else {
         return I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.no_team", new Object[0]);
      }
   }

   private int resolveRuntimeTeamFallbackColor(String teamName) {
      String normalized = teamName == null ? "" : teamName.toLowerCase(Locale.ROOT);
      if (normalized.endsWith("_camp_a")) {
         return -7876885;
      } else {
         return normalized.endsWith("_camp_b") ? -65536 : GuiConstants.COLOR_GOLD;
      }
   }

   private String resolveBoundTeamName(String bind) {
      if (bind != null && !bind.isBlank() && !this.isTeamAll(bind)) {
         int separator = bind.indexOf(124);
         return separator <= 0 ? bind : bind.substring(0, separator);
      } else {
         return bind;
      }
   }

   private int resolveBoundTeamConfiguredMax(String bind) {
      if (bind != null && !bind.isBlank()) {
         int separator = bind.indexOf("|cap=");
         if (separator >= 0 && separator + 5 < bind.length()) {
            try {
               return Integer.parseInt(bind.substring(separator + 5));
            } catch (NumberFormatException var4) {
               return 0;
            }
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private void openColumnModeSelector() {
      if (!ScoreboardLoadoutConfigManager.isDisplayModeLockedToAuto()) {
         List<FixedChoiceConfigEntry.Choice> options = new ArrayList<>();
         options.add(new FixedChoiceConfigEntry.Choice("auto", I18n.get("battlefield_ops.client.gui.scoreboard.column_mode.auto", new Object[0])));
         options.add(new FixedChoiceConfigEntry.Choice("single", I18n.get("battlefield_ops.client.gui.scoreboard.column_mode.single", new Object[0])));
         options.add(new FixedChoiceConfigEntry.Choice("double", I18n.get("battlefield_ops.client.gui.scoreboard.column_mode.double", new Object[0])));
         options.add(new FixedChoiceConfigEntry.Choice("quad", I18n.get("battlefield_ops.client.gui.scoreboard.column_mode.quad", new Object[0])));

         String selected = switch (ScoreboardLoadoutConfigManager.getDisplayMode()) {
            case AUTO -> "auto";
            case SINGLE -> "single";
            case DOUBLE -> "double";
            case QUAD -> "quad";
         };
         this.choiceListDialog.show(selected, I18n.get("battlefield_ops.client.gui.button.scoreboard_column_mode", new Object[0]), options, value -> {
            if ("double".equals(value)) {
               ScoreboardLoadoutConfigManager.setDisplayMode(ScoreboardLoadoutConfigManager.DisplayMode.DOUBLE);
            } else if ("quad".equals(value)) {
               ScoreboardLoadoutConfigManager.setDisplayMode(ScoreboardLoadoutConfigManager.DisplayMode.QUAD);
            } else if ("single".equals(value)) {
               ScoreboardLoadoutConfigManager.setDisplayMode(ScoreboardLoadoutConfigManager.DisplayMode.SINGLE);
            } else {
               ScoreboardLoadoutConfigManager.setDisplayMode(ScoreboardLoadoutConfigManager.DisplayMode.AUTO);
            }

            this.panelTargetScrollY[0] = 0.0;
            this.panelTargetScrollY[1] = 0.0;
            this.panelTargetScrollY[2] = 0.0;
            this.panelTargetScrollY[3] = 0.0;
         }, null);
      }
   }

   private void openPanelTeamSelector(int panelIndex) {
      if (this.minecraft.level != null && ScoreboardLoadoutConfigManager.getDisplayMode() != ScoreboardLoadoutConfigManager.DisplayMode.AUTO) {
         List<FixedChoiceConfigEntry.Choice> options = new ArrayList<>();
         options.add(new FixedChoiceConfigEntry.Choice("__all__", I18n.get("battlefield_ops.client.gui.scoreboard.team_filter.all", new Object[0])));

         for (PlayerTeam team : this.minecraft.level.getScoreboard().getPlayerTeams()) {
            String label = (team.getColor() != null ? team.getColor().toString() : "") + team.getDisplayName().getString() + "§r";
            options.add(new FixedChoiceConfigEntry.Choice(team.getName(), label));
         }

         this.choiceListDialog
            .show(
               ScoreboardLoadoutConfigManager.getEffectivePanelTeamBinding(panelIndex, this.inGame()),
               I18n.get("battlefield_ops.client.gui.scoreboard.team_filter.title", new Object[0]),
               options,
               value -> {
                  ScoreboardLoadoutConfigManager.setPanelTeamBinding(panelIndex, value);
                  this.panelTargetScrollY[panelIndex] = 0.0;
               },
               null
            );
      }
   }

   private ItemStack getPanelIconByTeam(String bind) {
      if (this.minecraft.level != null && bind != null && !this.isTeamAll(bind)) {
         PlayerTeam team = this.minecraft.level.getScoreboard().getPlayerTeam(bind);
         if (team == null) {
            return Items.GOLDEN_CARROT.getDefaultInstance();
         } else {
            return switch (team.getColor()) {
               case RED, DARK_RED -> Items.REDSTONE.getDefaultInstance();
               case BLUE, DARK_BLUE, AQUA, DARK_AQUA -> Items.LAPIS_LAZULI.getDefaultInstance();
               case GREEN, DARK_GREEN -> Items.EMERALD.getDefaultInstance();
               case YELLOW, GOLD -> Items.GOLD_INGOT.getDefaultInstance();
               case WHITE, GRAY -> Items.QUARTZ.getDefaultInstance();
               case BLACK -> Items.COAL.getDefaultInstance();
               case LIGHT_PURPLE, DARK_PURPLE -> Items.AMETHYST_SHARD.getDefaultInstance();
               default -> Items.GOLDEN_CARROT.getDefaultInstance();
            };
         }
      } else {
         return Items.GOLDEN_CARROT.getDefaultInstance();
      }
   }

   private int convertChatFormattingToColor(ChatFormatting color) {
      if (color == null) {
         return GuiConstants.COLOR_GOLD;
      } else {
         return switch (color) {
            case RED -> -43691;
            case DARK_RED -> -5636096;
            case BLUE -> -11184641;
            case DARK_BLUE -> -16777046;
            case AQUA -> -11141121;
            case DARK_AQUA -> -16733526;
            case GREEN -> -11141291;
            case DARK_GREEN -> -16733696;
            case YELLOW -> -171;
            case GOLD -> -22016;
            case WHITE -> -1;
            case GRAY -> -5592406;
            case BLACK -> -15658735;
            case LIGHT_PURPLE -> -43521;
            case DARK_PURPLE -> -5635926;
            case DARK_GRAY -> -11184811;
            default -> GuiConstants.COLOR_GOLD;
         };
      }
   }

   private List<GDTextRenderer.ColoredText> getPingInfo() {
      List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
      if (this.minecraft.player != null && this.minecraft.getConnection() != null) {
         int ping = 0;
         PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(this.minecraft.player.getUUID());
         if (info != null) {
            ping = info.getLatency();
            float factor = Math.min(1.0F, (float)ping / 200.0F);
            int r;
            int g;
            int b;
            if (factor <= 0.5F) {
               r = (int)(factor * 2.0F * 255.0F);
               g = 200;
               b = 0;
            } else {
               r = 255;
               g = (int)((1.0F - factor) * 2.0F * 200.0F);
               b = 0;
            }

            int pingColor = 0xFF000000 | r << 16 | g << 8 | b;
            texts.add(new GDTextRenderer.ColoredText(ping + "ms", pingColor));
            return texts;
         } else {
            texts.add(new GDTextRenderer.ColoredText("--", -10066330));
            return texts;
         }
      } else {
         texts.add(new GDTextRenderer.ColoredText("--", -10066330));
         return texts;
      }
   }

   @Override
   protected void updateSubtitle(int x1, int y1, int x2) {
      List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
      texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.config.tab.scoreboard.your_score", new Object[0]), -5592406));
      Integer scoreObj = this.getSelfGlobalScore();
      if (scoreObj == null) {
         texts.add(new GDTextRenderer.ColoredText("--", -10066330));
      } else {
         texts.add(new GDTextRenderer.ColoredText(String.valueOf(scoreObj), GuiConstants.COLOR_GOLD));
      }

      if (this.subtitleRenderer == null) {
         this.subtitleRenderer = new GDTextRenderer(texts, x1, y1, x2, y1 + 10, 1.0F, false);
      } else {
         this.subtitleRenderer.setX1(x1);
         this.subtitleRenderer.setY1(y1);
         this.subtitleRenderer.setX2(x2);
         this.subtitleRenderer.setColoredTexts(texts);
      }
   }

   private Integer getSelfGlobalScore() {
      if (this.minecraft.player == null) {
         return null;
      } else {
         Scoreboard scoreboard = this.minecraft.player.getScoreboard();
         if (scoreboard != null) {
            Objective objective = scoreboard.getObjective("battlefield_ops.score");
            if (objective != null) {
               try {
                  Object scoreAccess = scoreboard.getOrCreatePlayerScore(this.minecraft.player.getScoreboardName(), objective);

                  try {
                     int score = ((Number)scoreAccess.getClass().getMethod("get").invoke(scoreAccess)).intValue();
                     lastKnownGlobalScore = score;
                     return score;
                  } catch (Exception var6) {
                     try {
                        int scorex = ((Number)scoreAccess.getClass().getMethod("getScore").invoke(scoreAccess)).intValue();
                        lastKnownGlobalScore = scorex;
                        return scorex;
                     } catch (Exception var5) {
                     }
                  }
               } catch (Exception var7) {
               }
            }
         }

         if (this.isConquestMatchScoreboardJoined()) {
            return lastKnownGlobalScore;
         } else {
            UUID selfUUID = this.minecraft.player.getUUID();

            for (ScoreboardSyncPacket.Entry entry : leaderboardData) {
               if (entry.uuid.equals(selfUUID)) {
                  lastKnownGlobalScore = entry.score;
                  return entry.score;
               }
            }

            return lastKnownGlobalScore;
         }
      }
   }

   @Override
   protected void renderSideButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
   }

   private static record ColumnLayout(
      int rankWidth, int nameWidth, int scoreWidth, int killWidth, int deathWidth, int assistWidth, int reviveWidth, int pingWidth
   ) {
   }

   private static enum SortType {
      NAME,
      SCORE,
      KILL,
      DEATH,
      ASSIST,
      REVIVE,
      PING;
   }
}
