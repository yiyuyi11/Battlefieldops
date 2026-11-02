package cn.bfops.killicon.client.util;

import net.minecraft.client.resources.language.I18n;

public final class I18nCompat {
   private I18nCompat() {
   }

   public static boolean exists(String key) {
      if (key != null && !key.isBlank()) {
         try {
            return !I18n.get(key, new Object[0]).equals(key);
         } catch (Exception var2) {
            return false;
         }
      } else {
         return false;
      }
   }
}
