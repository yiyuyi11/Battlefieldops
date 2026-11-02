package cn.bfops.killicon.client;

import cn.bfops.killicon.client.bridge.ClientBridge;

import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.impl.AceLogoRenderer;
import cn.bfops.killicon.client.render.impl.Battlefield1Renderer;
import cn.bfops.killicon.client.render.impl.BonusListRenderer;
import cn.bfops.killicon.client.render.impl.CardBarRenderer;
import cn.bfops.killicon.client.render.impl.CardRenderer;
import cn.bfops.killicon.client.render.impl.ComboIconRenderer;
import cn.bfops.killicon.client.render.impl.ComboSubtitleRenderer;
import cn.bfops.killicon.client.render.impl.HitInfoRenderer;
import cn.bfops.killicon.client.render.impl.HonorRenderer;
import cn.bfops.killicon.client.render.impl.ScoreSubtitleRenderer;
import cn.bfops.killicon.client.render.impl.ScrollingIconRenderer;
import cn.bfops.killicon.client.render.impl.SubtitleRenderer;
import cn.bfops.killicon.client.render.impl.ValorantIconRenderer;
import cn.bfops.killicon.client.sounds.ExternalSoundManager;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.client.textures.ExternalTextureManager;

public class ClientSetup {
   public static void initializeClient() {
      ClientStatsManager.init();
      ConfigManager.init();
      ExternalTextureManager.init();
      ExternalSoundManager.init();
      HudElementManager.init();
      HudElementManager.register("kill_icon", "scrolling", ScrollingIconRenderer.getInstance());
      HudElementManager.register("kill_icon", "combo", new ComboIconRenderer());
      HudElementManager.register("kill_icon", "valorant", new ValorantIconRenderer());
      HudElementManager.register("kill_icon", "card_bar", new CardBarRenderer());
      HudElementManager.register("kill_icon", "card", new CardRenderer());
      HudElementManager.register("kill_icon", "battlefield1", new Battlefield1Renderer());
      HudElementManager.register("kill_icon", "honor", HonorRenderer.getInstance());
      HudElementManager.register("subtitle", "kill_feed", SubtitleRenderer.getInstance());
      HudElementManager.register("subtitle", "score", ScoreSubtitleRenderer.getInstance());
      HudElementManager.register("subtitle", "combo", ComboSubtitleRenderer.getInstance());
      HudElementManager.register("subtitle", "bonus_list", BonusListRenderer.getInstance());
      HudElementManager.register("subtitle", "hit_info", HitInfoRenderer.getInstance());
      HudElementManager.register("global", "ace_logo", new AceLogoRenderer());
      registerConfigScreen();

   }

   public static void registerConfigScreen() {
      ClientBridge.loader().registerConfigScreen();
   }
}
