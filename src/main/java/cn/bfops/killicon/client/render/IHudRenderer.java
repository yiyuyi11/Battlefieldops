package cn.bfops.killicon.client.render;

import net.minecraft.client.gui.GuiGraphics;

public interface IHudRenderer {
   void render(GuiGraphics var1, float var2);

   void trigger(IHudRenderer.TriggerContext var1);

   default void resetPreview() {
   }

   public static record TriggerContext(int type, int entityId, int comboCount, String extraData, float distance, float bonusMultiplier, float bonusScale) {
      public static IHudRenderer.TriggerContext of(int type, int entityId) {
         return new IHudRenderer.TriggerContext(type, entityId, 0, "", 0.0F, 0.0F, 0.0F);
      }

      public static IHudRenderer.TriggerContext of(int type, int entityId, int comboCount) {
         return new IHudRenderer.TriggerContext(type, entityId, comboCount, "", 0.0F, 0.0F, 0.0F);
      }

      public static IHudRenderer.TriggerContext of(int type, int entityId, int comboCount, String extraData) {
         return new IHudRenderer.TriggerContext(type, entityId, comboCount, extraData, 0.0F, 0.0F, 0.0F);
      }

      public static IHudRenderer.TriggerContext of(int type, int entityId, int comboCount, String extraData, float distance) {
         return new IHudRenderer.TriggerContext(type, entityId, comboCount, extraData, distance, 0.0F, 0.0F);
      }

      public static IHudRenderer.TriggerContext of(int type, String extraData) {
         return new IHudRenderer.TriggerContext(type, -1, 0, extraData, 0.0F, 0.0F, 0.0F);
      }
   }
}
