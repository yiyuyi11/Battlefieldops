package cn.bfops.killicon.client.render;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ScreenAnchor {
   public static final String DEFAULT = "bottom_center";
   private static final Map<String, float[]> ANCHORS = Map.of(
      "top_left",
      new float[]{0.0F, 0.0F},
      "middle_left",
      new float[]{0.0F, 0.5F},
      "bottom_left",
      new float[]{0.0F, 1.0F},
      "top_center",
      new float[]{0.5F, 0.0F},
      "center",
      new float[]{0.5F, 0.5F},
      "bottom_center",
      new float[]{0.5F, 1.0F},
      "top_right",
      new float[]{1.0F, 0.0F},
      "middle_right",
      new float[]{1.0F, 0.5F},
      "bottom_right",
      new float[]{1.0F, 1.0F}
   );
   public static final List<String> ANCHOR_IDS = List.of(
      "top_left", "middle_left", "bottom_left", "top_center", "center", "bottom_center", "top_right", "middle_right", "bottom_right"
   );

   private ScreenAnchor() {
   }

   public static boolean isValid(String anchor) {
      return anchor != null && ANCHORS.containsKey(anchor);
   }

   public static float resolveAnchorX(String anchor, int screenWidth) {
      float[] a = ANCHORS.get(isValid(anchor) ? anchor : "bottom_center");
      return a[0] * (float)screenWidth;
   }

   public static float resolveAnchorY(String anchor, int screenHeight) {
      float[] a = ANCHORS.get(isValid(anchor) ? anchor : "bottom_center");
      return a[1] * (float)screenHeight;
   }

   public static int resolveCenterX(String anchor, int xOffset, int screenWidth) {
      return Math.round(resolveAnchorX(anchor, screenWidth) + (float)xOffset);
   }

   public static int resolveCenterY(String anchor, int yOffset, int screenHeight) {
      return Math.round(resolveAnchorY(anchor, screenHeight) - (float)yOffset);
   }

   public static int translateXOffset(String fromAnchor, String toAnchor, int xOffset, int screenWidth) {
      float refXFrom = resolveAnchorX(fromAnchor, screenWidth);
      float refXTo = resolveAnchorX(toAnchor, screenWidth);
      return Math.round((float)xOffset + (refXFrom - refXTo));
   }

   public static int translateYOffset(String fromAnchor, String toAnchor, int yOffset, int screenHeight) {
      float refYFrom = resolveAnchorY(fromAnchor, screenHeight);
      float refYTo = resolveAnchorY(toAnchor, screenHeight);
      return Math.round((float)yOffset + (refYTo - refYFrom));
   }

   public static String resolveAnchorOrDefault(String anchor) {
      return isValid(anchor) ? anchor : "bottom_center";
   }

   public static List<String> getAnchorIds() {
      return ANCHOR_IDS;
   }

   public static String[] anchorIdsArray() {
      return Arrays.copyOf(ANCHOR_IDS.toArray(new String[0]), ANCHOR_IDS.size());
   }
}
