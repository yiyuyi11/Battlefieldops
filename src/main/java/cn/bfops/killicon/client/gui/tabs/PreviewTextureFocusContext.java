package cn.bfops.killicon.client.gui.tabs;

public final class PreviewTextureFocusContext {
   private static String focusedElementId;
   private static String focusedTextureKey;

   private PreviewTextureFocusContext() {
   }

   public static void activate(String elementId, String textureKey) {
      focusedElementId = elementId;
      focusedTextureKey = textureKey;
   }

   public static void clear() {
      focusedElementId = null;
      focusedTextureKey = null;
   }

   public static float alphaMultiplier(String elementId, String textureKey) {
      if (focusedElementId == null || focusedTextureKey == null) {
         return 1.0F;
      } else if (!focusedElementId.equals(elementId)) {
         return 1.0F;
      } else {
         return textureKey != null && !focusedTextureKey.equals(textureKey) ? 0.3F : 1.0F;
      }
   }
}
