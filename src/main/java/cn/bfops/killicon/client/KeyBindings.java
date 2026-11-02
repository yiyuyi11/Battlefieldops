package cn.bfops.killicon.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import cn.bfops.killicon.client.gui.MainConfigScreen;

public class KeyBindings {
   public static final String CATEGORY = "key.categories.battlefield_ops";
   public static final String OPEN_CONFIG_KEY = "key.battlefield_ops.open_config";
   public static final String OPEN_SCOREBOARD_KEY = "key.battlefield_ops.open_scoreboard";
   /** Full configuration window. Unbound by default; reached from the leaderboard's Home tab. */
   public static final KeyMapping OPEN_CONFIG = new KeyMapping("key.battlefield_ops.open_config", Type.KEYSYM, -1, "key.categories.battlefield_ops");
   /**
    * Opens the 656 leaderboard directly. Defaults to {@code GLFW_KEY_HOME}.
    *
    * <p>Two separate defects are fixed by giving this a default. It was registered with {@code -1}
    * ("no binding"), so the feature was documented and unreachable without digging through the controls
    * menu; and it was not registered as a {@link KeyMapping} at all, so even a player who bound it by
    * hand got nothing. Both are why "the leaderboard cannot be opened" — the page itself was merely
    * match-gated on top of that.
    *
    * <p>{@code Home} specifically: every letter and digit in this pack's {@code options.txt} is already
    * taken, the F-row here is F4/F6/F7/F8/F9/F10/F12, and F3 belongs to vanilla's hardcoded debug
    * overlay (F1 likewise toggles the HUD), so neither can be claimed. Of the keys left free —
    * insert, home, delete, minus, semicolon, caps.lock, right control/alt, menu — {@code Home} is the
    * only one that neither a modifier nor a text entry can swallow, and reading standings is a "step
    * back and look" action rather than something needed under fire, so a key away from WASD is right.
    */
   public static final KeyMapping OPEN_SCOREBOARD = new KeyMapping("key.battlefield_ops.open_scoreboard", Type.KEYSYM,
           com.mojang.blaze3d.platform.InputConstants.KEY_HOME, "key.categories.battlefield_ops");

   public static boolean matches(KeyMapping mapping, int keyCode) {
      return mapping.matches(keyCode, 0);
   }

   /** Both mappings, so the mod's own registration hook has one list to register. */
   public static KeyMapping[] all() {
      return new KeyMapping[]{OPEN_SCOREBOARD, OPEN_CONFIG};
   }

   /**
    * Held-key handling for the quick standings overlay.
    *
    * <p>Kept for the press/release form of the binding. Wiring goes through {@link #poll(Minecraft)},
    * because {@code consumeClick()} is what a real {@code Screen}-opening key needs: the window it opens
    * takes the keyboard, so a held-state implementation would just reopen it on release.
    */
   public static void onKeyInput(int key, int action) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         handleScoreboardKey(mc, key, action);
         if (mc.screen == null) {
            handleConfigKey(mc);
         }
      }
   }

   /**
    * Polls the mappings once per client tick.
    *
    * <p>Called from {@code Client.tick}. No match-state guard on purpose: the leaderboard is available
    * in the lobby, between rounds and after the final summary, which is the whole point of the feature.
    */
   public static void poll(Minecraft mc) {
      if (mc == null) return;
      while (OPEN_SCOREBOARD.consumeClick()) {
         if (mc.screen == null && mc.player != null) {
            mc.setScreen(new MainConfigScreen(null, 3, false));
         }
      }
   }

   private static void handleScoreboardKey(Minecraft mc, int key, int action) {
      if (matches(OPEN_SCOREBOARD, key)) {
         if (action == 1) {
            if (mc.screen == null) {
               mc.setScreen(new MainConfigScreen(null, 3, true));
            }
         } else if (action == 0
            && mc.screen instanceof MainConfigScreen screen
            && screen.isQuickScoreboardMode()
            && screen.shouldCloseQuickScoreboardOnRelease()) {
            mc.setScreen(null);
         }
      }
   }

   private static void handleConfigKey(Minecraft mc) {
      if (OPEN_CONFIG.consumeClick()) {
         mc.setScreen(new MainConfigScreen(null));
      }
   }
}
