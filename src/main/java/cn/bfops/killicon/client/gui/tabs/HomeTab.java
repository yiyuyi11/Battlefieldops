package cn.bfops.killicon.client.gui.tabs;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;

public class HomeTab extends ConfigTabContent {
   private final List<HomeTab.ModStatus> modStatuses = new ArrayList<>();
   private final List<GDRowRenderer> area3Renderers = new ArrayList<>();
   private int area3X1;
   private int area3Y1;
   private int area3X2;
   private int area3Y2;
   private double scrollY3 = 0.0;
   private double targetScrollY3 = 0.0;
   private boolean isDragging3 = false;
   private double lastMouseY3 = 0.0;
   private long lastFrameTime = 0L;
   private static final ResourceLocation ICON_NORMAL = new ResourceLocation("battlefield_ops", "icon/gd656killicon_icon.png");
   private static final ResourceLocation ICON_RARE = new ResourceLocation("battlefield_ops", "icon/gd656killicon_656de_shuai_zhao.png");
   private ResourceLocation currentIcon = ICON_NORMAL;
   private int versionColor = -1;

   public HomeTab(Minecraft minecraft) {
      super(minecraft, "battlefield_ops.client.gui.config.tab.home");
      this.initModStatuses();
   }

   private void initModStatuses() {
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.tacz.name",
               "tacz",
               "https://www.mcmod.cn/class/14980.html",
               "battlefield_ops.client.gui.hometab.mod.tacz.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.sbw.name",
               "superbwarfare",
               "https://www.mcmod.cn/class/18845.html",
               "battlefield_ops.client.gui.hometab.mod.sbw.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.ywzj.name",
               "ywzj_vehicle",
               "https://www.mcmod.cn/class/24495.html",
               "battlefield_ops.client.gui.hometab.mod.ywzj.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.ia.name",
               "immersive_aircraft",
               "https://www.mcmod.cn/class/8527.html",
               "battlefield_ops.client.gui.hometab.mod.ia.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.spotting.name",
               "spotting",
               "https://www.curseforge.com/minecraft/mc-mods/spotting",
               "battlefield_ops.client.gui.hometab.mod.spotting.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.pingwheel.name",
               "pingwheel",
               "https://www.mcmod.cn/class/9032.html",
               "battlefield_ops.client.gui.hometab.mod.pingwheel.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.lr.name",
               "lrtactical",
               "https://curseforge.com/minecraft/mc-mods/tacz-lesraisins-tactical-equipements",
               "battlefield_ops.client.gui.hometab.mod.lr.desc"
            )
         );
      this.modStatuses
         .add(
            new HomeTab.ModStatus(
               "battlefield_ops.client.gui.hometab.mod.cnpc.name",
               "customnpcs",
               "https://www.mcmod.cn/class/15373.html",
               "battlefield_ops.client.gui.hometab.mod.cnpc.desc"
            )
         );
   }

   @Override
   public void onTabOpen() {
      super.onTabOpen();
      this.area3Renderers.clear();
      this.scrollY3 = 0.0;
      this.targetScrollY3 = 0.0;
      if (Math.random() < 0.01) {
         this.currentIcon = ICON_RARE;
      } else {
         this.currentIcon = ICON_NORMAL;
      }

      String version = GuiConstants.MOD_VERSION;
      if (version.endsWith("Alpha")) {
         this.versionColor = -65536;
      } else if (version.endsWith("Beta")) {
         this.versionColor = GuiConstants.COLOR_GOLD_ORANGE;
      } else if (version.endsWith("Release")) {
         this.versionColor = -16711936;
      } else {
         this.versionColor = -1;
      }
   }

   @Override
   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      this.updateAreaCoordinates(screenWidth, screenHeight);
      long now = System.nanoTime();
      if (this.lastFrameTime == 0L) {
         this.lastFrameTime = now;
      }

      float dt = (float)(now - this.lastFrameTime) / 1.0E9F;
      this.lastFrameTime = now;
      if (dt > 0.1F) {
         dt = 0.1F;
      }

      if (this.isDragging3) {
         double diff = (double)mouseY - this.lastMouseY3;
         this.targetScrollY3 -= diff;
         this.lastMouseY3 = (double)mouseY;
      }

      if (this.isDragging) {
         double diff = (double)mouseY - this.lastMouseY;
         this.targetScrollY -= diff;
         this.lastMouseY = (double)mouseY;
      }

      this.updateScroll(dt, screenHeight);
      this.renderArea3(guiGraphics, mouseX, mouseY, partialTick);
      if (this.configRows.isEmpty()) {
         int area1Right = (screenWidth - 16) / 3 + 8;
         int contentX = area1Right + 8;
         int contentY = 34;
         int contentWidth = screenWidth - contentX - 8;
         int contentHeight = screenHeight - contentY - 8;
         int centerX = contentX + contentWidth / 2;
         int centerY = contentY + contentHeight / 2;
         guiGraphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
         String titleText = Component.translatable("battlefield_ops.client.gui.hometab.title").getString();
         float titleScale = 2.0F;
         int titleWidth = (int)((float)this.minecraft.font.width(titleText) * titleScale);
         int titleHeight = (int)(9.0F * titleScale);
         String versionText = GuiConstants.MOD_VERSION;
         float versionScale = 1.0F;
         int versionWidth = this.minecraft.font.width(versionText);
         int versionHeight = 9;
         int textGap = 2;
         int totalTextHeight = titleHeight + textGap + versionHeight;
         int maxTextWidth = Math.max(titleWidth, versionWidth);
         int totalGroupWidth = totalTextHeight + maxTextWidth;
         int groupX = centerX - totalGroupWidth / 2;
         int groupY = centerY - totalTextHeight / 2;
         if (this.currentIcon == null) {
            this.currentIcon = ICON_NORMAL;
         }

         guiGraphics.blit(this.currentIcon, groupX, groupY, 0.0F, 0.0F, totalTextHeight, totalTextHeight, totalTextHeight, totalTextHeight);
         int textStartX = groupX + totalTextHeight;
         int textVisibleRight = contentX + contentWidth;
         GDTextRenderer titleRenderer = new GDTextRenderer(titleText, textStartX, groupY, textVisibleRight, groupY + titleHeight, titleScale, -1, false);
         titleRenderer.render(guiGraphics, partialTick, false);
         int versionY = groupY + titleHeight + textGap;
         GDTextRenderer versionRenderer = new GDTextRenderer(
            versionText, textStartX, versionY, textVisibleRight, versionY + versionHeight, versionScale, this.versionColor, false
         );
         versionRenderer.render(guiGraphics, partialTick, false);
         guiGraphics.disableScissor();
      } else {
         int area1Rightx = (screenWidth - 16) / 3 + 8;
         int contentXx = area1Rightx + 8;
         int contentYx = 34;
         int contentWidthx = screenWidth - contentXx - 8;
         int contentHeightx = screenHeight - contentYx - 8;
         guiGraphics.enableScissor(contentXx, contentYx, contentXx + contentWidthx, contentYx + contentHeightx);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0, -this.scrollY, 0.0);

         for (GDRowRenderer row : this.configRows) {
            row.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY), partialTick);
         }

         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
      }
   }

   @Override
   protected void updateScroll(float dt, int screenHeight) {
      if (!this.configRows.isEmpty()) {
         int contentY = 34;
         int viewHeight = screenHeight - contentY - 8;
         double maxScroll = (double)Math.max(0, this.totalContentHeight - viewHeight);
         this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
         double diff = this.targetScrollY - this.scrollY;
         if (Math.abs(diff) < 0.1) {
            this.scrollY = this.targetScrollY;
         } else {
            this.scrollY += diff * 15.0 * (double)dt;
         }
      }

      int rowHeight = 17;
      int gap = 1;
      int totalContentHeight3 = 0;

      for (HomeTab.ModStatus status : this.modStatuses) {
         totalContentHeight3 += rowHeight + gap;
         if (status.expanded) {
            totalContentHeight3 += rowHeight + gap;
         }
      }

      int viewHeight3 = this.area3Y2 - this.area3Y1;
      double maxScroll3 = (double)Math.max(0, totalContentHeight3 - viewHeight3);
      this.targetScrollY3 = Math.max(0.0, Math.min(maxScroll3, this.targetScrollY3));
      double diff3 = this.targetScrollY3 - this.scrollY3;
      if (Math.abs(diff3) < 0.1) {
         this.scrollY3 = this.targetScrollY3;
      } else {
         this.scrollY3 += diff3 * 15.0 * (double)dt;
      }
   }

   private void updateAreaCoordinates(int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      this.area3X1 = 8;
      this.area3Y1 = this.area1Bottom + 8;
      this.area3X2 = area1Right;
      this.area3Y2 = screenHeight - 35 - 16;
   }

   private void renderArea3(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      int x1 = this.area3X1;
      int yStart = this.area3Y1;
      int x2 = this.area3X2;
      int rowHeight = 17;
      int gap = 1;
      int currentY = yStart;
      if (this.area3Renderers.isEmpty()) {
         for (HomeTab.ModStatus status : this.modStatuses) {
            GDRowRenderer renderer = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.25F, false);
            this.area3Renderers.add(renderer);
         }
      }

      guiGraphics.enableScissor(this.area3X1, this.area3Y1, this.area3X2, this.area3Y2);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0, -this.scrollY3, 0.0);

      for (int i = 0; i < this.modStatuses.size(); i++) {
         HomeTab.ModStatus status = this.modStatuses.get(i);
         GDRowRenderer renderer = this.area3Renderers.get(i);
         renderer.setBounds(x1, currentY, x2, currentY + rowHeight);
         renderer.resetColumnConfig();
         boolean isInstalled = ClientBridge.loader().isModLoaded(status.modId);
         int nameColor = isInstalled ? GuiConstants.COLOR_GOLD : -5592406;
         renderer.addColumn(Component.translatable(status.nameKey).getString(), -1, nameColor, false, false, btn -> status.expanded = !status.expanded);
         String statusText = isInstalled
            ? Component.translatable("battlefield_ops.client.gui.hometab.status.installed").getString()
            : Component.translatable("battlefield_ops.client.gui.hometab.status.not_installed").getString();
         int statusColor = isInstalled ? -1 : -10066330;
         renderer.addColumn(statusText, 40, statusColor, true, true);
         int actionColor = status.clickable ? -1 : -10066330;
         renderer.addColumn(
            Component.translatable("battlefield_ops.client.gui.hometab.action.get").getString(),
            30,
            actionColor,
            true,
            true,
            status.clickable ? btn -> Util.getPlatform().openUri(URI.create(status.url)) : null
         );
         float actualTop = (float)currentY - (float)this.scrollY3;
         float actualBottom = (float)(currentY + rowHeight) - (float)this.scrollY3;
         if (actualBottom > (float)this.area3Y1 && actualTop < (float)this.area3Y2) {
            renderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
         }

         currentY += rowHeight + gap;
         if (status.expanded) {
            if (status.descriptionRenderer == null) {
               status.descriptionRenderer = new GDRowRenderer(x1 + rowHeight, 0, x2, 0, GuiConstants.COLOR_BLACK, 0.22F, false);
            }

            GDRowRenderer descRenderer = status.descriptionRenderer;
            descRenderer.setBounds(x1 + rowHeight, currentY, x2, currentY + rowHeight);
            descRenderer.resetColumnConfig();
            descRenderer.addColumn(Component.translatable(status.descriptionKey).getString(), -1, -1, false, false);
            float descTop = (float)currentY - (float)this.scrollY3;
            float descBottom = (float)(currentY + rowHeight) - (float)this.scrollY3;
            if (descBottom > (float)this.area3Y1 && descTop < (float)this.area3Y2) {
               descRenderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY3), partialTick);
            }

            currentY += rowHeight + gap;
         }
      }

      guiGraphics.pose().popPose();
      guiGraphics.disableScissor();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
         double adjustedMouseY = mouseY + this.scrollY3;
         int rowHeight = 17;
         int gap = 1;
         int currentY = this.area3Y1;

         for (int i = 0; i < this.modStatuses.size(); i++) {
            HomeTab.ModStatus status = this.modStatuses.get(i);
            GDRowRenderer renderer = this.area3Renderers.get(i);
            if (renderer.mouseClicked(mouseX, adjustedMouseY, button)) {
               return true;
            }

            currentY += rowHeight + gap;
            if (status.expanded && status.descriptionRenderer != null) {
               if (status.descriptionRenderer.mouseClicked(mouseX, adjustedMouseY, button)) {
                  return true;
               }

               currentY += rowHeight + gap;
            }
         }

         this.isDragging3 = true;
         this.lastMouseY3 = mouseY;
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDragging3 = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
         this.targetScrollY3 -= delta * 40.0;
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, delta);
      }
   }

   private static class ModStatus {
      final String nameKey;
      final String modId;
      final String url;
      final String descriptionKey;
      final boolean clickable;
      boolean expanded = false;
      GDRowRenderer descriptionRenderer;

      ModStatus(String nameKey, String modId, String url, String descriptionKey) {
         this(nameKey, modId, url, descriptionKey, true);
      }

      ModStatus(String nameKey, String modId, String url, String descriptionKey, boolean clickable) {
         this.nameKey = nameKey;
         this.modId = modId;
         this.url = url;
         this.descriptionKey = descriptionKey;
         this.clickable = clickable;
      }
   }
}