package cn.bfops.killicon.client.render;

public final class PreviewRenderTimeContext {
   private static final ThreadLocal<Boolean> IN_PREVIEW = ThreadLocal.withInitial(() -> false);
   private static volatile boolean paused = false;
   private static volatile long frozenTime = -1L;
   private static volatile long lastPreviewTime = -1L;

   private PreviewRenderTimeContext() {
   }

   public static void beginPreviewFrame() {
      IN_PREVIEW.set(true);
   }

   public static void endPreviewFrame() {
      IN_PREVIEW.set(false);
   }

   public static long currentTimeMillis() {
      if (!IN_PREVIEW.get()) {
         return System.currentTimeMillis();
      } else if (paused) {
         if (frozenTime < 0L) {
            frozenTime = lastPreviewTime > 0L ? lastPreviewTime : System.currentTimeMillis();
         }

         return frozenTime;
      } else {
         long now = System.currentTimeMillis();
         lastPreviewTime = now;
         return now;
      }
   }

   public static void setPaused(boolean value) {
      if (value) {
         if (!paused) {
            frozenTime = lastPreviewTime > 0L ? lastPreviewTime : System.currentTimeMillis();
         }

         paused = true;
      } else {
         paused = false;
         frozenTime = -1L;
      }
   }
}
